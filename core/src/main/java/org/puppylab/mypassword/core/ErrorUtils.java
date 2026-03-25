package org.puppylab.mypassword.core;

import org.puppylab.mypassword.rpc.BaseResponse;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

public class ErrorUtils {

    public static BaseResponse error(ErrorCode code, String message) {
        var error = new BaseResponse();
        error.error = code;
        error.errorMessage = message;
        return error;
    }

    public static String errorJson(ErrorCode code, String message) {
        try {
            return JsonUtils.getObjectMapper().writeValueAsString(error(code, message));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
