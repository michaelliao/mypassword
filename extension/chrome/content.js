// Content script - injected at document_idle on all pages
// Detects credential fields, updates badge, shows inline fill dropdown

(function () {
  'use strict';

  const ITEM_TYPE_LOGIN = 1;

  let cachedItems = null;  // items list, cached until dataVersion changes
  let cachedDataVersion = -1;
  let vaultLocked = false;
  let activeDropdown = null;
  let activeSavePanel = null;
  let pendingUsername = null; // for multi-page login: cache username from page 1

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
    }
  }

  function createDropdown(matches, anchorField, pwField, userField) {
    removeDropdown();
    if (matches.length === 0) return;

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
      row.addEventListener('mouseenter', () => { row.style.background = '#f5f7ff'; });
      row.addEventListener('mouseleave', () => { row.style.background = ''; });
      row.addEventListener('mousedown', (e) => {
        e.preventDefault();
        fetchAndFill(item, pwField, userField);
        removeDropdown();
      });
      dropdown.appendChild(row);
    }

    document.body.appendChild(dropdown);
    activeDropdown = dropdown;
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
      createLockedDropdown(focusedField);
      return;
    }
    const hostname = location.hostname;
    const matches = items.filter((i) => matchesHostname(i, hostname));
    createDropdown(matches, focusedField, pwField, userField);
  }

  function createLockedDropdown(anchorField) {
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
  }

  function attachListeners(fields) {
    for (const { pwField, userField } of fields) {
      const addFocusBlur = (field) => {
        field.addEventListener('focus', () => onFieldFocus(field, pwField, userField));
        field.addEventListener('blur', () => setTimeout(removeDropdown, 150));
      };
      if (pwField) addFocusBlur(pwField);
      if (userField) addFocusBlur(userField);
    }
  }

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
    const items = await loadItems();
    const hostname = location.hostname;
    const matched = items.filter((i) => matchesHostname(i, hostname));
    showSavePanel(hostname, username, password, matched);
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

  function showSavePanel(hostname, username, password, matchedItems) {
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

    // options list
    const list = document.createElement('div');
    list.style.cssText = 'max-height: 240px; overflow-y: auto;';

    // "Create new" option
    const createRow = makeOptionRow(
      `Create new login for ${hostname}`,
      null,
      () => showConfirmPanel('save', hostname, username, password, null)
    );
    list.appendChild(createRow);

    // "Update existing" options
    for (const item of matchedItems) {
      const label = `Update ${item.data?.title || hostname} ${item.data?.username || ''}`.trim();
      const updateRow = makeOptionRow(
        label,
        item.data?.username,
        () => showConfirmPanel('update', hostname, username, password, item)
      );
      list.appendChild(updateRow);
    }

    panel.appendChild(list);
    document.body.appendChild(panel);
    activeSavePanel = panel;
  }

  function makeOptionRow(text, subtitle, onClick) {
    const row = document.createElement('div');
    row.style.cssText = `
      padding: 10px 14px;
      cursor: pointer;
      border-bottom: 1px solid #f0f0f0;
    `;
    const titleSpan = document.createElement('div');
    titleSpan.style.cssText = 'color: #222;';
    titleSpan.textContent = text;
    row.appendChild(titleSpan);
    if (subtitle) {
      const sub = document.createElement('div');
      sub.style.cssText = 'color: #888; font-size: 11px; margin-top: 2px;';
      sub.textContent = subtitle;
      row.appendChild(sub);
    }
    row.addEventListener('mouseenter', () => { row.style.background = '#f5f7ff'; });
    row.addEventListener('mouseleave', () => { row.style.background = ''; });
    row.addEventListener('click', onClick);
    return row;
  }

  function showConfirmPanel(action, hostname, username, password, existingItem) {
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

    const isSave = action === 'save';
    const title = isSave ? `Save login for ${hostname}?` : `Update login for ${hostname}?`;

    // header
    const header = document.createElement('div');
    header.style.cssText = `
      padding: 10px 14px;
      background: #4a90e2;
      color: #fff;
      font-weight: 600;
      font-size: 14px;
    `;
    header.textContent = title;
    panel.appendChild(header);

    // body
    const body = document.createElement('div');
    body.style.cssText = 'padding: 14px;';
    const userLine = document.createElement('div');
    userLine.style.cssText = 'color: #444; margin-bottom: 14px;';
    userLine.textContent = `User: ${username || '(empty)'}`;
    body.appendChild(userLine);

    // buttons
    const btnRow = document.createElement('div');
    btnRow.style.cssText = 'display: flex; gap: 8px; justify-content: flex-end;';

    const cancelBtn = document.createElement('button');
    cancelBtn.textContent = 'Cancel';
    cancelBtn.style.cssText = `
      padding: 6px 16px; border: 1px solid #d0d0d0; border-radius: 5px;
      background: #fff; color: #444; cursor: pointer; font-size: 13px;
    `;
    cancelBtn.addEventListener('click', removeSavePanel);

    const confirmBtn = document.createElement('button');
    confirmBtn.textContent = isSave ? 'Save' : 'Update';
    confirmBtn.style.cssText = `
      padding: 6px 16px; border: none; border-radius: 5px;
      background: #4a90e2; color: #fff; cursor: pointer; font-size: 13px; font-weight: 500;
    `;
    confirmBtn.addEventListener('click', async () => {
      confirmBtn.disabled = true;
      confirmBtn.textContent = isSave ? 'Saving...' : 'Updating...';
      try {
        if (isSave) {
          await daemonRequest('/items/create', {
            item: {
              item_type: ITEM_TYPE_LOGIN,
              data: {
                title: hostname,
                username: username || '',
                password: password,
                websites: [hostname],
                memo: ''
              }
            }
          });
        } else {
          // update existing item's password (and username if changed)
          const updatedData = { ...existingItem.data, password: password };
          if (username) updatedData.username = username;
          await daemonRequest(`/items/${existingItem.id}/update`, {
            item: {
              item_type: ITEM_TYPE_LOGIN,
              data: updatedData
            }
          });
        }
        // invalidate cache so next fill picks up changes
        cachedItems = null;
        cachedDataVersion = -1;
        showSuccessPanel(isSave ? 'Login saved!' : 'Login updated!');
      } catch (e) {
        confirmBtn.textContent = 'Error: ' + e.message;
        confirmBtn.disabled = false;
      }
    });

    btnRow.appendChild(cancelBtn);
    btnRow.appendChild(confirmBtn);
    body.appendChild(btnRow);
    panel.appendChild(body);

    document.body.appendChild(panel);
    activeSavePanel = panel;
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
  });

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
