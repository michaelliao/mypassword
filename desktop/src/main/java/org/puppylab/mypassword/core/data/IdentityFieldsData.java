package org.puppylab.mypassword.core.data;

import java.util.List;

public class IdentityFieldsData extends AbstractFields {

    public String       name;
    public String       email;
    public String       passport_number;
    public String       identity_number;
    public String       tax_number;
    public List<String> mobiles;
    public List<String> telephones;
    public String       address;
    public String       zip_code;
    public String       memo;

    @Override
    public String check() {
        if (name == null || name.isBlank()) {
            return "name";
        }
        return null;
    }

}
