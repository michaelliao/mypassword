package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Id;

public class ItemHistory {

    @Id
    public long hid; // history id
    public long rid; // reference id

    public long updated_at;

    public String b64_encrypted_data;
    public String b64_encrypted_data_iv;

}
