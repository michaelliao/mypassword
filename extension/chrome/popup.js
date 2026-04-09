// Popup state machine: loading → locked/unlocked/error

'use strict';

const ITEM_TYPE_LOGIN = 1;
const ITEM_TYPE_NOTE = 2;
const ITEM_TYPE_IDENTITY = 3;

const TYPE_LABELS = {
  [ITEM_TYPE_LOGIN]: 'Logins',
  [ITEM_TYPE_NOTE]: 'Notes',
  [ITEM_TYPE_IDENTITY]: 'Identities',
};

// ---- DOM refs ----
const views = {
  loading: document.getElementById('view-loading'),
  error: document.getElementById('view-error'),
  pair: document.getElementById('view-pair'),
  unlock: document.getElementById('view-unlock'),
  items: document.getElementById('view-items'),
};
const errorMessage = document.getElementById('error-message');
const btnRetry = document.getElementById('btn-retry');
const btnPair = document.getElementById('btn-pair');
const pairActions = document.getElementById('pair-actions');
const btnPairCheck = document.getElementById('btn-pair-check');
const btnPairRetry = document.getElementById('btn-pair-retry');
const pairHint = document.getElementById('pair-hint');
const pairStatus = document.getElementById('pair-status');
const inputPassword = document.getElementById('input-password');
const btnUnlock = document.getElementById('btn-unlock');
const unlockError = document.getElementById('unlock-error');
const btnLock = document.getElementById('btn-lock');
const inputSearch = document.getElementById('input-search');
const itemsContainer = document.getElementById('items-container');

let allItems = [];
let currentHostname = '';

// ---- Daemon communication ----
function daemonRequest(path, body, method = 'POST') {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage({ type: 'DAEMON_REQUEST', path, body, method }, (resp) => {
      if (chrome.runtime.lastError) {
        reject(new Error(chrome.runtime.lastError.message));
        return;
      }
      if (resp?.ok) {
        if (resp.data?.error === 'UNKNOWN_EXTENSION') {
          chrome.storage.local.remove(['extensionId', 'extensionSeed']).then(() => {
            showPairInitial();
          });
          reject(new Error('Extension unpaired. Please pair again.'));
          return;
        }
        resolve(resp.data);
      } else reject(new Error(resp?.error || 'Request failed'));
    });
  });
}

// ---- View management ----
function showView(name) {
  for (const [key, el] of Object.entries(views)) {
    el.classList.toggle('hidden', key !== name);
  }
}

// ---- Init ----
async function init() {
  showView('loading');
  try {
    const info = await daemonRequest('/info', {});
    if (info.error) {
      showError(`Daemon error: ${info.errorMessage || info.error}`);
      return;
    }
    if (!info.data?.initialized) {
      showError('Vault is not initialized. Please set up via the desktop app first.');
      return;
    }
    // Check if extension is paired (caller present in response)
    if (!info.data.caller) {
      const creds = await chrome.storage.local.get(['extensionId', 'extensionSeed']);
      if (creds.extensionId && creds.extensionSeed) {
        // Credentials exist but not yet approved — show waiting state
        showPairWaiting();
      } else {
        showView('pair');
      }
      return;
    }
    if (info.data.locked) {
      showView('unlock');
      inputPassword.focus();
    } else {
      await loadAndShowItems();
    }
  } catch (e) {
    showError(e.message);
  }
}

// ---- Pairing ----
function showPairWaiting() {
  pairHint.textContent = 'Waiting for approval in the desktop app...';
  btnPair.classList.add('hidden');
  pairActions.classList.remove('hidden');
  btnPairCheck.disabled = false;
  btnPairRetry.disabled = false;
  pairStatus.classList.add('hidden');
  showView('pair');
}

function showPairInitial() {
  pairHint.textContent = 'This extension is not paired with the desktop app. Click below to request pairing.';
  btnPair.classList.remove('hidden');
  btnPair.disabled = false;
  btnPair.textContent = 'Pair with App';
  pairActions.classList.add('hidden');
  pairStatus.classList.add('hidden');
  showView('pair');
}

async function getDeviceInfo() {
  const info = await chrome.runtime.getPlatformInfo();
  return info.os + ' ' + info.arch;
}

async function sendPairRequest() {
  try {
    const device = await getDeviceInfo();
    const resp = await daemonRequest('/pair', { name: 'MyPassword Chrome Extension', device });
    if (resp.error) {
      pairStatus.textContent = 'Error: ' + (resp.errorMessage || resp.error);
      pairStatus.classList.remove('hidden');
      return false;
    }
    await chrome.storage.local.set({
      extensionId: resp.data.id,
      extensionSeed: resp.data.seed,
    });
    return true;
  } catch (e) {
    pairStatus.textContent = 'Error: ' + e.message;
    pairStatus.classList.remove('hidden');
    return false;
  }
}

async function doPair() {
  btnPair.disabled = true;
  btnPair.textContent = 'Pairing...';
  pairStatus.classList.add('hidden');
  if (await sendPairRequest()) {
    showPairWaiting();
  } else {
    btnPair.disabled = false;
    btnPair.textContent = 'Pair with App';
  }
}

async function doPairCheck() {
  btnPairCheck.disabled = true;
  btnPairCheck.textContent = 'Checking...';
  pairStatus.classList.add('hidden');
  try {
    const info = await daemonRequest('/info', {});
    if (info.data?.caller) {
      if (info.data.locked) {
        showView('unlock');
        inputPassword.focus();
      } else {
        await loadAndShowItems();
      }
      return;
    }
    pairStatus.textContent = 'Not yet approved. Please approve in the desktop app.';
    pairStatus.classList.remove('hidden');
  } catch (e) {
    pairStatus.textContent = 'Error: ' + e.message;
    pairStatus.classList.remove('hidden');
  } finally {
    btnPairCheck.disabled = false;
    btnPairCheck.textContent = 'Check Status';
  }
}

async function doPairRetry() {
  btnPairRetry.disabled = true;
  btnPairRetry.textContent = 'Retrying...';
  pairStatus.classList.add('hidden');
  if (await sendPairRequest()) {
    pairStatus.textContent = 'New pairing request sent. Please approve in the desktop app.';
    pairStatus.classList.remove('hidden');
  }
  btnPairRetry.disabled = false;
  btnPairRetry.textContent = 'Retry';
}

function showError(msg) {
  errorMessage.textContent = msg;
  showView('error');
}

// ---- Unlock ----
async function doUnlock() {
  const password = inputPassword.value.trim();
  if (!password) {
    unlockError.textContent = 'Password is required.';
    unlockError.classList.remove('hidden');
    return;
  }
  unlockError.classList.add('hidden');
  btnUnlock.disabled = true;
  btnUnlock.textContent = 'Unlocking...';
  try {
    const resp = await daemonRequest('/vault/unlock', { password });
    if (resp.error) {
      unlockError.textContent = `Error: ${resp.errorMessage || resp.error}`;
      unlockError.classList.remove('hidden');
      inputPassword.value = '';
      inputPassword.focus();
    } else {
      inputPassword.value = '';
      await loadAndShowItems();
    }
  } catch (e) {
    unlockError.textContent = e.message;
    unlockError.classList.remove('hidden');
  } finally {
    btnUnlock.disabled = false;
    btnUnlock.textContent = 'Unlock';
  }
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

function getDefaultItems() {
  return allItems.filter((item) => matchesHostname(item, currentHostname));
}

// ---- Items ----
async function loadAndShowItems() {
  showView('loading');
  try {
    // get active tab hostname:
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (tab?.url) {
      try { currentHostname = new URL(tab.url).hostname; } catch {}
    }
    const resp = await daemonRequest('/items/list?type=1', null, 'GET');
    if (resp.error) {
      showError(`Error loading items: ${resp.errorMessage || resp.error}`);
      return;
    }
    allItems = (resp.items || []).filter((i) => !i.deleted);
    renderItems(getDefaultItems());
    showView('items');
    inputSearch.focus();
  } catch (e) {
    showError(e.message);
  }
}

function renderItems(items) {
  itemsContainer.innerHTML = '';

  if (items.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'empty-state';
    empty.textContent = 'No items matched';
    itemsContainer.appendChild(empty);
    return;
  }

  // Sort: website-matched first, then by title, then by username
  const sorted = [...items].sort((a, b) => {
    const am = matchesHostname(a, currentHostname) ? 0 : 1;
    const bm = matchesHostname(b, currentHostname) ? 0 : 1;
    if (am !== bm) return am - bm;
    const tc = (a.data?.title || '').localeCompare(b.data?.title || '');
    if (tc !== 0) return tc;
    return (a.data?.username || '').localeCompare(b.data?.username || '');
  });

  for (const item of sorted) {
    itemsContainer.appendChild(createItemRow(item));
  }
}

function createItemRow(item) {
  const row = document.createElement('div');
  row.className = 'item-row';

  if (item.item_type === ITEM_TYPE_LOGIN) {
    const dot = document.createElement('span');
    dot.className = matchesHostname(item, currentHostname) ? 'match-dot' : 'match-dot unmatched';
    row.appendChild(dot);
  }

  const info = document.createElement('div');
  info.className = 'item-info';

  const title = document.createElement('div');
  title.className = 'item-title';
  title.textContent = item.data?.title || `Item #${item.id}`;

  const subtitle = document.createElement('div');
  subtitle.className = 'item-subtitle';
  subtitle.textContent = item.data?.username || item.data?.name || '';

  info.appendChild(title);
  info.appendChild(subtitle);
  row.appendChild(info);

  // Action buttons (Login items only)
  if (item.item_type === ITEM_TYPE_LOGIN) {
    const actions = document.createElement('div');
    actions.className = 'item-actions';

    const btnUser = makeActionBtn('User', () => copyToClipboard(item.data?.username || '', btnUser));
    const btnPass = makeActionBtn('Pass', () => copyPassword(item.id, btnPass));
    const btnFill = makeActionBtn('Fill', () => doFill(item));

    actions.appendChild(btnUser);
    actions.appendChild(btnPass);
    actions.appendChild(btnFill);
    row.appendChild(actions);
  }

  return row;
}

function makeActionBtn(label, onClick) {
  const btn = document.createElement('button');
  btn.className = 'btn btn-secondary';
  btn.textContent = label;
  btn.addEventListener('click', onClick);
  return btn;
}

function copyPassword(id, btn) {
  daemonRequest(`/items/${id}/copy`, {}).then(() => {
    showCopiedFeedback(btn);
  });
}

function showCopiedFeedback(btn) {
  const original = btn.textContent;
  btn.textContent = '✓';
  btn.classList.add('btn-copied');
  setTimeout(() => {
    btn.textContent = original;
    btn.classList.remove('btn-copied');
  }, 1500);
}

function copyToClipboard(text, btn) {
  navigator.clipboard.writeText(text).then(() => showCopiedFeedback(btn));
}

async function doFill(item) {
  // Send fill to content script via background
  chrome.runtime.sendMessage({ type: 'FILL_CREDENTIALS', item });
  window.close();
}

// ---- Lock ----
async function doLock() {
  try {
    await daemonRequest('/vault/lock', {});
  } catch (_) {
    // best-effort
  }
  allItems = [];
  inputPassword.value = '';
  unlockError.classList.add('hidden');
  showView('unlock');
  inputPassword.focus();
}

// ---- Search ----
function onSearch() {
  const query = inputSearch.value.toLowerCase().trim();
  if (!query) {
    renderItems(getDefaultItems());
    return;
  }
  const filtered = allItems.filter((item) => {
    const title = (item.data?.title || '').toLowerCase();
    const username = (item.data?.username || '').toLowerCase();
    const websites = (item.data?.websites || []).join(' ').toLowerCase();
    return title.includes(query) || username.includes(query) || websites.includes(query);
  });
  renderItems(filtered);
}

// ---- Event listeners ----
btnRetry.addEventListener('click', init);
btnPair.addEventListener('click', doPair);
btnPairCheck.addEventListener('click', doPairCheck);
btnPairRetry.addEventListener('click', doPairRetry);
btnUnlock.addEventListener('click', doUnlock);
inputPassword.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') doUnlock();
});
btnLock.addEventListener('click', doLock);
inputSearch.addEventListener('input', onSearch);

// ---- Start ----
init();
