package org.puppylab.mypassword.rpc.request;

import org.puppylab.mypassword.rpc.BaseRequest;

/**
 * Request body for {@code POST /totps/get}.
 */
public class TotpGetRequest extends BaseRequest {

    public long itemId;
}
