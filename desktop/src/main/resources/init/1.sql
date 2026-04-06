---------- Schema for mypassword ----------

CREATE TABLE VaultVersion (
    id INTEGER PRIMARY KEY CHECK (id = 1), -- only 1 record
    version INTEGER
);

INSERT INTO VaultVersion (id, version) VALUES(1, 1);

CREATE TABLE VaultSetting (
    setting_key TEXT PRIMARY KEY NOT NULL, -- lock.time
    setting_value TEXT NOT NULL            -- "10"
);

-- User master password:
CREATE TABLE VaultConfig (
    id INTEGER PRIMARY KEY CHECK (id = 1) NOT NULL, -- only 1 record
    pbe_iterations INTEGER NOT NULL,                -- iterations
    b64_pbe_salt TEXT NOT NULL,                     -- salt
    b64_encrypted_dek TEXT NOT NULL,                -- encrypted data encryption key
    b64_encrypted_dek_iv TEXT NOT NULL              -- iv used above
);

-- User oauth recovery:
CREATE TABLE RecoveryConfig (
    oauth_provider TEXT PRIMARY KEY NOT NULL,        -- OAuth provider: "google", "github", etc.
    oauth_client_id TEXT NOT NULL,                   -- OAuth client id
    oauth_client_secret TEXT NOT NULL,               -- OAuth client secret
    oauth_name TEXT NOT NULL DEFAULT "",             -- OAuth user display name
    oauth_email TEXT NOT NULL DEFAULT "",            -- OAuth user email
    b64_uid_hash TEXT NOT NULL DEFAULT "",           -- OAuth user id hash by HmacSHA256
    b64_uid_hash_hmac TEXT NOT NULL DEFAULT "",      -- hmac key used
    pbe_iterations INTEGER NOT NULL DEFAULT 1000000, -- iterations
    b64_pbe_salt TEXT NOT NULL DEFAULT "",           -- salt
    b64_encrypted_dek TEXT NOT NULL DEFAULT "",      -- encrypted dek
    b64_encrypted_dek_iv TEXT NOT NULL DEFAULT "",   -- iv used above
    updated_at INTEGER                               -- updated at timestamp
);

INSERT INTO RecoveryConfig (oauth_provider, oauth_client_id, oauth_client_secret) VALUES("google", "316516407199-o9kch40i9adm4881k4kl5e9ngrfihoi2.apps.googleusercontent.com", "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");
INSERT INTO RecoveryConfig (oauth_provider, oauth_client_id, oauth_client_secret) VALUES("github", "Ov23liFEn9C7XEvQlOeR", "2fdfa08c8f88da4271a9154e84e8e132f448a5b8");

CREATE TABLE Item (
    id INTEGER PRIMARY KEY,               -- auto increment id
    favorite INTEGER NOT NULL,             -- in favorite?
    deleted INTEGER NOT NULL,             -- in trash?
    item_type INTEGER NOT NULL,           -- item type
    b64_encrypted_data TEXT NOT NULL,     -- encrypted json
    b64_encrypted_data_iv TEXT NOT NULL,  -- iv used above
    updated_at INTEGER                    -- updated at timestamp
);

CREATE TABLE ItemHistory (
    hid INTEGER PRIMARY KEY,  -- history id
    rid INTEGER,              -- ref id to Item
    b64_encrypted_data TEXT NOT NULL,
    b64_encrypted_data_iv TEXT NOT NULL,
    updated_at INTEGER
);

CREATE INDEX idx_item_history ON ItemHistory (rid);
