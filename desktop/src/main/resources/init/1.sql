---------- Schema for mypassword ----------

CREATE TABLE VaultVersion (
    id INTEGER PRIMARY KEY CHECK (id = 1), -- only 1 record
    version INTEGER
);

INSERT INTO VaultVersion (id, version) VALUES(1, 1);

CREATE TABLE VaultConfig (
    id INTEGER PRIMARY KEY CHECK (id = 1) NOT NULL, -- only 1 record
    pbe_iterations INTEGER NOT NULL,                -- iterations
    b64_pbe_salt TEXT NOT NULL,                     -- salt
    b64_encrypted_dek TEXT NOT NULL,                -- encrypted data encryption key
    b64_encrypted_dek_iv TEXT NOT NULL              -- iv used above
);

CREATE TABLE Setting (
    setting_key TEXT PRIMARY KEY NOT NULL, -- lock.time = "10min"
    setting_value TEXT NOT NULL,           -- "600"
    setting_type TEXT NOT NULL             -- int, bool, string
);

CREATE TABLE RecoveryConfig (
    oauth_provider TEXT PRIMARY KEY NOT NULL, -- OAuth provider: GOOGLE, GITHUB, etc.
    oauth_client_id TEXT NOT NULL,            -- OAuth client id
    b64_uid_hash TEXT NOT NULL,               -- OAuth user id hash by HmacSHA256
    b64_uid_hash_hmac TEXT NOT NULL,          -- hmac key used
    b64_encrypted_dek TEXT NOT NULL,          -- encrypted dek
    b64_encrypted_dek_iv TEXT NOT NULL        -- iv used above
);

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
