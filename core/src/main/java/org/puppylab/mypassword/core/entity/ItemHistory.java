package org.puppylab.mypassword.core.entity;

import org.puppylab.mypassword.rpc.data.AbstractItem;

import jakarta.persistence.Id;

public class ItemHistory extends AbstractItem {

    @Id
    public long hid;
    public long rid;

}
