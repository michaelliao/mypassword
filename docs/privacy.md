# Privacy Policy

_Last updated: 2026-04-14_

MyPassword is a local-first password manager. This policy describes what the MyPassword desktop application and its companion Chrome extension do with your data.

## Summary

- Your vault (logins, notes, identities, passkeys) is stored **locally on your own computer**, encrypted with a key derived from your master password.
- **No data is sent to the developer or to any third-party server.**
- The Chrome extension communicates only with the MyPassword desktop app running on your own machine over the loopback address `http://127.0.0.1:27432`. It does not make requests to any remote server.
- There are no analytics, no telemetry, no advertising SDKs, and no account or sign-up.

## Data We Collect

**We do not collect any data.** The developer operates no backend service and has no way to read your vault, your browsing activity, or your usage patterns.

## Data Stored Locally by the Desktop App

The desktop application stores the following on your computer:

- Your encrypted vault file, containing the items you have chosen to save (login credentials, secure notes, identities, passkeys).
- Application configuration (language, auto-lock timeout, etc.).

The vault is encrypted with a data encryption key (DEK) that is itself protected by a key derived from your master password. The master password is never written to disk. The DEK is held in memory only while the vault is unlocked and is cleared automatically after 10 minutes of inactivity (auto-lock).

## Data Stored Locally by the Chrome Extension

The extension uses `chrome.storage` to hold:

- A pairing identifier and HMAC seed used to authenticate the extension to the desktop app.
- User preferences, such as whether to act as a WebAuthn (passkey) provider.
- Short-lived per-request state during a passkey ceremony (the in-flight request ID and originating tab), which is cleared as soon as the ceremony completes or is cancelled.

This data never leaves your computer.

## How the Extension Uses Browser Permissions

- **`activeTab`** — When you click the toolbar icon, the extension temporarily accesses the current tab to fill the credential you select.
- **`tabs`** — Used to read the active tab's URL so the extension can look up matching saved logins, to route passkey prompts to the correct tab, and to cancel a pending passkey ceremony if its tab is closed.
- **`storage`** — Holds the pairing credentials, user settings, and short-lived passkey request state described above.
- **Content script / scripting** — Injected into pages to detect login and signup forms, display the in-page passkey prompt, and fill credentials you explicitly choose.
- **Host permission `http://127.0.0.1:27432/*`** — The sole channel used to communicate with the MyPassword desktop application on your own computer. No remote hosts are contacted.

## What We Do Not Do

- We do not transmit your vault, your master password, your passkeys, or any derived key material off of your computer.
- We do not track your browsing activity.
- We do not sell, share, or disclose any user data, because we do not have any.
- We do not use remote code. All extension code is contained in the published package.

## Network Activity

- **Desktop app:** Binds an HTTP server to the loopback interface (`127.0.0.1:27432`) so the extension running on the same machine can talk to it. This server is not reachable from other computers.
- **Extension:** Makes `fetch` requests only to `http://127.0.0.1:27432`. The extension does not contact any remote server.

If you sign into an account provided by a website (for example, an OAuth provider), that network traffic is between your browser and that website and is outside the scope of MyPassword.

## Third Parties

MyPassword does not integrate with any third-party analytics, advertising, crash-reporting, or cloud-storage service.

## Children

MyPassword is a general-purpose utility and is not directed at children under 13.

## Changes to This Policy

If this policy changes, the updated version will be published in the project repository with a new "Last updated" date.

## Contact

Questions about this policy can be filed as an issue in the project's source repository.
