// Background service worker
// Forwards DAEMON_REQUEST messages to the desktop app HTTP server via fetch.

const BASE_URL = 'http://127.0.0.1:27432';

// Expose chrome.storage.session to content scripts (required since MV3).
// Without this, storage.session.get/set from content.js silently fails.
chrome.storage.session.setAccessLevel({
  accessLevel: 'TRUSTED_AND_UNTRUSTED_CONTEXTS',
}).catch(() => {});

async function getExtensionCredentials() {
  const result = await chrome.storage.local.get(['extensionId', 'extensionSeed']);
  if (result.extensionId && result.extensionSeed) {
    return { id: result.extensionId, seed: result.extensionSeed };
  }
  return null;
}

async function computeSignature(timestamp, seed) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw', encoder.encode(seed), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(timestamp));
  return Array.from(new Uint8Array(sig)).map(b => b.toString(16).padStart(2, '0')).join('');
}

async function daemonRequest(path, body, method = 'POST') {
  const options = { method, headers: {} };
  if (method === 'POST') {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  // Add extension auth headers if paired (skip for /pair itself)
  if (path !== '/pair') {
    const creds = await getExtensionCredentials();
    if (creds) {
      const ts = String(Date.now());
      options.headers['X-Extension-Id'] = String(creds.id);
      options.headers['X-Extension-Timestamp'] = ts;
      options.headers['X-Extension-Signature'] = await computeSignature(ts, creds.seed);
    }
  }
  const resp = await fetch(BASE_URL + path, options);
  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`);
  }
  return resp.json();
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'DAEMON_REQUEST') {
    daemonRequest(msg.path, msg.body, msg.method || 'POST')
      .then((data) => sendResponse({ ok: true, data }))
      .catch((err) => sendResponse({ ok: false, error: err.message }));
    return true; // keep channel open for async response
  }

  if (msg.type === 'SET_BADGE') {
    const tabId = sender.tab?.id;
    if (tabId != null) {
      const text = msg.count > 0 ? String(msg.count) : '';
      chrome.action.setBadgeText({ text, tabId });
      chrome.action.setBadgeBackgroundColor({ color: '#4a90e2', tabId });
    }
    return false;
  }

  if (msg.type === 'FILL_CREDENTIALS') {
    // fetch full item (with password) before sending to content script
    daemonRequest(`/items/${msg.item.id}/get`, null, 'GET')
      .then((data) => {
        chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
          if (tabs[0]?.id) {
            chrome.tabs.sendMessage(tabs[0].id, { type: 'DO_FILL', item: data.item });
          }
        });
      })
      .catch(() => {});
    return false;
  }
});
