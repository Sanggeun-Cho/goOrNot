package com.thc.goornotdev.exception;

/**
 * 어노테이션 하나로는 표현할 수 없는 요청 검증 실패.
 * (예: source 가 SEARCH 면 좌표가 필수, RANDOM 이면 totalCount 가 필수)
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
