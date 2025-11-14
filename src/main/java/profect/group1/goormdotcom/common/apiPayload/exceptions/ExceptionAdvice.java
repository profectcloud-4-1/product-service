package profect.group1.goormdotcom.common.apiPayload.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.core.env.Environment;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.apiPayload.code.BaseErrorCode;
import profect.group1.goormdotcom.common.apiPayload.code.ErrorReasonDTO;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice(annotations = {RestController.class})
public class ExceptionAdvice extends ResponseEntityExceptionHandler {

    @Autowired(required = false)
    private Environment env;

    /* ========= 공통 컨텍스트 유틸 ========= */


    private String getRequestId(HttpServletRequest req) {
        Object id = req.getAttribute("X-Request-ID");
        return id != null ? id.toString() : "-";
    }

    private long getDurationMillis(HttpServletRequest req) {
        Object st = req.getAttribute("startTime");
        return (st instanceof Long) ? (System.currentTimeMillis() - (Long) st) : -1L;
    }

    private String getClientIp(HttpServletRequest req) {
        String[] candidates = {
                "X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP", "X-Client-IP"
        };
        for (String h : candidates) {
            String v = req.getHeader(h);
            if (v != null && !v.isBlank()) {
                int commaIdx = v.indexOf(',');
                return commaIdx > 0 ? v.substring(0, commaIdx).trim() : v.trim();
            }
        }
        return req.getRemoteAddr();
    }

    private boolean isProd() {
        if (env == null) {
            return false;
        }
        return Arrays.asList(env.getActiveProfiles()).contains("prod");
    }

    /* ========= 공통 로깅 ========= */

    private void logRequest(String level, String tag, WebRequest webRequest, String detail, Throwable ex) {
        HttpServletRequest req = ((ServletWebRequest) webRequest).getRequest();

        String msg = String.format(
                "[%s] id=%s ip=%s %s %s%s took=%dms detail=%s ex=%s",
                tag,
                getRequestId(req),
                getClientIp(req),
                req.getMethod(),
                req.getRequestURI(),
                req.getQueryString() == null ? "" : ("?" + req.getQueryString()),
                getDurationMillis(req),
                detail,
                ex != null ? ex.getClass().getSimpleName() : "none"
        );

        switch (level.toUpperCase()) {
            case "ERROR" -> {
                if (isProd()) {
                    log.error(msg);
                } else {
                    log.error(msg, ex);
                }
            }
            case "WARN" -> {
                log.warn(msg);
                if (!isProd() && ex != null) {
                    log.debug("[{}-stack]", tag, ex);
                }
            }
            default -> log.info(msg);
        }
    }

    private void log4xx(String tag, WebRequest webRequest, String detail, Throwable ex) {
        logRequest("WARN", tag, webRequest, detail, ex);
    }

    private void log5xx(String tag, WebRequest webRequest, String detail, Throwable ex) {
        logRequest("ERROR", tag, webRequest, detail, ex);
    }

    /* ========= 헬퍼 ========= */

    private BaseErrorCode getErrorCodeByName(String name) {
        try {
            return ErrorStatus.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return ErrorStatus._INTERNAL_SERVER_ERROR;
        }
    }

    private ResponseEntity<Object> handleExceptionInternalFalse(
            Exception e, BaseErrorCode errorCommonStatus,
            HttpHeaders headers, HttpStatus status, WebRequest request, Object errorPoint
    ) {
        ApiResponse<Object> body = ApiResponse.onFailure(
                errorCommonStatus.getReason().getCode(),
                errorCommonStatus.getReason().getMessage(),
                errorPoint
        );
        return super.handleExceptionInternal(e, body, headers, status, request);
    }

    private ResponseEntity<Object> handleExceptionInternalArgs(
            Exception e, HttpHeaders headers, BaseErrorCode errorCommonStatus,
            WebRequest request, Map<String, String> errorArgs
    ) {
        ApiResponse<Object> body = ApiResponse.onFailure(
                errorCommonStatus.getReason().getCode(),
                errorCommonStatus.getReason().getMessage(),
                errorArgs
        );
        return super.handleExceptionInternal(
                e, body, headers, errorCommonStatus.getReasonHttpStatus().getHttpStatus(), request
        );
    }

    private ResponseEntity<Object> handleExceptionInternalConstraint(
            Exception e, BaseErrorCode errorCommonStatus,
            HttpHeaders headers, WebRequest request
    ) {
        ApiResponse<Object> body = ApiResponse.onFailure(
                errorCommonStatus.getReason().getCode(),
                errorCommonStatus.getReason().getMessage(),
                null
        );
        return super.handleExceptionInternal(
                e, body, headers, errorCommonStatus.getReasonHttpStatus().getHttpStatus(), request
        );
    }

    private ResponseEntity<Object> handleExceptionInternal(
            Exception e, ErrorReasonDTO reason, HttpHeaders headers, HttpServletRequest request
    ) {
        ApiResponse<Object> body = ApiResponse.onFailure(reason.getCode(), reason.getMessage(), null);
        WebRequest webRequest = new ServletWebRequest(request);
        return super.handleExceptionInternal(e, body, headers, reason.getHttpStatus(), webRequest);
    }

    /* ========= 핸들러 ========= */

    /** IllegalArgumentException 핸들러 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException e, WebRequest request) {
        BaseErrorCode errorCode = ErrorStatus._BAD_REQUEST;
        return handleExceptionInternalFalse(e, errorCode, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request, e.getMessage());
    }

    /** 도메인 공통 예외 */
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<Object> handleGeneral(GeneralException ex, HttpServletRequest request) {
        ErrorReasonDTO reason = ex.getErrorReasonHttpStatus();

        // 로깅
        WebRequest webRequest = new ServletWebRequest(request);
        log4xx("GeneralException", webRequest, reason.getMessage(), ex);

        // X-Request-ID 헤더 연결 유지
        HttpHeaders headers = new HttpHeaders();
        String reqId = getRequestId(request);
        if (!"-".equals(reqId)) {
            headers.add("X-Request-ID", reqId);
        }
        return handleExceptionInternal(ex, reason, headers, request);
    }

    /** Bean Validation - 메서드 파라미터 등 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> validation(ConstraintViolationException e, WebRequest request) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(cv -> Optional.ofNullable(cv.getMessage()).orElse(""))
                .findFirst()
                .orElse("_BAD_REQUEST");

        BaseErrorCode errorCode = getErrorCodeByName(errorMessage);

        log4xx("ConstraintViolation", request, errorMessage, e);
        return handleExceptionInternalConstraint(e, errorCode, HttpHeaders.EMPTY, request);
    }

    /** @RequestBody @Valid 실패 */
    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String fieldName = fieldError.getField();
            String errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
            errors.merge(fieldName, errorMessage, (existing, replacement) -> existing + ", " + replacement);
        });

        BaseErrorCode errorCode = getErrorCodeByName("_BAD_REQUEST");

        log4xx("MethodArgumentNotValid", request, errors.toString(), e);
        return handleExceptionInternalArgs(e, HttpHeaders.EMPTY, errorCode, request, errors);
    }

    /** JSON 파싱 실패 등 */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        BaseErrorCode errorCode = getErrorCodeByName("_BAD_REQUEST");
        String errorPoint = "요청 형식이 올바르지 않습니다. JSON 형식을 확인해주세요.";

        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException inv) {
            String path = inv.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            Class<?> target = inv.getTargetType();
            Object invalid = inv.getValue();

            if (target.isEnum()) {
                String allowed = Arrays.stream(target.getEnumConstants())
                        .map(c -> ((Enum<?>) c).name())
                        .collect(Collectors.joining(", "));
                errorPoint = String.format(
                        "필드 '%s'의 값 '%s'는 잘못된 Enum 값입니다. 허용 값: [%s]",
                        path, invalid, allowed
                );
            }
        }

        log4xx("HttpMessageNotReadable", request, errorPoint, ex);
        return handleExceptionInternalFalse(
                ex, errorCode, headers, errorCode.getReasonHttpStatus().getHttpStatus(), request, errorPoint
        );
    }

    /** 잘못된 쿼리/경로 파라미터 타입 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleBadRequestParameter(MethodArgumentTypeMismatchException ex, WebRequest request) {
        BaseErrorCode errorCode = ErrorStatus.INVALID_REQUEST_PARAMETER;
        String detailMessage = String.format(
                "파라미터 '%s' 값 '%s'은(는) 올바른 형식이 아닙니다.", ex.getName(), ex.getValue()
        );

        log4xx("TypeMismatch", request, detailMessage, ex);

        ApiResponse<Object> body = ApiResponse.onFailure(
                errorCode.getReason().getCode(),
                errorCode.getReason().getMessage(),
                detailMessage
        );
        return super.handleExceptionInternal(
                ex, body, HttpHeaders.EMPTY, errorCode.getReasonHttpStatus().getHttpStatus(), request
        );
    }

    /** 권한 부족 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException e, WebRequest request) {
        BaseErrorCode errorCode = ErrorStatus._FORBIDDEN;
        log4xx("AccessDenied", request, errorCode.getReason().getMessage(), e);
        return handleExceptionInternalFalse(
                e, errorCode, HttpHeaders.EMPTY, errorCode.getReasonHttpStatus().getHttpStatus(),
                request, errorCode.getReason().getMessage()
        );
    }

    protected ResponseEntity<Object> handleServletRequestBinding(
            ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        BaseErrorCode errorCode = ErrorStatus.INVALID_REQUEST_PARAMETER;
        String detail = ex.getMessage();

        log4xx("ServletRequestBinding", request, detail, ex);

        return handleExceptionInternalFalse(
                ex, errorCode, headers, HttpStatus.BAD_REQUEST, request, detail
        );
    }

    /** 처리되지 않은 예외 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> exception(Exception e, WebRequest request) {
        BaseErrorCode errorCode = getErrorCodeByName("_INTERNAL_SERVER_ERROR");
        log5xx("UnhandledException", request, e.getMessage(), e);
        return handleExceptionInternalFalse(
                e, errorCode, HttpHeaders.EMPTY, errorCode.getReasonHttpStatus().getHttpStatus(),
                request, e.getMessage()
        );
    }
}