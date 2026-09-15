package com.thc.goornotdev.exception;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoMatchingDataException.class)
    public ResponseEntity<Map<String, String>> handleNoMatchingData(NoMatchingDataException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateDataException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateData(DuplicateDataException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(InvalidRequestException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({InvalidTokenException.class, JwtException.class})
    public ResponseEntity<Map<String, String>> handleInvalidToken(RuntimeException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * permitAll 로 열어둔 URL 이라도 메서드 보안(@PreAuthorize)이 걸려 있으면
     * 비로그인 요청이 여기까지 들어와 AccessDeniedException 이 된다.
     * 이때 403 을 내리면 프론트가 Access Token 재발급을 시도하지 않으므로,
     * "아직 인증 안 된 요청" 은 401, "인증은 됐지만 권한이 없는 요청" 만 403 으로 구분한다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        if (!authenticated) {
            return error(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /**
     * 외부 API(TourAPI) 장애는 우리 서버 잘못이 아니므로 500 이 아니라 502 로 구분해 내려준다.
     * 예외 메시지는 호출부에서 이미 마스킹된 값이라 그대로 노출해도 인증키가 새지 않는다.
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<Map<String, String>> handleExternalApi(ExternalApiException e) {
        return error(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                errors.put(fe.getField(), fe.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", message);

        return ResponseEntity.status(status).body(body);
    }
}
