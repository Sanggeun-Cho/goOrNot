package com.thc.goornotdev.exception;

public class NoMatchingDataException extends RuntimeException {
    public NoMatchingDataException(String message) {
        super("데이터가 없습니다. " + message);
    }
}
