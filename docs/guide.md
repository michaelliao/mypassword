# MyPassword User Guide

MyPassword is an offline, open-source password manager. All data is stored locally on your device, encrypted with AES-256-GCM. It includes a desktop application and a Chrome browser extension.

## Getting Started

### First Launch

On first launch, MyPassword shows the **Locate Vault** dialog so you can choose where your vault file lives:

- **Open existing vault…** — point MyPassword at an existing `mypassword.db` file (useful when restoring a backup or switching machines)
- **Create new vault…** — pick a folder in which a fresh `mypassword.db` will be created. You can choose any folder, including one inside a cloud-synced directory (e.g. OneDrive, Google Drive, Dropbox) so the vault syncs automatically across devices

MyPassword records your choice in a small pointer file at `~/.mypassword/vault.path` and reads it on every subsequent launch, so you only go through this dialog once. See [backup](backup) for more on vault location and cloud sync.

After locating the vault, you are asked to create a **master password**. This is the only password you need to remember — it protects all your vault data.

- Must be 8-50 characters
- Choose something strong and unique
- If you forget it, you can only recover via OAuth (if configured)

After creation, the vault is populated with sample items to help you get familiar with the app.

### Unlocking the Vault

Each time you open MyPassword, enter your master password to unlock. If OAuth recovery is configured, you can also unlock via Google or Microsoft login.

## Item Types

MyPassword stores three types of items:

### Logins

Store website credentials:

- **Title** — a label (e.g. "GitHub")
- **Username** — your login name or email
- **Password** — your password (stored encrypted)
- **Websites** — one or more URLs for matching in the browser extension
- **Memo** — optional notes

Logins also support a built-in **password generator** (see below).

### Notes

Store free-form text securely:

- **Title** — a label (e.g. "WiFi Password")
- **Content** — the note body

### Identities

Store personal identification information:

- **Name**
- **Passport Number**
- **Identity Number**
- **Tax Number**
- **Mobiles** — one or more mobile numbers
- **Telephones** — one or more phone numbers

## Categories

The sidebar organizes items into categories:

| Category | Description |
|---|---|
| All | All non-deleted items |
| Favorites | Items you have starred |
| Logins | Login items only |
| Notes | Note items only |
| Identities | Identity items only |
| Trash | Deleted items (can be restored) |

## Managing Items

- **Add** — click the add button and choose an item type
- **Edit** — select an item and click the edit button
- **Favorite** — star/unstar an item to add or remove it from Favorites
- **Delete** — moves the item to Trash (soft delete)
- **Restore** — from Trash, restore a deleted item
- **Permanent delete** — items in Trash can be permanently removed

## Password Features

### Copy Password

In the login detail view, click **Copy** to copy the password to the clipboard. The clipboard is automatically cleared after the configured timeout (default: 1 minute).

### Show / Hide Password

Click the **dropdown arrow** next to the Copy button for options:

- **Show** / **Hide** — toggle password visibility inline
- **Show Large** — display the password in a large popup window (useful for reading complex passwords)

If a login item has no password, the Copy and dropdown buttons are hidden.

### Password Generator

When creating or editing a login item, expand the password generator to create a strong password:

- **Length** — adjust with a slider (8-32 characters)
- **Style options:**
  - Word + Number (alphanumeric)
  - Word Only (letters only)
  - Number Only (digits only)
  - Contains Symbol (letters, numbers, and symbols like `?!@#$%&*()-_+=[]{}<>:;,.`)

Click **Generate** to fill the password field.

## Passkey Features

A **passkey** is a phishing-resistant credential based on public-key cryptography (WebAuthn / FIDO2). MyPassword can act as your authenticator: when you register a passkey on a website, the private key is stored inside the matching login item and encrypted with your master password like any other vault data.

### Enabling Passkey Handling

Passkey handling is off by default. To turn it on:

1. Click the MyPassword extension icon in Chrome
2. Toggle **Handle passkeys with MyPassword** on

When enabled, the extension attaches to Chrome's `webAuthenticationProxy` API (Chrome 115+) and routes all passkey requests in the current profile to the running desktop app. While disabled, Chrome's built-in authenticator picker is used instead.

The desktop app must be running and unlocked for passkey registration and sign-in to work.

### Registering a Passkey

On a site that supports passkeys, click the site's "Create a passkey" (or similar) button. The extension shows a panel listing your existing logins for that site:

- Pick a login to attach the new passkey to, or
- Create a new login on the spot

Once you confirm, MyPassword generates an ES256 key pair, stores the private key in the chosen login item, and returns the public key to the site.

### Signing In with a Passkey

When a site initiates a passkey sign-in, the extension shows a picker listing the passkeys that match the site's relying party ID (and any `allowCredentials` filter the site provides). Click the entry you want to use — MyPassword signs the challenge with the stored private key and returns the assertion to the site.

### Viewing and Deleting Passkeys

Login items that contain a passkey show a **Passkey** field in the detail view. Click **Delete passkey** to remove it from the item; the rest of the login (username, password, websites, memo) is preserved. Deleting a passkey cannot be undone — the site will no longer accept sign-ins from that credential.

## Chrome Extension

The Chrome extension connects to the running desktop app at `http://127.0.0.1:27432`. The desktop app must be running and unlocked for the extension to work.

### Installation

1. Open Chrome and go to `chrome://extensions/`
2. Enable **Developer mode**
3. Click **Load unpacked** and select the `extension/chrome/` folder

### Auto-Fill

When you focus on a login form, the extension automatically detects username and password fields. If matching logins exist for the current website, a dropdown appears with available credentials. Click one to fill the form.

The extension handles:

- Standard login forms
- Multi-page logins (username on one page, password on the next)
- Single-page applications (SPAs)

### Save & Update

When you submit a login form, the extension detects the entered credentials and offers to:

- **Save** — if the login is new
- **Update** — if a matching login already exists

### Popup

Click the extension icon to open the popup:

- **Search** — find logins by title, username, or website
- **Copy** — copy username or password directly
- **Lock** — lock the vault from the browser
- **Unlock** — enter master password to unlock without switching to the desktop app

The extension badge shows the number of matching logins for the current site.

## Settings

Open settings from the system tray menu or within the app.

### General

- **Keep tray icon** — when enabled (default), closing the window minimizes to the system tray instead of exiting
- **Language** — System default, English, or Chinese

### Security

- **Auto-lock** — lock the vault after a period of system inactivity (keyboard/mouse). Options: 1, 2, 5, 10, 15, 30, 60 minutes, or Never. Default: 10 minutes. Uses OS-level idle detection, not just app focus.
- **Clear clipboard** — automatically clear copied passwords from the clipboard. Options: 1, 2, 5 minutes, or Never. Default: 1 minute.

### Password

- **Change master password** — requires entering the current password
- **OAuth recovery** — connect a Google or Microsoft account for vault recovery. See [OAuth Recovery](#oauth-recovery) below.

## OAuth Recovery

If you forget your master password, OAuth recovery lets you unlock the vault using your Google or Microsoft account.

### Setup

1. Go to **Settings > Password**
2. Click **Login with Google** or **Login with Microsoft**
3. Complete the OAuth login in your browser
4. Once connected, the account is displayed in settings

You can connect multiple providers. You can also disconnect a provider at any time.

### Recovery

1. On the unlock screen, click the Google or Microsoft recovery button
2. Authenticate in your browser
3. The vault unlocks and you are prompted to set a new master password

For technical details, see [key-gen.md](key-gen.md).

## System Tray

MyPassword runs in the system tray with the following menu:

- **Open MyPassword** — show the main window
- **Lock MyPassword** — lock the vault immediately
- **Settings** — open the settings dialog
- **Exit** — close the application

Single-click the tray icon to restore the window. Right-click for the menu.

## Backup

The vault is a single file at `~/.mypassword/mypassword.db`. It is fully encrypted and safe to back up to cloud storage. See [backup.md](backup.md) for detailed instructions.

## Security Overview

- All vault data is encrypted with **AES-256-GCM**
- The encryption key (DEK) is protected by your master password via **PBKDF2** with 1,000,000 iterations
- The DEK is held in memory only while unlocked and zeroed on lock
- The Chrome extension communicates only over `127.0.0.1` (localhost) — no data leaves your machine
- No network calls are made by the desktop app except during OAuth flows

For the full encryption architecture, see [key-gen.md](key-gen.md).
