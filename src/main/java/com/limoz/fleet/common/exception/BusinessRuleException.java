package com.limoz.fleet.common.exception;

/**
 * A request that is syntactically valid but violates an operational rule
 * (e.g. dispatching a vehicle that is in the workshop). Rendered as HTTP 422.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String message) {
        this(null, message);
    }

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
