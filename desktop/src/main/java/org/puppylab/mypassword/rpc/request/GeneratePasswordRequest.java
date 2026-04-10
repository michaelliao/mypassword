package org.puppylab.mypassword.rpc.request;

import org.puppylab.mypassword.rpc.BaseRequest;
import org.puppylab.mypassword.util.PasswordUtils;

public class GeneratePasswordRequest extends BaseRequest {

    public int len   = 16;
    public int style = PasswordUtils.STYLE_ALPHABET_NUMBER;
}
