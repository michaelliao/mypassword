package org.puppylab.mypassword.rpc;

/**
 * VaultException will be converted to JSON error response.
 */
public class VaultException extends RuntimeException {

    public final ErrorCode errorCode;

    public VaultException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
