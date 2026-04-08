// Background service worker
// Forwards DAEMON_REQUEST messages to the desktop app HTTP server via fetch.

const BASE_URL = 'http://127.0.0.1:27432';

async function daemonRequest(path, body, method = 'POST') {
  const options = { method };
  if (method === 'POST') {
    options.headers = { 'Content-Type': 'application/json' };
    options.body = JSON.stringify(body);
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
