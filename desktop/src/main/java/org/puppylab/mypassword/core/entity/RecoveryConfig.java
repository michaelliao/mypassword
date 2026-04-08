package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Id;

public class RecoveryConfig {

    @Id
    public String oauth_provider;
    public String oauth_config_json;

    public String oauth_name;
    public String oauth_email;

    // sha256(uid) only for check if uid matches:
    public String b64_uid_hash;
    // random hmac key:
    public String b64_uid_hash_hmac;

    // AES-key = pbe(hmac-sha256(uid, uid_hash_hmac), pbe_salt, iterations)
    public int    pbe_iterations;
    public String b64_pbe_salt;

    public String b64_encrypted_dek;
    public String b64_encrypted_dek_iv;

    public long updated_at;
}
