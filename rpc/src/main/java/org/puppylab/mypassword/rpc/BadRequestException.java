package org.puppylab.mypassword.rpc;

public class BadRequestException extends RuntimeException {

    public final ErrorCode errorCode;

    public BadRequestException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
