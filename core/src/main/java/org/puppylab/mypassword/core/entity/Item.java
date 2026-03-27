package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Id;

public class Item {

    @Id
    public long id;

    public boolean favorite;

    public boolean deleted;

    public int item_type;

    public long updated_at;

    public String b64_encrypted_data;
    public String b64_encrypted_data_iv;

}
