package com.helpdesk.common.error;

public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String detail) {
        super(detail);
    }
}
