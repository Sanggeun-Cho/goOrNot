package com.thc.goornotdev.exception;

/**
 * 외부 OpenAPI(TourAPI 등) 호출 실패.
 *
 * 메시지에는 인증키 같은 비밀값이 절대 들어가면 안 된다.
 * 호출부에서 마스킹한 문자열만 넘긴다.
 */
public class ExternalApiException extends RuntimeException {
    public ExternalApiException(String message) {
        super("외부 API 호출에 실패했습니다. " + message);
    }
}
