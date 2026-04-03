package org.puppylab.mypassword.core.data;

import org.puppylab.mypassword.util.StringUtils;

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
