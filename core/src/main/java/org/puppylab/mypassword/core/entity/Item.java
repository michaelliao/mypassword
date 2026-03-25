package org.puppylab.mypassword.core.entity;

import org.puppylab.mypassword.rpc.data.AbstractItem;

import jakarta.persistence.Id;

public class Item extends AbstractItem {

    @Id
    public long id;

    public boolean deleted;

    public int item_type;
}
