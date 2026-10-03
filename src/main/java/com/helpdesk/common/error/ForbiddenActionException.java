package com.helpdesk.common.error;

public class ForbiddenActionException extends RuntimeException {
    public ForbiddenActionException(String detail) {
        super(detail);
    }
}
