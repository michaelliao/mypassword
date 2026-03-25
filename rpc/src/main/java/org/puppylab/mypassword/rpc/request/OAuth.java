package org.puppylab.mypassword.rpc.request;

import org.puppylab.mypassword.rpc.BaseRequest;

public class OAuth implements BaseRequest {

    public String action;

    public String provider;

    public String oauthId;
}
