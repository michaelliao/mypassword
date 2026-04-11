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

// ---- WebAuthn proxy ----
// When the `proxy_passkey` setting in chrome.storage.local is true, we attach
// as Chrome's WebAuthn provider — every navigator.credentials.create/.get call
// in this profile is then routed to onCreateRequest/onGetRequest below.
// While unattached, Chrome's normal authenticator picker is used as before.
//
// Smoke-test handlers reject every request with NotAllowedError. The real
// handling will live behind /passkey/create on the daemon.

async function syncWebAuthnProxy() {
  if (!chrome.webAuthenticationProxy) {
    console.warn('webAuthenticationProxy API not available — Chrome 115+ required');
    return;
  }
  const { proxy_passkey } = await chrome.storage.local.get(['proxy_passkey']);
  if (proxy_passkey) {
    try {
      await chrome.webAuthenticationProxy.attach();
      console.log('webAuthenticationProxy: attached');
    } catch (e) {
      console.warn('webAuthenticationProxy.attach failed:', e);
    }
  } else {
    try {
      await chrome.webAuthenticationProxy.detach();
      console.log('webAuthenticationProxy: detached');
    } catch (_) {
      // not attached — fine
    }
  }
}

chrome.runtime.onInstalled.addListener(syncWebAuthnProxy);
chrome.runtime.onStartup.addListener(syncWebAuthnProxy);

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === 'local' && 'proxy_passkey' in changes) {
    syncWebAuthnProxy();
  }
});

if (chrome.webAuthenticationProxy) {
  chrome.webAuthenticationProxy.onCreateRequest.addListener(async (req) => {
    console.log('webAuthenticationProxy.onCreateRequest', req);
    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (!tab || tab.id == null) {
        throw new Error('No active tab');
      }
      // Remember which tab is handling this request so we can cancel on tab close
      await chrome.storage.session.set({
        passkeyCreateRequest: {
          requestId: req.requestId,
          requestDetailsJson: req.requestDetailsJson,
          tabId: tab.id,
        },
      });
      // Ask the content script to show the in-page panel
      try {
        await chrome.tabs.sendMessage(tab.id, {
          type: 'SHOW_PASSKEY_PROMPT',
          request: {
            requestId: req.requestId,
            requestDetailsJson: req.requestDetailsJson,
          },
        });
      } catch (e) {
        throw new Error('Cannot show prompt on this page: ' + e.message);
      }
    } catch (e) {
      console.warn('Failed to show passkey prompt:', e);
      chrome.webAuthenticationProxy.completeCreateRequest({
        requestId: req.requestId,
        error: {
          name: 'NotAllowedError',
          message: 'MyPassword: ' + e.message,
        },
      });
      await chrome.storage.session.remove(['passkeyCreateRequest']);
    }
  });

  chrome.webAuthenticationProxy.onGetRequest.addListener((req) => {
    console.log('webAuthenticationProxy.onGetRequest', req);
    chrome.webAuthenticationProxy.completeGetRequest({
      requestId: req.requestId,
      error: {
        name: 'NotAllowedError',
        message: 'MyPassword passkey sign-in is not implemented yet.',
      },
    });
  });

  chrome.webAuthenticationProxy.onRequestCanceled.addListener(async (requestId) => {
    console.log('webAuthenticationProxy.onRequestCanceled', requestId);
    const stored = await chrome.storage.session.get(['passkeyCreateRequest']);
    if (stored.passkeyCreateRequest?.requestId === requestId) {
      // Tell the content script to close its panel if still open
      if (stored.passkeyCreateRequest.tabId != null) {
        chrome.tabs.sendMessage(stored.passkeyCreateRequest.tabId, {
          type: 'HIDE_PASSKEY_PROMPT',
        }).catch(() => {});
      }
      await chrome.storage.session.remove(['passkeyCreateRequest']);
    }
  });
}

// If the tab hosting the passkey request is closed, treat as cancel.
chrome.tabs.onRemoved.addListener(async (tabId) => {
  const stored = await chrome.storage.session.get(['passkeyCreateRequest']);
  if (stored.passkeyCreateRequest?.tabId === tabId) {
    try {
      chrome.webAuthenticationProxy.completeCreateRequest({
        requestId: stored.passkeyCreateRequest.requestId,
        error: { name: 'NotAllowedError', message: 'Tab closed.' },
      });
    } catch (_) {}
    await chrome.storage.session.remove(['passkeyCreateRequest']);
  }
});

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

  if (msg.type === 'PASSKEY_CREATE_RESULT') {
    (async () => {
      const stored = await chrome.storage.session.get(['passkeyCreateRequest', 'passkeyWindowId']);
      const reqId = stored.passkeyCreateRequest?.requestId;
      if (reqId == null) {
        console.warn('PASSKEY_CREATE_RESULT: no pending passkey request');
        sendResponse({ ok: false, error: 'No pending passkey request' });
        return;
      }
      // completeCreateRequest returns a Promise in MV3 — we MUST await it so
      // rejected JSON parses / bad shapes surface as caught errors instead of
      // leaving the browser's WebAuthn promise hanging forever.
      try {
        if (msg.ok) {
          console.log('completeCreateRequest(success) responseJson:', msg.responseJson);
          await chrome.webAuthenticationProxy.completeCreateRequest({
            requestId: reqId,
            responseJson: msg.responseJson,
          });
          console.log('completeCreateRequest: success');
        } else {
          await chrome.webAuthenticationProxy.completeCreateRequest({
            requestId: reqId,
            error: {
              name: msg.errorName || 'NotAllowedError',
              message: msg.errorMessage || 'Cancelled',
            },
          });
          console.log('completeCreateRequest: error delivered');
        }
      } catch (e) {
        console.error('completeCreateRequest failed:', e);
        // Last-ditch: tell the browser the ceremony failed so it doesn't hang.
        try {
          await chrome.webAuthenticationProxy.completeCreateRequest({
            requestId: reqId,
            error: { name: 'NotAllowedError', message: 'MyPassword: ' + (e && e.message || e) },
          });
        } catch (_) {}
      }
      await chrome.storage.session.remove(['passkeyCreateRequest', 'passkeyWindowId']);
      if (stored.passkeyWindowId != null) {
        chrome.windows.remove(stored.passkeyWindowId).catch(() => {});
      }
      sendResponse({ ok: true });
    })();
    return true;
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
