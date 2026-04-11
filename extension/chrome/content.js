// Content script - injected at document_idle on all pages
// Detects credential fields, updates badge, shows inline fill dropdown

(function () {
  'use strict';

  const ITEM_TYPE_LOGIN = 1;

  let cachedItems = null;  // items list, cached until dataVersion changes
  let cachedDataVersion = -1;
  let vaultLocked = false;
  let activeDropdown = null;
  let activeDropdownFields = null; // { pwField, userField } associated with current dropdown
  let activeSavePanel = null;
  let pendingUsername = null; // for multi-page login: cache username from page 1

  // ---- Injected stylesheet (for :hover etc. that can't be expressed inline) ----

  function ensureStyles() {
    if (document.getElementById('mypassword-injected-styles')) return;
    const style = document.createElement('style');
    style.id = 'mypassword-injected-styles';
    style.textContent = `
      .mypassword-login-row { transition: background 0.12s; }
      .mypassword-login-row:hover { background: #e3ecff; }

      .mypassword-passkey-row { transition: background 0.12s, border-color 0.12s; }
      .mypassword-passkey-row:hover {
        background: #e3ecff;
        border-color: #b7cbff;
      }
    `;
    (document.head || document.documentElement).appendChild(style);
  }

  // ---- Credential field detection ----

  function findCredentialFields() {
    const passwordFields = Array.from(document.querySelectorAll('input[type="password"]'));
    const result = [];
    const usedUserFields = new Set();
    for (const pwField of passwordFields) {
      if (!isVisible(pwField)) continue;
      const userField = findAdjacentUsernameField(pwField);
      if (userField) usedUserFields.add(userField);
      result.push({ userField, pwField });
    }
    // Standalone username/email fields (no visible password field on page, e.g. multi-step login)
    if (result.length === 0) {
      const userInputs = document.querySelectorAll('input[type="text"], input[type="email"], input:not([type])');
      for (const inp of userInputs) {
        if (!isVisible(inp) || usedUserFields.has(inp)) continue;
        if (isLikelyUsernameField(inp)) {
          result.push({ userField: inp, pwField: null });
        }
      }
    }
    return result;
  }

  function isLikelyUsernameField(inp) {
    const hints = ['user', 'email', 'login', 'account', 'identifier', 'webauthn'];
    const text = (inp.name + ' ' + inp.id + ' ' + inp.placeholder + ' ' + (inp.getAttribute('aria-label') || '')
        + ' ' + (inp.getAttribute('autocomplete') || '')).toLowerCase();
    return hints.some((h) => text.includes(h));
  }

  function isVisible(el) {
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0 && el.offsetParent !== null;
  }

  function findAdjacentUsernameField(pwField) {
    // Walk backwards in DOM order to find the nearest email/text input
    const inputs = Array.from(document.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"], input:not([type])'));
    const pwRect = pwField.getBoundingClientRect();
    let best = null;
    let bestDist = Infinity;
    for (const inp of inputs) {
      if (!isVisible(inp)) continue;
      const rect = inp.getBoundingClientRect();
      // must be above or at roughly the same vertical position
      if (rect.top > pwRect.top + 10) continue;
      const dist = pwRect.top - rect.top;
      if (dist >= 0 && dist < bestDist) {
        bestDist = dist;
        best = inp;
      }
    }
    return best;
  }

  // ---- Hostname matching ----

  function matchesHostname(item, hostname) {
    if (item.item_type !== ITEM_TYPE_LOGIN) return false;
    const websites = item.data?.websites;
    if (!websites || websites.length === 0) return false;
    return websites.some((url) => {
      try {
        const h = new URL(url.startsWith('http') ? url : 'https://' + url).hostname;
        return h === hostname || h.endsWith('.' + hostname) || hostname.endsWith('.' + h);
      } catch {
        return url.includes(hostname);
      }
    });
  }

  // ---- Daemon communication ----

  function daemonRequest(path, body, method = 'POST') {
    return new Promise((resolve, reject) => {
      chrome.runtime.sendMessage({ type: 'DAEMON_REQUEST', path, body, method }, (resp) => {
        if (chrome.runtime.lastError) {
          reject(new Error(chrome.runtime.lastError.message));
          return;
        }
        if (resp?.ok) resolve(resp.data);
        else reject(new Error(resp?.error || 'Unknown error'));
      });
    });
  }

  async function loadItems() {
    try {
      const info = await daemonRequest('/info', {});
      if (info.data?.locked !== false) {
        vaultLocked = true;
        return [];
      }
      vaultLocked = false;
      const serverVersion = info.data?.dataVersion ?? -1;
      if (cachedItems !== null && cachedDataVersion === serverVersion) {
        return cachedItems;
      }
      const list = await daemonRequest('/items/list?type=1', null, 'GET');
      cachedItems = (list.items || []).filter((i) => !i.deleted);
      cachedDataVersion = serverVersion;
      return cachedItems;
    } catch {
      return [];
    }
  }

  // ---- Badge update ----

  async function updateBadge() {
    const items = await loadItems();
    const hostname = location.hostname;
    const count = items.filter((i) => matchesHostname(i, hostname)).length;
    chrome.runtime.sendMessage({ type: 'SET_BADGE', count });
  }

  // ---- Inline dropdown ----

  function removeDropdown() {
    if (activeDropdown) {
      activeDropdown.remove();
      activeDropdown = null;
      activeDropdownFields = null;
    }
  }

  function shouldKeepDropdown() {
    if (!activeDropdown || !activeDropdownFields) return false;
    const active = document.activeElement;
    const { pwField, userField } = activeDropdownFields;
    return active === pwField || active === userField;
  }

  function createDropdown(matches, anchorField, pwField, userField) {
    removeDropdown();
    if (matches.length === 0) return;
    ensureStyles();

    const dropdown = document.createElement('div');
    dropdown.className = 'mypassword-dropdown';
    dropdown.style.cssText = `
      position: absolute;
      z-index: 2147483647;
      background: #fff;
      border: 1px solid #d0d0d0;
      border-radius: 6px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.15);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 13px;
      min-width: 220px;
      max-width: 340px;
      overflow: hidden;
    `;

    const rect = anchorField.getBoundingClientRect();
    dropdown.style.top = `${window.scrollY + rect.bottom + 4}px`;
    dropdown.style.left = `${window.scrollX + rect.left}px`;

    for (const item of matches) {
      const row = document.createElement('div');
      row.className = 'mypassword-login-row';
      row.style.cssText = `
        padding: 8px 12px;
        cursor: pointer;
        display: flex;
        flex-direction: column;
        gap: 2px;
        border-bottom: 1px solid #f0f0f0;
      `;
      row.innerHTML = `
        <span style="font-weight:600;color:#222;">${escapeHtml(item.data?.title || '')}</span>
        <span style="color:#888;font-size:11px;">${escapeHtml(item.data?.username || '')}</span>
      `;
      row.addEventListener('mousedown', (e) => {
        e.preventDefault();
        fetchAndFill(item, pwField, userField);
        removeDropdown();
      });
      dropdown.appendChild(row);
    }

    document.body.appendChild(dropdown);
    activeDropdown = dropdown;
    activeDropdownFields = { pwField, userField };
  }

  function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // React-compatible field fill
  function setNativeValue(el, value) {
    const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
    if (nativeInputValueSetter) {
      nativeInputValueSetter.call(el, value);
    } else {
      el.value = value;
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function fillFields(item, pwField, userField) {
    if (userField) setNativeValue(userField, item.data?.username || '');
    if (pwField && item.data?.password) setNativeValue(pwField, item.data.password);
  }

  async function fetchAndFill(item, pwField, userField) {
    let fullItem = item;
    if (item.data?.password === '') {
      try {
        const resp = await daemonRequest(`/items/${item.id}/get`, null, 'GET');
        if (resp.item) fullItem = resp.item;
      } catch {}
    }
    // re-find fields after async fetch in case DOM changed
    const fields = findCredentialFields();
    if (fields.length > 0) {
      pwField = fields[0].pwField;
      userField = fields[0].userField;
    }
    fillFields(fullItem, pwField, userField);
  }

  // ---- Field focus listeners ----

  async function onFieldFocus(focusedField, pwField, userField) {
    const items = await loadItems();
    if (vaultLocked) {
      createLockedDropdown(focusedField, pwField, userField);
      return;
    }
    const hostname = location.hostname;
    const matches = items.filter((i) => matchesHostname(i, hostname));
    createDropdown(matches, focusedField, pwField, userField);
  }

  function createLockedDropdown(anchorField, pwField, userField) {
    removeDropdown();
    const dropdown = document.createElement('div');
    dropdown.className = 'mypassword-dropdown';
    dropdown.style.cssText = `
      position: absolute;
      z-index: 2147483647;
      background: #fff;
      border: 1px solid #d0d0d0;
      border-radius: 6px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.15);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 13px;
      min-width: 220px;
      padding: 10px 14px;
      color: #888;
    `;
    const rect = anchorField.getBoundingClientRect();
    dropdown.style.top = `${window.scrollY + rect.bottom + 4}px`;
    dropdown.style.left = `${window.scrollX + rect.left}px`;
    dropdown.textContent = 'Please unlock MyPassword';
    document.body.appendChild(dropdown);
    activeDropdown = dropdown;
    activeDropdownFields = { pwField, userField };
  }

  function attachListeners(fields) {
    for (const { pwField, userField } of fields) {
      const addFocusBlur = (field) => {
        field.addEventListener('focus', () => onFieldFocus(field, pwField, userField));
        field.addEventListener('blur', () => {
          setTimeout(() => {
            if (!shouldKeepDropdown()) {
              removeDropdown();
            }
          }, 150);
        });
      };
      if (pwField) addFocusBlur(pwField);
      if (userField) addFocusBlur(userField);
    }
  }

  // Dismiss dropdown when clicking outside it and associated fields
  document.addEventListener('mousedown', (e) => {
    if (!activeDropdown) return;
    if (activeDropdown.contains(e.target)) return;
    if (activeDropdownFields) {
      const { pwField, userField } = activeDropdownFields;
      if (e.target === pwField || e.target === userField) return;
    }
    removeDropdown();
  }, true);

  // ---- Form submit interception ----

  function interceptFormSubmit() {
    document.addEventListener('submit', onFormSubmit, true);
    document.addEventListener('click', onSubmitButtonClick, true);
  }

  function onFormSubmit(e) {
    const form = e.target;
    if (!(form instanceof HTMLFormElement)) return;
    const pwField = form.querySelector('input[type="password"]');
    if (!pwField || !pwField.value) return;
    const userField = form.querySelector('input[type="email"], input[type="text"], input[autocomplete*="user"], input:not([type])');
    captureCredential(userField?.value || '', pwField.value);
  }

  function onSubmitButtonClick(e) {
    const btn = e.target.closest('button[type="submit"], input[type="submit"], button:not([type])');
    if (!btn) return;
    // find the nearest form or credential fields
    const form = btn.closest('form');
    if (form) return; // will be handled by onFormSubmit
    // no form — look for visible password fields on the page
    const fields = findCredentialFields();
    for (const { pwField, userField } of fields) {
      if (pwField && pwField.value) {
        captureCredential(userField?.value || '', pwField.value);
        return;
      }
    }
  }

  async function captureCredential(username, password) {
    if (vaultLocked || !password) return;
    // for multi-page login: if we only have password, use cached username
    if (!username && pendingUsername) {
      username = pendingUsername;
      pendingUsername = null;
    }
    const hostname = location.hostname;
    // Persist to session storage so the save panel survives form submission / navigation.
    // Fire-and-forget — the write completes before the new page's content script runs.
    chrome.storage.session.set({
      pendingCredential: { hostname, username, password, timestamp: Date.now() }
    });
    const items = await loadItems();
    const matched = items.filter((i) => matchesHostname(i, hostname));
    await preparePanelAndShow(hostname, username, password, matched);
  }

  function hostnameRelated(a, b) {
    return a === b || a.endsWith('.' + b) || b.endsWith('.' + a);
  }

  async function checkPendingCredential() {
    try {
      const stored = await chrome.storage.session.get(['pendingCredential']);
      const pc = stored.pendingCredential;
      if (!pc) return;
      // Expire after 60 seconds so stale creds don't resurrect later
      if (Date.now() - pc.timestamp > 60_000) {
        chrome.storage.session.remove(['pendingCredential']);
        return;
      }
      if (!hostnameRelated(pc.hostname, location.hostname)) return;
      // Consume the pending credential immediately so it only shows once
      chrome.storage.session.remove(['pendingCredential']);
      const items = await loadItems();
      if (vaultLocked) return;
      const matched = items.filter((i) => matchesHostname(i, pc.hostname));
      await preparePanelAndShow(pc.hostname, pc.username, pc.password, matched);
    } catch {}
  }

  // Cache username for multi-page login (page with only username field)
  function cacheUsername() {
    const fields = findCredentialFields();
    for (const { pwField, userField } of fields) {
      if (!pwField && userField && userField.value) {
        chrome.storage.session.set({ pendingUsername: userField.value, pendingHost: location.hostname });
      }
    }
  }

  // ---- Save / Update panel ----

  function removeSavePanel() {
    if (activeSavePanel) {
      activeSavePanel.remove();
      activeSavePanel = null;
    }
  }

  // Decide what (if anything) to show based on existing matches.
  //
  // Rules:
  // - If no existing login has the same username  → offer "Create".
  // - If a login matches by username but the password is different → offer "Update".
  // - If a login matches by username AND has the same password → show nothing
  //   (nothing to save).
  //
  // Cached items from /items/list don't include passwords, so for username
  // matches we must fetch the full item via /items/{id}/get to compare.
  async function preparePanelAndShow(hostname, username, password, hostnameMatches) {
    const usernameMatches = hostnameMatches.filter(
      (i) => (i.data?.username || '') === (username || '')
    );

    if (usernameMatches.length === 0) {
      showSavePanel(hostname, username, password, true, []);
      return;
    }

    const updateItems = [];
    for (const item of usernameMatches) {
      try {
        const resp = await daemonRequest(`/items/${item.id}/get`, null, 'GET');
        const fullItem = resp.item;
        if ((fullItem.data?.password || '') !== password) {
          updateItems.push(fullItem);
        }
      } catch {
        // fetch failed — fall back to offering update with stale data
        updateItems.push(item);
      }
    }

    if (updateItems.length === 0) {
      // same username + same password already stored — nothing to save
      return;
    }

    showSavePanel(hostname, username, password, false, updateItems);
  }

  function showSavePanel(hostname, username, password, showCreate, updateItems) {
    removeSavePanel();

    const panel = document.createElement('div');
    panel.style.cssText = `
      position: fixed;
      top: 10px;
      right: 10px;
      z-index: 2147483647;
      background: #fff;
      border: 1px solid #d0d0d0;
      border-radius: 8px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.2);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 13px;
      width: 320px;
      overflow: hidden;
    `;

    // header
    const header = document.createElement('div');
    header.style.cssText = `
      padding: 10px 14px;
      background: #4a90e2;
      color: #fff;
      font-weight: 600;
      font-size: 14px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    `;
    header.textContent = 'MyPassword';
    const closeBtn = document.createElement('span');
    closeBtn.textContent = '\u00d7';
    closeBtn.style.cssText = 'cursor:pointer;font-size:18px;line-height:1;';
    closeBtn.addEventListener('click', removeSavePanel);
    header.appendChild(closeBtn);
    panel.appendChild(header);

    // body
    const body = document.createElement('div');
    body.style.cssText = 'padding: 14px;';

    // "Create new" section — only if the submitted username isn't already saved
    if (showCreate) {
      const createLabel = document.createElement('div');
      createLabel.style.cssText = 'color: #222; font-weight: 500; margin-bottom: 6px;';
      createLabel.textContent = `Create new login for ${hostname}`;
      body.appendChild(createLabel);

      const userLine = document.createElement('div');
      userLine.style.cssText = 'color: #888; font-size: 11px; margin-bottom: 10px;';
      userLine.textContent = `User: ${username || '(empty)'}`;
      body.appendChild(userLine);

      const createBtnRow = document.createElement('div');
      createBtnRow.style.cssText = 'display: flex; gap: 8px; justify-content: flex-start;';
      body.appendChild(createBtnRow);

      const createCancelBtn = makeSecondaryBtn('Cancel', removeSavePanel);
      const createBtn = makePrimaryBtn('Create', async () => {
        createBtn.disabled = true;
        createBtn.textContent = 'Saving...';
        try {
          await daemonRequest('/items/create', {
            item: {
              item_type: ITEM_TYPE_LOGIN,
              data: {
                title: document.title || hostname,
                username: username || '',
                password: password,
                websites: ['https://' + hostname],
                memo: ''
              }
            }
          });
          cachedItems = null;
          cachedDataVersion = -1;
          showSuccessPanel('Login saved!');
        } catch (e) {
          createBtn.textContent = 'Error: ' + e.message;
          createBtn.disabled = false;
        }
      });
      createBtnRow.appendChild(createBtn);
      createBtnRow.appendChild(createCancelBtn);
    }

    // "Update existing" sections (one per match)
    for (let idx = 0; idx < updateItems.length; idx++) {
      const item = updateItems[idx];
      // Only add separator if something came before this update section
      if (showCreate || idx > 0) {
        const sep = document.createElement('div');
        sep.style.cssText = 'border-top: 1px solid #eee; margin: 14px -14px;';
        body.appendChild(sep);
      }

      const updateLabel = document.createElement('div');
      updateLabel.style.cssText = 'color: #222; font-weight: 500; margin-bottom: 6px;';
      updateLabel.textContent = `Update ${item.data?.title || hostname}`;
      body.appendChild(updateLabel);

      const existingUser = document.createElement('div');
      existingUser.style.cssText = 'color: #888; font-size: 11px; margin-bottom: 10px;';
      existingUser.textContent = `User: ${item.data?.username || '(empty)'}`;
      body.appendChild(existingUser);

      const updateBtnRow = document.createElement('div');
      updateBtnRow.style.cssText = 'display: flex; gap: 8px; justify-content: flex-start;';
      body.appendChild(updateBtnRow);

      const updateCancelBtn = makeSecondaryBtn('Cancel', removeSavePanel);
      const updateBtn = makePrimaryBtn('Update', async () => {
        updateBtn.disabled = true;
        updateBtn.textContent = 'Updating...';
        try {
          const updatedData = { ...item.data, password: password };
          if (username) updatedData.username = username;
          await daemonRequest(`/items/${item.id}/update`, {
            item: {
              item_type: ITEM_TYPE_LOGIN,
              data: updatedData
            }
          });
          cachedItems = null;
          cachedDataVersion = -1;
          showSuccessPanel('Login updated!');
        } catch (e) {
          updateBtn.textContent = 'Error: ' + e.message;
          updateBtn.disabled = false;
        }
      });
      updateBtnRow.appendChild(updateBtn);
      updateBtnRow.appendChild(updateCancelBtn);
    }

    panel.appendChild(body);
    document.body.appendChild(panel);
    activeSavePanel = panel;
  }

  function makePrimaryBtn(text, onClick) {
    const btn = document.createElement('button');
    btn.textContent = text;
    btn.style.cssText = `
      padding: 6px 16px; border: none; border-radius: 5px;
      background: #4a90e2; color: #fff; cursor: pointer; font-size: 13px; font-weight: 500;
    `;
    btn.addEventListener('click', onClick);
    return btn;
  }

  function makeSecondaryBtn(text, onClick) {
    const btn = document.createElement('button');
    btn.textContent = text;
    btn.style.cssText = `
      padding: 6px 16px; border: 1px solid #d0d0d0; border-radius: 5px;
      background: #fff; color: #444; cursor: pointer; font-size: 13px;
    `;
    btn.addEventListener('click', onClick);
    return btn;
  }

  function showSuccessPanel(message) {
    removeSavePanel();
    const panel = document.createElement('div');
    panel.style.cssText = `
      position: fixed;
      top: 10px;
      right: 10px;
      z-index: 2147483647;
      background: #fff;
      border: 1px solid #d0d0d0;
      border-radius: 8px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.2);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 14px;
      padding: 14px 18px;
      color: #27ae60;
      font-weight: 500;
    `;
    panel.textContent = message;
    document.body.appendChild(panel);
    activeSavePanel = panel;
    setTimeout(removeSavePanel, 3000);
  }

  // ---- DO_FILL from popup ----

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg.type === 'DO_FILL') {
      const fields = findCredentialFields();
      if (fields.length > 0) {
        const { pwField, userField } = fields[0];
        fillFields(msg.item, pwField, userField);
      }
    }
    if (msg.type === 'SHOW_PASSKEY_PROMPT') {
      showPasskeyCreatePanel(msg.request);
    }
    if (msg.type === 'SHOW_PASSKEY_GET_PROMPT') {
      showPasskeyGetPanel(msg.request);
    }
    if (msg.type === 'HIDE_PASSKEY_PROMPT') {
      removePasskeyPanel();
    }
  });

  // ---- Passkey create panel (in-page) ----

  let activePasskeyPanel = null;

  function removePasskeyPanel() {
    if (activePasskeyPanel) {
      activePasskeyPanel.remove();
      activePasskeyPanel = null;
    }
  }

  async function showPasskeyCreatePanel(req) {
    removeSavePanel();
    removePasskeyPanel();
    ensureStyles();

    let finished = false;
    // Forward the result to the background service worker which will call
    // chrome.webAuthenticationProxy.completeCreateRequest. Panel lifecycle is
    // managed by the caller so users can see a brief success/error message.
    function sendResult(result) {
      if (finished) return;
      finished = true;
      chrome.runtime.sendMessage({ type: 'PASSKEY_CREATE_RESULT', ...result });
    }
    function finishAndClose(result) {
      sendResult(result);
      removePasskeyPanel();
    }

    const panel = document.createElement('div');
    panel.style.cssText = `
      position: fixed;
      top: 10px;
      right: 10px;
      z-index: 2147483647;
      background: #fff;
      border: 1px solid #d0d0d0;
      border-radius: 8px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.2);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 13px;
      width: 340px;
      overflow: hidden;
    `;

    // header
    const header = document.createElement('div');
    header.style.cssText = `
      padding: 10px 14px;
      background: #4a90e2;
      color: #fff;
      font-weight: 600;
      font-size: 14px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    `;
    header.textContent = 'Add Passkey';
    const closeBtn = document.createElement('span');
    closeBtn.textContent = '\u00d7';
    closeBtn.style.cssText = 'cursor:pointer;font-size:18px;line-height:1;';
    closeBtn.addEventListener('click', () => {
      finishAndClose({ ok: false, errorName: 'NotAllowedError', errorMessage: 'User cancelled' });
    });
    header.appendChild(closeBtn);
    panel.appendChild(header);

    // body
    const body = document.createElement('div');
    body.style.cssText = 'padding: 14px;';

    const rpInfo = document.createElement('div');
    rpInfo.style.cssText = 'margin-bottom: 10px; font-size: 12px; color: #555;';
    body.appendChild(rpInfo);

    const status = document.createElement('div');
    status.style.cssText = 'margin-bottom: 10px; font-size: 12px; color: #666;';
    status.textContent = 'Loading...';
    body.appendChild(status);

    const list = document.createElement('div');
    list.style.cssText = 'max-height: 240px; overflow-y: auto;';
    body.appendChild(list);

    const footer = document.createElement('div');
    footer.style.cssText = 'display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px;';
    const cancelBtn = makeSecondaryBtn('Cancel', () => {
      finishAndClose({ ok: false, errorName: 'NotAllowedError', errorMessage: 'User cancelled' });
    });
    footer.appendChild(cancelBtn);
    body.appendChild(footer);

    panel.appendChild(body);
    document.body.appendChild(panel);
    activePasskeyPanel = panel;

    function setStatus(msg) {
      status.textContent = msg;
      status.style.color = '#666';
    }
    function setStatusError(msg) {
      status.textContent = msg;
      status.style.color = '#c0392b';
      list.innerHTML = '';
    }

    // Parse the WebAuthn options
    let options;
    try {
      options = JSON.parse(req.requestDetailsJson);
    } catch (e) {
      setStatusError('Invalid request: ' + e.message);
      return;
    }
    const rpId = options.rp?.id || '';
    const rpName = options.rp?.name || rpId;
    rpInfo.innerHTML =
      'Site: <span style="font-weight:600;color:#222;">' + escapeHtml(rpName) + '</span>' +
      ' <span style="color:#999;">(' + escapeHtml(rpId) + ')</span>';

    // 1. Check vault is unlocked
    try {
      const info = await daemonRequest('/info', {});
      if (info.data?.locked !== false) {
        const m = 'Unlock the vault before add a passkey';
        setStatusError(m);
        sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: m });
        return;
      }
    } catch (e) {
      const m = 'Daemon error: ' + e.message;
      setStatusError(m);
      sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: m });
      return;
    }

    // 2. Load login items, filter by rpId
    let items;
    try {
      const resp = await daemonRequest('/items/list?type=1', null, 'GET');
      items = (resp.items || []).filter((i) => !i.deleted);
    } catch (e) {
      const m = 'Failed to load items: ' + e.message;
      setStatusError(m);
      sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: m });
      return;
    }
    const matched = items.filter((i) => matchesHostname(i, rpId));
    if (matched.length === 0) {
      const m = 'No login for ' + rpId + ' in vault';
      setStatusError(m);
      sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: m });
      return;
    }

    // 3. Render pickable list
    setStatus('Choose a login to attach the passkey to:');
    for (const item of matched) {
      const row = document.createElement('div');
      row.className = 'mypassword-passkey-row';
      row.style.cssText = `
        padding: 8px 10px;
        cursor: pointer;
        border-radius: 5px;
        border: 1px solid #eee;
        margin-bottom: 6px;
        display: flex;
        flex-direction: column;
        gap: 2px;
      `;
      row.innerHTML = `
        <span style="font-weight:600;color:#222;">${escapeHtml(item.data?.title || '')}</span>
        <span style="color:#888;font-size:11px;">${escapeHtml(item.data?.username || '')}</span>
      `;
      row.addEventListener('click', async () => {
        setStatus('Creating passkey...');
        list.innerHTML = '';
        try {
          const body = {
            itemId: item.id,
            origin: window.location.origin,
            options: options,
          };
          console.log('POST /passkeys/add body:', JSON.stringify(body, null, 2));
          const resp = await daemonRequest('/passkeys/add', body);
          // Daemon reports VaultException as 200 + {error, errorMessage}.
          if (resp && resp.error) {
            const message = resp.errorMessage || resp.error;
            setStatusError(message);
            sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: message });
            return;
          }
          // Success: the daemon returns a PublicKeyCredentialJSON object
          // (id, rawId, type, authenticatorAttachment, response, clientExtensionResults)
          // that Chrome's completeCreateRequest accepts verbatim.
          if (!resp || !resp.id || !resp.response || !resp.response.attestationObject) {
            const message = 'Malformed response from desktop';
            setStatusError(message);
            sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: message });
            return;
          }
          status.style.color = '#27ae60';
          status.textContent = 'Passkey saved to ' + (item.data?.title || rpId);
          sendResult({ ok: true, responseJson: JSON.stringify(resp) });
          setTimeout(removePasskeyPanel, 1500);
        } catch (e) {
          const message = 'Error: ' + e.message;
          setStatusError(message);
          sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: e.message });
        }
      });
      list.appendChild(row);
    }
  }

  // ---- Passkey get (assertion) panel (in-page) ----

  async function showPasskeyGetPanel(req) {
    removeSavePanel();
    removePasskeyPanel();
    ensureStyles();

    let finished = false;
    function sendResult(result) {
      if (finished) return;
      finished = true;
      chrome.runtime.sendMessage({ type: 'PASSKEY_GET_RESULT', ...result });
    }
    function finishAndClose(result) {
      sendResult(result);
      removePasskeyPanel();
    }

    const panel = document.createElement('div');
    panel.style.cssText = `
      position: fixed;
      top: 10px;
      right: 10px;
      z-index: 2147483647;
      background: #fff;
      border: 1px solid #d0d0d0;
      border-radius: 8px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.2);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 13px;
      width: 340px;
      overflow: hidden;
    `;

    const header = document.createElement('div');
    header.style.cssText = `
      padding: 10px 14px;
      background: #4a90e2;
      color: #fff;
      font-weight: 600;
      font-size: 14px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    `;
    header.textContent = 'Sign in with passkey';
    const closeBtn = document.createElement('span');
    closeBtn.textContent = '\u00d7';
    closeBtn.style.cssText = 'cursor:pointer;font-size:18px;line-height:1;';
    closeBtn.addEventListener('click', () => {
      finishAndClose({ ok: false, errorName: 'NotAllowedError', errorMessage: 'User cancelled' });
    });
    header.appendChild(closeBtn);
    panel.appendChild(header);

    const body = document.createElement('div');
    body.style.cssText = 'padding: 14px;';

    const rpInfo = document.createElement('div');
    rpInfo.style.cssText = 'margin-bottom: 10px; font-size: 12px; color: #555;';
    body.appendChild(rpInfo);

    const status = document.createElement('div');
    status.style.cssText = 'margin-bottom: 10px; font-size: 12px; color: #666;';
    status.textContent = 'Loading...';
    body.appendChild(status);

    const list = document.createElement('div');
    list.style.cssText = 'max-height: 240px; overflow-y: auto;';
    body.appendChild(list);

    const footer = document.createElement('div');
    footer.style.cssText = 'display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px;';
    const cancelBtn = makeSecondaryBtn('Cancel', () => {
      finishAndClose({ ok: false, errorName: 'NotAllowedError', errorMessage: 'User cancelled' });
    });
    footer.appendChild(cancelBtn);
    body.appendChild(footer);

    panel.appendChild(body);
    document.body.appendChild(panel);
    activePasskeyPanel = panel;

    function setStatus(msg) {
      status.textContent = msg;
      status.style.color = '#666';
    }
    function setStatusError(msg) {
      status.textContent = msg;
      status.style.color = '#c0392b';
      list.innerHTML = '';
    }

    // Parse the WebAuthn PublicKeyCredentialRequestOptions.
    let options;
    try {
      options = JSON.parse(req.requestDetailsJson);
    } catch (e) {
      setStatusError('Invalid request: ' + e.message);
      sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: e.message });
      return;
    }
    const rpId = options.rpId || '';
    rpInfo.innerHTML =
      'Site: <span style="font-weight:600;color:#222;">' + escapeHtml(rpId) + '</span>';

    // 1. Vault unlocked?
    try {
      const info = await daemonRequest('/info', {});
      if (info.data?.locked !== false) {
        const m = 'Unlock the vault before signing in';
        setStatusError(m);
        sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: m });
        return;
      }
    } catch (e) {
      const m = 'Daemon error: ' + e.message;
      setStatusError(m);
      sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: m });
      return;
    }

    // 2. Load login items and filter to ones with a matching passkey.
    let items;
    try {
      const resp = await daemonRequest('/items/list?type=1', null, 'GET');
      items = (resp.items || []).filter((i) => !i.deleted);
    } catch (e) {
      const m = 'Failed to load items: ' + e.message;
      setStatusError(m);
      sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: m });
      return;
    }

    // /items/list returns lightweight items — for logins the passkey field
    // is included in data, so we can filter without extra round-trips.
    const allow = Array.isArray(options.allowCredentials) ? options.allowCredentials : [];
    const allowIds = allow.map((c) => c && c.id).filter(Boolean);
    const matched = items.filter((i) => {
      const pk = i.data?.passkey;
      if (!pk) return false;
      if (pk.relyingPartyId !== rpId) return false;
      if (allowIds.length > 0 && !allowIds.includes(pk.b64CredentialId)) return false;
      return true;
    });

    if (matched.length === 0) {
      const m = 'No passkey for ' + rpId + ' in vault';
      setStatusError(m);
      sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: m });
      return;
    }

    // 3. Render pickable list.
    setStatus('Choose a passkey to sign in with:');
    for (const item of matched) {
      const row = document.createElement('div');
      row.className = 'mypassword-passkey-row';
      row.style.cssText = `
        padding: 8px 10px;
        cursor: pointer;
        border-radius: 5px;
        border: 1px solid #eee;
        margin-bottom: 6px;
        display: flex;
        flex-direction: column;
        gap: 2px;
      `;
      const pk = item.data.passkey;
      const pkUser = pk.displayName || pk.username || '';
      row.innerHTML = `
        <span style="font-weight:600;color:#222;">${escapeHtml(item.data?.title || '')}</span>
        <span style="color:#888;font-size:11px;">${escapeHtml(pkUser)}</span>
      `;
      row.addEventListener('click', async () => {
        setStatus('Signing in...');
        list.innerHTML = '';
        try {
          const body = {
            itemId: item.id,
            origin: window.location.origin,
            options: options,
          };
          console.log('POST /passkeys/login body:', JSON.stringify(body, null, 2));
          const resp = await daemonRequest('/passkeys/login', body);
          if (resp && resp.error) {
            const message = resp.errorMessage || resp.error;
            setStatusError(message);
            sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: message });
            return;
          }
          if (!resp || !resp.id || !resp.response
              || !resp.response.signature || !resp.response.authenticatorData) {
            const message = 'Malformed response from desktop';
            setStatusError(message);
            sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: message });
            return;
          }
          status.style.color = '#27ae60';
          status.textContent = 'Signed in to ' + (item.data?.title || rpId);
          sendResult({ ok: true, responseJson: JSON.stringify(resp) });
          setTimeout(removePasskeyPanel, 1500);
        } catch (e) {
          const message = 'Error: ' + e.message;
          setStatusError(message);
          sendResult({ ok: false, errorName: 'NotAllowedError', errorMessage: e.message });
        }
      });
      list.appendChild(row);
    }
  }

  // ---- Init ----

  async function init() {
    // restore cached username for multi-page login
    try {
      const stored = await chrome.storage.session.get(['pendingUsername', 'pendingHost']);
      if (stored.pendingHost === location.hostname && stored.pendingUsername) {
        pendingUsername = stored.pendingUsername;
        chrome.storage.session.remove(['pendingUsername', 'pendingHost']);
      }
    } catch {}

    interceptFormSubmit();

    // Check if a previous page submitted a login form — resurrect the save panel.
    checkPendingCredential();

    const fields = findCredentialFields();
    if (fields.length > 0) {
      attachListeners(fields);
      updateBadge();
      // cache username for standalone username fields (multi-page login)
      for (const { pwField, userField } of fields) {
        if (!pwField && userField) {
          const form = userField.closest('form');
          if (form) {
            form.addEventListener('submit', () => {
              if (userField.value) {
                chrome.storage.session.set({ pendingUsername: userField.value, pendingHost: location.hostname });
              }
            }, true);
          }
        }
      }
    }
  }

  // Also watch for dynamically added forms (SPAs)
  const observer = new MutationObserver(() => {
    const fields = findCredentialFields();
    if (fields.length > 0) {
      attachListeners(fields);
      observer.disconnect();
      updateBadge();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });

  init();
})();
