package org.puppylab.mypassword.rpc.data;

import java.util.List;

public class LoginFieldsData extends AbstractFields {

    public String       title;
    public String       username;
    public String       password;
    public List<String> websites;
    public String       ga;
    public String       memo;

    @Override
    public String check() {
        if (title == null || title.isBlank()) {
            return "title";
        }
        return null;
    }

}
