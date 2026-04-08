# MyPassword

MyPassword is a free, open-source, offline password manager. All data is encrypted with AES-256-GCM and stored locally — nothing is sent to the cloud.

## Key Features

- **Offline & local** — your vault never leaves your device
- **AES-256-GCM encryption** — military-grade encryption with PBKDF2 key derivation (1M iterations)
- **Three item types** — Logins, Notes, and Identities
- **Chrome extension** — auto-fill, auto-save, and search from the browser
- **Password generator** — configurable length and character styles
- **OAuth recovery** — recover your vault with Google or Microsoft if you forget your master password
- **Auto-lock** — locks automatically after system idle (keyboard/mouse inactivity)
- **Clipboard protection** — copied passwords are cleared automatically
- **Cross-platform** — runs on Windows, macOS, and Linux
- **Multi-language** — English and Chinese

## How It Works

```
Master Password ──> PBKDF2 ──> Wrapping Key ──> Decrypts DEK
                                                      |
                                                AES-256-GCM
                                                      |
                                                 Vault Data
```

Your master password derives a key that unwraps the Data Encryption Key (DEK). The DEK encrypts all vault items. Optionally, a second copy of the DEK is protected by your OAuth identity for recovery.

## Documentation

- [User Guide](guide.md) — features, usage, Chrome extension, settings
- [Key Generation & Encryption](key-gen.md) — AES key generation, PBKDF2, OAuth recovery internals
- [Backup Guide](backup.md) — how to back up and restore your vault

## Quick Start

1. Download the latest release for your platform
2. Run `MyPassword.exe` (Windows), `MyPassword.app` (macOS), or `MyPassword` (Linux)
3. Create a master password on first launch
4. Start adding logins, notes, and identities
5. Install the Chrome extension from `extension/chrome/` for browser auto-fill

## Requirements

- Java 25 or later (bundled in the release package)
- Chrome browser (for the extension)

## Build from Source

```bash
cd desktop
mvn clean package
```

The app image is output to `desktop/target/dist/`.
