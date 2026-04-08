# Vault Backup Guide

## What to Back Up

All vault data is stored in a single SQLite file:

```
~/.mypassword/mypassword.db
```

Platform-specific paths:

| OS | Path |
|---|---|
| Windows | `C:\Users\<username>\.mypassword\mypassword.db` |
| macOS | `/Users/<username>/.mypassword/mypassword.db` |
| Linux | `/home/<username>/.mypassword/mypassword.db` |

This file contains everything: encrypted items (logins, notes, identities), vault configuration (encrypted DEK, salt, IV), recovery configuration, and settings. All sensitive data within the file is encrypted with AES-256-GCM — the file itself is safe to copy and store.

## Is It Safe to Upload?

Yes. The database file does not contain any plaintext secrets. Vault items are encrypted with the DEK, and the DEK is wrapped by your master password (and optionally by OAuth recovery). Without the master password or a successful OAuth recovery, the data cannot be decrypted.

That said, treat the backup as sensitive — see [Security Considerations](#security-considerations) below.

## How to Back Up

### Manual Copy

1. **Close MyPassword** (or lock the vault) to ensure no writes are in progress
2. Copy `mypassword.db` to your backup location

### Upload to Cloud Drive

You can upload the database file to any cloud storage provider:

- **Google Drive** — upload to a private folder
- **OneDrive** — upload to the Personal Vault for extra protection
- **iCloud Drive** — upload via Finder or iCloud folder
- **Dropbox** — upload to a private folder

**Steps:**

1. Lock or close MyPassword
2. Navigate to `~/.mypassword/`
3. Copy `mypassword.db` to your cloud drive folder (or upload via the web interface)
4. Optionally rename the file with a date, e.g. `mypassword-2026-04-08.db`

### Automated Sync

You can place the `~/.mypassword/` directory inside a cloud-synced folder, or create a symbolic link:

**Windows (PowerShell, run as Administrator):**
```powershell
# Move the data directory into OneDrive and create a symlink
Move-Item "$env:USERPROFILE\.mypassword" "$env:USERPROFILE\OneDrive\.mypassword"
New-Item -ItemType SymbolicLink -Path "$env:USERPROFILE\.mypassword" -Target "$env:USERPROFILE\OneDrive\.mypassword"
```

**macOS / Linux:**
```bash
# Move the data directory into a cloud-synced folder and create a symlink
mv ~/.mypassword ~/GoogleDrive/.mypassword
ln -s ~/GoogleDrive/.mypassword ~/.mypassword
```

This keeps the vault automatically synced. However, be aware of potential conflicts if MyPassword is running on multiple machines simultaneously — SQLite does not support concurrent writes from different processes over network file systems.

## How to Restore

1. Close MyPassword if it is running
2. Copy the backup file to `~/.mypassword/mypassword.db`, replacing the existing file
3. Open MyPassword and unlock with your master password (or OAuth recovery)

## Security Considerations

- **The file is encrypted, but not invulnerable.** An attacker with the backup file can attempt offline brute-force attacks against your master password with no rate limiting. Use a strong master password. See [key-gen.md](key-gen.md#when-can-the-vault-be-compromised) for details.
- **Keep backup copies limited.** Every copy is another target. Delete old backups you no longer need.
- **Enable MFA on your cloud account.** If your cloud account is compromised, the attacker gets the vault file. The vault is still encrypted, but they can begin offline attacks.
- **Do not share the file.** Even though it is encrypted, sharing it exposes it to unnecessary risk.
