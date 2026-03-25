package org.puppylab.mypassword.rpc.data;

public enum ItemType {

    LOGIN(1),

    NOTE(2),

    IDENTITY(3);

    public final int value;

    private ItemType(int value) {
        this.value = value;
    }
}
