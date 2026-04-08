package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;

public class VaultVersion {

    public static int ID_DATA_VERSION = 1;

    public static int ID_APP_VERSION = 2;

    @Id
    public int id;

    @Column
    public int version;
}
