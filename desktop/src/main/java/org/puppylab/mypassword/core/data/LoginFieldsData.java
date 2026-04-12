package org.puppylab.mypassword.core.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public class LoginFieldsData extends AbstractFields {

    public String title;
    public String username;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String password;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public PasskeyData passkey;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public TotpData totp;

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
