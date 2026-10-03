package com.helpdesk.common.error;

public class UnprocessableException extends RuntimeException {
    public UnprocessableException(String detail) {
        super(detail);
    }
}
