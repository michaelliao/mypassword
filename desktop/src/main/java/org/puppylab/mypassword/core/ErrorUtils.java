package org.puppylab.mypassword.core;

import org.puppylab.mypassword.rpc.BaseResponse;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.util.JsonUtils;

public class ErrorUtils {

    public static BaseResponse error(ErrorCode code, String message) {
        var error = new BaseResponse();
        error.error = code;
        error.errorMessage = message;
        return error;
    }

    public static String errorJson(ErrorCode code, String message) {
        return JsonUtils.toJson(error(code, message));
    }
}
