package com.helpdesk.common.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String detail) {
        super(detail);
    }
}
