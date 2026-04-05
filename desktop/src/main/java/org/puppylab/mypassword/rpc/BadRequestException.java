package org.puppylab.mypassword.rpc;

/**
 * HttpException will be converted to JSON error response.
 */
public class BadRequestException extends RuntimeException {

    public final ErrorCode errorCode;

    public BadRequestException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
