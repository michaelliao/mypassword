# Key Generation & OAuth Recovery

## Overview

MyPassword uses a two-layer encryption architecture. A random **Data Encryption Key (DEK)** encrypts all vault data. The DEK itself is encrypted (wrapped) by a key derived from the user's master password. Optionally, a second copy of the DEK is encrypted using an OAuth identity for recovery.

## Algorithms

| Component | Algorithm | Key Size | Salt | IV | Iterations |
|---|---|---|---|---|---|
| DEK | SecureRandom | 256-bit | - | - | - |
| Key Derivation | PBKDF2-HMAC-SHA256 | 256-bit | 256-bit | - | 1,000,000 |
| Data Encryption | AES-256-GCM | 256-bit | - | 96-bit | - |

## DEK Generation

A 256-bit DEK is generated using `java.security.SecureRandom` when the vault is first created. This key is never stored in plaintext — it is always wrapped by a derived key.

## Master Password Path

When the user sets a master password, the DEK is encrypted as follows:

1. Generate a random 32-byte **salt**
2. Derive a 256-bit wrapping key from the master password using **PBKDF2-HMAC-SHA256** (1,000,000 iterations)
3. Generate a random 12-byte **IV**
4. Encrypt the DEK using **AES-256-GCM** with the derived key and IV

Stored in `VaultConfig`:

- `pbe_iterations` — PBKDF2 iteration count
- `b64_pbe_salt` — Base64url-encoded salt
- `b64_encrypted_dek` — Base64url-encoded ciphertext (includes GCM tag)
- `b64_encrypted_dek_iv` — Base64url-encoded IV

### Unlocking

1. Derive the wrapping key from the entered password using the stored salt and iterations
2. Decrypt the DEK using AES-256-GCM with the stored IV
3. Hold the DEK in memory (`Session`) for data operations

## Data Encryption

Each vault item (login, note, identity) is encrypted individually:

1. Serialize the item to a JSON string (UTF-8)
2. Generate a random 12-byte IV
3. Encrypt with AES-256-GCM using the DEK
4. Store the IV and ciphertext as Base64url in the SQLite database

## OAuth Recovery

OAuth recovery provides an alternative way to decrypt the DEK when the master password is forgotten. It supports **Google** and **Microsoft** OAuth with PKCE.

### Setup

When the user connects an OAuth account (while the vault is already unlocked):

1. Perform OAuth PKCE flow with the chosen provider (Google or Microsoft, `openid email profile` scopes)
2. Extract the user's unique `sub` (subject) claim from the ID token
3. Generate a random 32-byte **HMAC key**
4. Compute `SHA-256(oauthId)` — stored for later verification
5. Derive a password: `HMAC-SHA256(oauthId, hmacKey)`
6. Derive a 256-bit wrapping key from that password using **PBKDF2-HMAC-SHA256** (1,000,000 iterations) with a new random salt
7. Generate a random 12-byte IV
8. Encrypt the DEK using AES-256-GCM

Stored in `RecoveryConfig`:

- `oauth_provider` — provider name (`google` or `microsoft`)
- `oauth_config_json` — OAuth client configuration
- `oauth_name` / `oauth_email` — display info
- `b64_uid_hash` — `SHA-256(oauthId)`, used to verify identity on recovery
- `b64_uid_hash_hmac` — HMAC key used in key derivation
- `pbe_iterations` — PBKDF2 iteration count
- `b64_pbe_salt` — salt
- `b64_encrypted_dek` — encrypted DEK
- `b64_encrypted_dek_iv` — IV

### Recovery

1. User initiates OAuth recovery and authenticates with the linked provider (PKCE flow)
2. Extract `oauthId` from the ID token
3. Verify: `SHA-256(oauthId)` matches the stored `b64_uid_hash`
4. Derive the wrapping key: `PBKDF2(HMAC-SHA256(oauthId, storedHmacKey), storedSalt, iterations)`
5. Decrypt the DEK using AES-256-GCM with the stored IV
6. Unlock the vault — user is prompted to set a new master password

## OAuth Configuration

### Default Client IDs

MyPassword ships with built-in OAuth client IDs for each provider, seeded into the `RecoveryConfig` table when the vault is first created:

| Provider | Client ID | Client Secret |
|---|---|---|
| Google | `316516407199-o9kch...googleusercontent.com` | Required by Google for desktop apps, but not treated as confidential (per Google's documentation) |
| Microsoft | `983c29d1-7261-4827-a1a9-a00603a15367` | Not required (public client) |

The OAuth config is stored as a JSON string in the `oauth_config_json` column of the `RecoveryConfig` table in the vault database (`~/.mypassword/mypassword.db`).

Google format:
```json
{"client_id":"<client_id>","client_secret":"<client_secret>"}
```

Microsoft format:
```json
{"client_id":"<client_id>"}
```

### Using Your Own OAuth App

Advanced users can replace the default client IDs with their own OAuth application credentials.

**Google:**

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project (or select an existing one)
3. Navigate to **APIs & Services > Credentials**
4. Create an **OAuth 2.0 Client ID** with application type **Desktop app**
5. Note the `client_id` and `client_secret`
6. Under **OAuth consent screen**, add the scopes: `openid`, `email`, `profile`
7. Add `http://127.0.0.1:27432/oauth/google/callback` as an authorized redirect URI

**Microsoft:**

1. Go to [Azure Portal > App registrations](https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps)
2. Register a new application with **Accounts in any organizational directory and personal Microsoft accounts**
3. Under **Authentication**, add a **Mobile and desktop** platform with redirect URI `http://127.0.0.1:27432/oauth/microsoft/callback`
4. Note the `Application (client) ID`
5. Under **API permissions**, ensure `openid`, `email`, and `profile` are granted

**Applying the config:**

Update the `RecoveryConfig` table directly in the vault database before connecting an OAuth account:

```sql
UPDATE RecoveryConfig
SET oauth_config_json = '{"client_id":"<your_client_id>","client_secret":"<your_client_secret>"}'
WHERE oauth_provider = 'google';

UPDATE RecoveryConfig
SET oauth_config_json = '{"client_id":"<your_client_id>"}'
WHERE oauth_provider = 'microsoft';
```

**Important:** If you have already set up OAuth recovery with the default client ID, changing the client ID will **not** affect existing recovery. The recovery key derivation is based on your OAuth user ID (`sub` claim), not the client ID. However, you will need to re-authenticate through the new OAuth app to initiate any new recovery setup.

## Session & Auto-Lock

- The decrypted DEK is held in memory in the `Session` object
- Auto-lock triggers after 10 minutes of OS-level input inactivity (keyboard/mouse), checked every 30 seconds using platform-native APIs:
  - Windows: `GetLastInputInfo` / `GetTickCount`
  - macOS: `CGEventSourceSecondsSinceLastEventType`
  - Linux: XScreenSaver via `libX11` / `libXss`
- On lock, the DEK is zeroed out from memory

## Architecture Diagram

```
Vault Data (passwords, notes, identities)
    |
    | AES-256-GCM (per-item random IV)
    v
Encrypted Items (stored in SQLite)
    |
    | DEK (256-bit, held in Session memory)
    |
    +---> Master Password Path
    |       Master Password
    |         -> PBKDF2-HMAC-SHA256 (1M iterations, random salt)
    |         -> AES-256-GCM wrap DEK
    |         -> Stored in VaultConfig
    |
    +---> OAuth Recovery Path (optional, Google or Microsoft)
            OAuth PKCE -> oauthId
              -> HMAC-SHA256(oauthId, random hmacKey)
              -> PBKDF2-HMAC-SHA256 (1M iterations, random salt)
              -> AES-256-GCM wrap DEK
              -> Stored in RecoveryConfig
```

## When Can the Vault Be Compromised?

The vault's security depends on protecting the DEK. Below are the conditions under which an attacker could decrypt vault data.

### 1. Weak Master Password

The master password is the primary line of defense. If an attacker obtains the vault database file, they can attempt an offline brute-force attack against the wrapped DEK. PBKDF2 with 1,000,000 iterations slows each guess, but a weak or common password (e.g. `password123`) can still be cracked with sufficient computing resources.

**Mitigation:** Use a strong, unique master password (long, random, high entropy).

### 2. Compromised OAuth Account

If OAuth recovery is enabled and the attacker gains control of the linked Google or Microsoft account, they can perform the OAuth recovery flow to decrypt the DEK — no master password needed. This effectively makes the OAuth account a second master key.

**Mitigation:** Enable MFA on the linked OAuth account. Only enable OAuth recovery if the OAuth account is well-protected.

### 3. Memory Dump While Unlocked

While the vault is unlocked, the DEK is held in plaintext in JVM heap memory. An attacker with local access (e.g. malware, physical access to an unlocked machine) could dump the process memory and extract the DEK.

**Mitigation:** The 10-minute auto-lock reduces the exposure window. Lock the vault manually when stepping away. Keep the OS and software up to date.

### 4. Local File Access + Offline Attack

The vault database (SQLite) and configuration (containing the encrypted DEK, salt, IV) are stored as local files. An attacker who copies these files can attempt offline attacks against either the master password path or the OAuth recovery path without rate limiting.

**Mitigation:** Use full-disk encryption (BitLocker, FileVault, LUKS). Restrict file permissions.

### 5. Keylogger or Screen Capture

A keylogger can capture the master password as it is typed. Screen capture or clipboard monitoring can capture passwords displayed or copied from the vault.

**Mitigation:** Keep the OS free of malware. The clipboard is cleared automatically after a timeout.

### 6. HMAC Key Exposure (OAuth Path)

The OAuth recovery path derives the wrapping key from `HMAC-SHA256(oauthId, hmacKey)`. The HMAC key is stored in `RecoveryConfig`. If an attacker has both the vault file (containing the HMAC key, salt, IV, and encrypted DEK) and knows the user's OAuth `sub` claim, they can derive the wrapping key and decrypt the DEK without authenticating through OAuth.

**Mitigation:** Protect the vault file. The OAuth `sub` claim is not publicly exposed, but it is not a high-entropy secret either — the HMAC key adds the necessary randomness.
