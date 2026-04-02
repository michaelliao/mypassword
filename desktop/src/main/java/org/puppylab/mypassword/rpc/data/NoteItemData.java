package org.puppylab.mypassword.rpc.data;

import org.puppylab.mypassword.util.StringUtils;

public class NoteItemData extends AbstractItemData {

    public NoteFieldsData data;

    @Override
    public AbstractFields fields() {
        return data;
    }

    @Override
    public String title() {
        return StringUtils.normalize(data == null ? null : data.title);
    }

    @Override
    public String subtitle() {
        String s = data == null ? null : data.content;
        if (s != null) {
            int pos = s.indexOf('\n');
            if (pos > 0) {
                s = s.substring(0, pos);
            } else if (s.length() > 20) {
                s = s.substring(0, 20);
            }
        }
        return StringUtils.normalize(s);
    }

}
