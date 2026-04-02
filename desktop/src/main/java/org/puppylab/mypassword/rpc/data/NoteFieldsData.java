package org.puppylab.mypassword.rpc.data;

public class NoteFieldsData extends AbstractFields {

    public String title;
    public String content;

    @Override
    public String check() {
        if (title == null || title.isBlank()) {
            return "title";
        }
        return null;
    }

}
