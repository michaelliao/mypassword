package org.puppylab.mypassword.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;

public class BaseResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ErrorCode error;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String errorMessage;

}
