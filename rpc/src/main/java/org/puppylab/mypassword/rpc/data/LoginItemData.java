package org.puppylab.mypassword.rpc.data;

import org.puppylab.mypassword.rpc.util.StringUtils;

public class LoginItemData extends AbstractItemData {

    public LoginFieldsData data;

    @Override
    public AbstractFields fields() {
        return data;
    }

    @Override
    public String title() {
        return data == null ? "" : data.title;
    }

    @Override
    public String subtitle() {
        return StringUtils.normalize(data == null ? null : data.username);
    }
}
