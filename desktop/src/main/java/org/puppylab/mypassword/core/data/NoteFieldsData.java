package org.puppylab.mypassword.core.data;

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
