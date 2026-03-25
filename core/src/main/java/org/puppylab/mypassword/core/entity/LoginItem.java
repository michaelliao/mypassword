package org.puppylab.mypassword.core.entity;

import org.puppylab.mypassword.rpc.data.AbstractLoginItem;

import jakarta.persistence.Id;

public class LoginItem extends AbstractLoginItem {

    @Id
    public long id;

    public boolean deleted;

}
