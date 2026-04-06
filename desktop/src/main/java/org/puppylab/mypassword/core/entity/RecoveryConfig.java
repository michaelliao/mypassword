package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Id;

public class RecoveryConfig {

    @Id
    public String oauth_provider;
    public String oauth_client_id;
    public String oauth_client_secret;

    public String oauth_name;
    public String oauth_email;

    // AES-key = hmac-sha256(uid, uid_hash_hmac)

    // sha256(uid) only for check if uid matches:
    public String b64_uid_hash;
    // random hmac key:
    public String b64_uid_hash_hmac;

    public String b64_encrypted_dek;
    public String b64_encrypted_dek_iv;

    public long updated_at;
}
