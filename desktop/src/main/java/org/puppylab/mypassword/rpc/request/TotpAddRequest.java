package org.puppylab.mypassword.rpc.request;

import org.puppylab.mypassword.rpc.BaseRequest;

public class TotpAddRequest extends BaseRequest {

    public long itemId;

    // otpauth://xxx
    public String uri;
}
