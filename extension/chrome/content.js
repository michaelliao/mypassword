// Content script - injected at document_idle on all pages
// Detects credential fields, updates badge, shows inline fill dropdown

(function () {
  'use strict';

  const ITEM_TYPE_LOGIN = 1;

  let cachedItems = null;  // items list, cached per page load
  let activeDropdown = null;

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
    if (cachedItems !== null) return cachedItems;
    try {
      const info = await daemonRequest('/info', {});
      if (info.data?.locked !== false) return [];
      const list = await daemonRequest('/items/list?type=1', null, 'GET');
      cachedItems = (list.items || []).filter((i) => !i.deleted);
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

  function createDropdown(matches, pwField, userField) {
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

    const anchorField = pwField || userField;
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
        fillFields(item, pwField, userField);
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
    if (pwField) setNativeValue(pwField, item.data?.password || '');
    if (userField) setNativeValue(userField, item.data?.username || '');
  }

  // ---- Field focus listeners ----

  async function onFieldFocus(pwField, userField) {
    const items = await loadItems();
    const hostname = location.hostname;
    const matches = items.filter((i) => matchesHostname(i, hostname));
    createDropdown(matches, pwField, userField);
  }

  function attachListeners(fields) {
    for (const { pwField, userField } of fields) {
      const addFocusBlur = (field) => {
        field.addEventListener('focus', () => onFieldFocus(pwField, userField));
        field.addEventListener('blur', () => setTimeout(removeDropdown, 150));
      };
      if (pwField) addFocusBlur(pwField);
      if (userField) addFocusBlur(userField);
    }
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

  function init() {
    const fields = findCredentialFields();
    if (fields.length > 0) {
      attachListeners(fields);
      updateBadge();
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
