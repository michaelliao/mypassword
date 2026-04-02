package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;

public class VaultVersion {

    @Id
    public int id;

    @Column
    public int version;
}
