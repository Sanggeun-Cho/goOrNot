package com.thc.goornotdev.exception;

public class DuplicateDataException extends RuntimeException {
    public DuplicateDataException(String message) {
        super("이미 사용 중입니다. " + message);
    }
}
