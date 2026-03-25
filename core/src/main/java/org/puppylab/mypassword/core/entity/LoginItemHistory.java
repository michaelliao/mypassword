package org.puppylab.mypassword.core.entity;

import org.puppylab.mypassword.rpc.data.AbstractLoginItem;

import jakarta.persistence.Id;

public class LoginItemHistory extends AbstractLoginItem {

    @Id
    public long hid;
    public long rid;

}
