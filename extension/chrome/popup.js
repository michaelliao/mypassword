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
  scan: document.getElementById('view-scan'),
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
const btnSettings = document.getElementById('btn-settings');
const settingsSection = document.getElementById('settings-section');
const inputSearch = document.getElementById('input-search');
const itemsContainer = document.getElementById('items-container');
const toggleProxyPasskey = document.getElementById('toggle-proxy-passkey');

const btnScan = document.getElementById('btn-scan');
const btnScanBack = document.getElementById('btn-scan-back');
const btnScanOk = document.getElementById('btn-scan-ok');
const scanLoading = document.getElementById('scan-loading');
const scanNotFound = document.getElementById('scan-not-found');
const scanFound = document.getElementById('scan-found');
const scanTotpInfo = document.getElementById('scan-totp-info');
const scanItemsContainer = document.getElementById('scan-items-container');

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
    const matched = getDefaultItems();
    renderItems(matched);
    btnScan.classList.toggle('hidden', matched.length === 0);
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

    if (item.data?.totp) {
      const btn2fa = makeActionBtn('2FA', () => copyTotpCode(item.id, btn2fa));
      actions.appendChild(btn2fa);
    }
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

function copyTotpCode(itemId, btn) {
  daemonRequest('/totps/get', { itemId }).then((resp) => {
    if (resp.data) {
      navigator.clipboard.writeText(resp.data).then(() => showCopiedFeedback(btn));
    }
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

// ---- Settings section ----
btnSettings.addEventListener('click', () => {
  settingsSection.classList.toggle('hidden');
});

chrome.storage.local.get(['proxy_passkey']).then(({ proxy_passkey }) => {
  toggleProxyPasskey.checked = !!proxy_passkey;
});
toggleProxyPasskey.addEventListener('change', () => {
  chrome.storage.local.set({ proxy_passkey: toggleProxyPasskey.checked });
});

// ---- QR Scan ----
async function doScan() {
  showView('scan');
  scanLoading.classList.remove('hidden');
  scanNotFound.classList.add('hidden');
  scanFound.classList.add('hidden');

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab?.id) {
      showScanNotFound();
      return;
    }
    // Inject jsQR library first, then run our scan function
    await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      files: ['lib/jsQR.js'],
    });
    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: scanPageForOtpAuth,
    });
    const scanResult = results?.[0]?.result;
    if (!scanResult?.uri) {
      showScanNotFound(scanResult?.logs || []);
    } else {
      showScanFound(scanResult.uri);
    }
  } catch (e) {
    showScanNotFound([`Scan error: ${e.message}`]);
  }
}

function showScanNotFound(logs = []) {
  scanLoading.classList.add('hidden');
  scanNotFound.classList.remove('hidden');
  // Show scan logs for debugging
  let logEl = document.getElementById('scan-logs');
  if (!logEl) {
    logEl = document.createElement('div');
    logEl.id = 'scan-logs';
    logEl.style.cssText = 'font-size:11px;color:#888;margin-top:10px;max-height:200px;overflow-y:auto;word-break:break-all;white-space:pre-wrap;';
    scanNotFound.appendChild(logEl);
  }
  logEl.textContent = logs.length ? logs.join('\n') : '';
}

function showScanFound(uri) {
  scanLoading.classList.add('hidden');
  scanFound.classList.remove('hidden');

  // Parse URI for display
  let display = uri;
  try {
    const u = new URL(uri);
    const issuer = u.searchParams.get('issuer') || '';
    const label = decodeURIComponent(u.pathname.replace(/^\/+/, ''));
    display = issuer ? `${issuer} (${label})` : label;
  } catch {}
  scanTotpInfo.textContent = display;

  // Show matched logins for selection
  const matched = getDefaultItems();
  scanItemsContainer.innerHTML = '';
  for (const item of matched) {
    const row = document.createElement('div');
    row.className = 'scan-item-row';

    const info = document.createElement('div');
    info.className = 'item-info';
    const title = document.createElement('div');
    title.className = 'item-title';
    title.textContent = item.data?.title || `Item #${item.id}`;
    const subtitle = document.createElement('div');
    subtitle.className = 'item-subtitle';
    subtitle.textContent = item.data?.username || '';
    info.appendChild(title);
    info.appendChild(subtitle);
    row.appendChild(info);

    const btn = document.createElement('button');
    btn.className = 'btn btn-primary';
    btn.textContent = 'Save';
    btn.addEventListener('click', () => saveTotpToItem(item.id, uri, btn));
    row.appendChild(btn);

    scanItemsContainer.appendChild(row);
  }
}

async function saveTotpToItem(itemId, uri, btn) {
  btn.disabled = true;
  btn.textContent = 'Saving...';
  try {
    const resp = await daemonRequest('/totps/add', { itemId, uri });
    if (resp.error) {
      btn.textContent = resp.errorMessage || resp.error;
      btn.classList.add('btn-secondary');
      btn.classList.remove('btn-primary');
    } else {
      btn.textContent = 'Saved';
      btn.classList.remove('btn-primary');
      btn.classList.add('btn-copied');
    }
  } catch (e) {
    btn.textContent = 'Error';
  }
}

/**
 * Injected into the active tab to scan for otpauth:// URIs.
 * Uses jsQR (injected beforehand) to decode QR codes from
 * <canvas>, <img>, and <svg> elements.
 */
function scanPageForOtpAuth() {
  const logs = [];

  if (typeof jsQR === 'undefined') {
    logs.push('jsQR library not available');
    return { uri: null, logs };
  }

  function decodeFromCanvas(canvas, label) {
    try {
      const ctx = canvas.getContext('2d');
      if (!ctx) { logs.push(`  ${label} no 2d context`); return null; }
      const w = canvas.width, h = canvas.height;
      if (w < 20 || h < 20) { logs.push(`  ${label} too small`); return null; }
      const imageData = ctx.getImageData(0, 0, w, h);
      const result = jsQR(imageData.data, w, h);
      if (result) {
        logs.push(`  ${label} decoded: ${result.data.substring(0, 80)}`);
        return result.data;
      }
      logs.push(`  ${label} no QR found`);
    } catch (e) {
      logs.push(`  ${label} error: ${e.message}`);
    }
    return null;
  }

  function drawToCanvasAndDecode(imgSource, w, h, label) {
    try {
      const canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(imgSource, 0, 0, w, h);
      return decodeFromCanvas(canvas, label);
    } catch (e) {
      logs.push(`  ${label} error: ${e.message}`);
      return null;
    }
  }

  // Scan <canvas> elements
  const canvases = document.querySelectorAll('canvas');
  logs.push(`Found ${canvases.length} <canvas> element(s)`);
  for (const canvas of canvases) {
    logs.push(`  canvas: ${canvas.width}x${canvas.height}, id="${canvas.id}"`);
    const data = decodeFromCanvas(canvas, 'canvas');
    if (data?.startsWith('otpauth://')) return { uri: data, logs };
  }

  // Scan <img> elements
  const imgs = document.querySelectorAll('img');
  logs.push(`Found ${imgs.length} <img> element(s)`);
  for (const img of imgs) {
    if (!img.complete) continue;
    const rect = img.getBoundingClientRect();
    const srcPreview = img.src ? img.src.substring(0, 80) : '(empty)';
    let w = img.naturalWidth || Math.round(rect.width);
    let h = img.naturalHeight || Math.round(rect.height);
    logs.push(`  img: ${w}x${h}, rendered=${Math.round(rect.width)}x${Math.round(rect.height)}, src=${srcPreview}`);
    if (w < 20 || h < 20) continue;
    // For SVG data URIs, render at a larger size for reliable decoding
    if (img.src && img.src.startsWith('data:image/svg')) {
      w = Math.max(w, 300);
      h = Math.max(h, 300);
    }
    const data = drawToCanvasAndDecode(img, w, h, 'img');
    if (data?.startsWith('otpauth://')) return { uri: data, logs };
  }

  // Scan inline <svg> elements
  const svgs = document.querySelectorAll('svg');
  logs.push(`Found ${svgs.length} <svg> element(s)`);
  for (const svg of svgs) {
    const rect = svg.getBoundingClientRect();
    if (rect.width < 20 || rect.height < 20) continue;
    logs.push(`  svg: ${Math.round(rect.width)}x${Math.round(rect.height)}, id="${svg.id}"`);
  }

  return { uri: null, logs };
}

btnScan.addEventListener('click', doScan);
btnScanBack.addEventListener('click', () => showView('items'));
btnScanOk.addEventListener('click', () => showView('items'));

// ---- Start ----
init();
