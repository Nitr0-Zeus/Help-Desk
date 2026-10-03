package com.helpdesk.common.error;

public class UnsupportedMediaTypeException extends RuntimeException {
    public UnsupportedMediaTypeException(String detail) {
        super(detail);
    }
}
