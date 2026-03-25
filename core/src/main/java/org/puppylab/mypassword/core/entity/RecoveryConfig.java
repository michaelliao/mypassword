package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Id;

public class RecoveryConfig {

    @Id
    public String oauth_provider;
    public String oauth_client_id;

    public String b64_uid_hash;
    public String b64_uid_hash_hmac;

    public String b64_encrypted_dek;
    public String b64_encrypted_dek_iv;

}
