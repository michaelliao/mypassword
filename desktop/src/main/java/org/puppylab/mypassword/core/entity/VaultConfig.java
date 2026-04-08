package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Id;

/**
 * Derive PBE key from master password.
 */
public class VaultConfig {

    @Id
    public int id;

    public int pbe_iterations;

    public String b64_pbe_salt;

    public String b64_encrypted_dek;
    public String b64_encrypted_dek_iv;

}
