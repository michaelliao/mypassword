package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Id;

public class VaultSetting {

    @Id
    public String setting_key;

    public String setting_value;

}
