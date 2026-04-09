package org.puppylab.mypassword.core.data;

import org.puppylab.mypassword.util.StringUtils;

public class IdentityItemData extends AbstractItemData {

    public IdentityFieldsData data;

    @Override
    public AbstractFields fields() {
        return data;
    }

    @Override
    public String title() {
        return StringUtils.normalize(data == null ? null : data.name);
    }

    @Override
    public String subtitle() {
        String s = null;
        if (data != null) {
            if (data.mobiles != null && !data.mobiles.isEmpty()) {
                s = data.mobiles.get(0);
            }
            if (s == null && data.telephones != null && !data.telephones.isEmpty()) {
                s = data.telephones.get(0);
            }
            if (s == null && data.email != null && !data.email.isEmpty()) {
                s = data.email;
            }
        }
        return StringUtils.normalize(s);
    }

}
