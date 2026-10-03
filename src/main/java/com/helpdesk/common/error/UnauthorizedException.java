package com.helpdesk.common.error;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String detail) {
        super(detail);
    }
}
