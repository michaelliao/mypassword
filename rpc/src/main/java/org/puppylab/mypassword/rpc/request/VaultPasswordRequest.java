package org.puppylab.mypassword.rpc.request;

import org.puppylab.mypassword.rpc.BaseRequest;

public class VaultPasswordRequest implements BaseRequest {

    // required for init and unlock:
    public String password;

}
