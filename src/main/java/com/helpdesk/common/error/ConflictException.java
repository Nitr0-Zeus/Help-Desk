package com.helpdesk.common.error;

public class ConflictException extends RuntimeException {
    public ConflictException(String detail) {
        super(detail);
    }
}
