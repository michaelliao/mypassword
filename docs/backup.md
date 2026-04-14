# Vault Backup Guide

## What to Back Up

All vault data is stored in a single SQLite file named `mypassword.db`. It contains everything: encrypted items (logins, notes, identities), vault configuration (encrypted DEK, salt, IV), recovery configuration, and settings. All sensitive data within the file is encrypted with AES-256-GCM — the file itself is safe to copy and store.

## Where Is My Vault?

MyPassword stores a small pointer file at `~/.mypassword/vault.path` containing the absolute path of the real database file. On startup the app reads this pointer and opens whatever it points at. If the pointer file is missing, the **Locate Vault** dialog is shown so you can pick an existing vault file or create a new one anywhere on disk.

| Default location | Path Pointer |
|---|---|
| Windows | `C:\Users\<username>\.mypassword\vault.path` |
| macOS | `/Users/<username>/.mypassword/vault.path` |
| Linux | `/home/<username>/.mypassword/vault.path` |

To find your real vault file at any time, open `~/.mypassword/vault.path` in a text editor — it is a plain-text file with a single absolute path.

If you let MyPassword create a vault in a custom location via the **Locate Vault** dialog, your vault file lives at whatever path you chose (e.g. inside a OneDrive folder) and the pointer file records that path.

## Is It Safe to Upload?

Yes. The database file does not contain any plaintext secrets. Vault items are encrypted with the DEK, and the DEK is wrapped by your master password (and optionally by OAuth recovery). Without the master password or a successful OAuth recovery, the data cannot be decrypted.

That said, treat the backup as sensitive — see [Security Considerations](#security-considerations) below.

## How to Back Up

### Manual Copy

1. **Close MyPassword** (or lock the vault) to ensure no writes are in progress
2. Locate your vault file (see [Where Is My Vault?](#where-is-my-vault))
3. Copy `mypassword.db` to your backup location

### Upload to Cloud Drive

You can upload the database file to any cloud storage provider:

- **Google Drive** — upload to a private folder
- **OneDrive** — upload to the Personal Vault for extra protection
- **iCloud Drive** — upload via Finder or iCloud folder
- **Dropbox** — upload to a private folder

**Steps:**

1. Lock or close MyPassword
2. Locate your vault file (see [Where Is My Vault?](#where-is-my-vault))
3. Copy `mypassword.db` to your cloud drive folder (or upload via the web interface)
4. Optionally rename the file with a date, e.g. `mypassword-2026-04-08.db`

### Automated Sync

The easiest way to keep the vault continuously backed up is to store it directly inside a cloud-synced folder. MyPassword supports this out of the box — no symlinks or platform-specific commands needed.

**First-time setup:**

1. Quit MyPassword
2. Delete `~/.mypassword/vault.path` (and `~/.mypassword/mypassword.db` if you haven't started using the vault yet)
3. Launch MyPassword — the **Locate Vault** dialog appears
4. Click **Create new vault…** and pick a folder inside your cloud drive, e.g. `~/OneDrive/MyPassword/`
5. The new vault file is created at `<cloud folder>/mypassword.db` and the pointer file at `~/.mypassword/vault.path` records that location

**Migrating an existing vault to a cloud folder:**

1. Quit MyPassword
2. Move (or copy) your current `mypassword.db` into the cloud folder, e.g. `~/OneDrive/MyPassword/mypassword.db`
3. Delete `~/.mypassword/vault.path` and `~/.mypassword/mypassword.db` if they still exist
4. Launch MyPassword — the **Locate Vault** dialog appears
5. Click **Open existing vault…** and pick the file you just moved
6. The pointer file is written and MyPassword uses the cloud-synced file from now on

**Caveat — concurrent access.** SQLite does not support reliable concurrent writes from different processes over network file systems. If you run MyPassword on multiple machines that share the same cloud-synced vault, make sure only one instance has the vault unlocked at a time.

## How to Restore

1. Close MyPassword if it is running
2. Copy the backup file over the real vault file (the path recorded in `~/.mypassword/vault.path`, or the default `~/.mypassword/mypassword.db` if no pointer file exists)
3. Open MyPassword and unlock with your master password (or OAuth recovery)

If you want to restore to a different location, delete the pointer file first and use the **Open existing vault…** button in the Locate Vault dialog to point MyPassword at the restored file.

## OAuth Recovery Configuration

If you enable OAuth recovery, each provider needs a small JSON config stored in the vault's `recovery_config` row. The required fields are:

| Field | Description |
|---|---|
| `client_id` | The OAuth client ID issued by the provider |
| `client_secret` | The OAuth client secret (optional for PKCE-only providers) |
| `issuer` | Exact string expected in the ID token's `iss` claim |
| `jwks` | URL of the provider's JWKS (used to verify the ID token signature) |

Both `issuer` and `jwks` are required for signature and issuer verification. If `jwks` is missing, the JWT signature is not checked and the token payload is trusted blindly — do **not** run in that mode in production.

### Google

| Field | Value |
|---|---|
| `issuer` | `https://accounts.google.com` |
| `jwks` | `https://www.googleapis.com/oauth2/v3/certs` |

### Microsoft (Entra ID / Azure AD)

| Field | Value |
|---|---|
| `issuer` | `https://login.microsoftonline.com/{appId}/v2.0` — use your **application (client) ID**, not the tenant ID |
| `jwks` | `https://login.microsoftonline.com/common/discovery/v2.0/keys` — `common`, no tenant ID |

Make sure your app registration has `"accessTokenAcceptedVersion": 2` in the manifest, otherwise tokens will be v1 and fail signature verification against the v2 JWKS.

To confirm the exact `issuer` string for any provider, paste an ID token into <https://jwt.ms> and read the `iss` claim.

## Security Considerations

- **The file is encrypted, but not invulnerable.** An attacker with the backup file can attempt offline brute-force attacks against your master password with no rate limiting. Use a strong master password. See [Security](/key-gen#when-can-the-vault-be-compromised) for details.
- **Keep backup copies limited.** Every copy is another target. Delete old backups you no longer need.
- **Enable MFA on your cloud account.** If your cloud account is compromised, the attacker gets the vault file. The vault is still encrypted, but they can begin offline attacks.
- **Do not share the file.** Even though it is encrypted, sharing it exposes it to unnecessary risk.
