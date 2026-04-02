package org.puppylab.mypassword.rpc.data;

import org.puppylab.mypassword.rpc.util.StringUtils;

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
        }
        return StringUtils.normalize(s);
    }

}
