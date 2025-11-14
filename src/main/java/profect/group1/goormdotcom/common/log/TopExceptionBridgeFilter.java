package profect.group1.goormdotcom.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class TopExceptionBridgeFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver handlerExceptionResolver;
    private final Environment env;

    private boolean isProd() {
        for (String p : env.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String uri = req.getRequestURI();

        // 로깅 제외 경로
        if (uri.equals("/actuator/health")
                || uri.startsWith("/actuator/health/")
                || uri.startsWith("/swagger-ui")
                || uri.equals("/swagger-ui.html")
                || uri.startsWith("/v3/api-docs")) {
            chain.doFilter(req, res);
            return;
        }

        // 요청 ID
        String requestId = UUID.randomUUID().toString();
        req.setAttribute("X-Request-ID", requestId);
        res.addHeader("X-Request-ID", requestId);

        ContentCachingRequestWrapper request = new ContentCachingRequestWrapper(req);
        ContentCachingResponseWrapper response = new ContentCachingResponseWrapper(res);

        long start = System.currentTimeMillis();

        logRequest(request, requestId);

        Throwable thrown = null;
        try {
            chain.doFilter(request, response);
        } catch (Throwable ex) {
            thrown = ex;
            log.error("[FilterError] id={} uri={} msg={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
            handlerExceptionResolver.resolveException(
                    request, response, null,
                    (Exception) (ex instanceof Exception ? ex : new RuntimeException(ex))
            );
        } finally {
            long took = System.currentTimeMillis() - start;
            logResponse(requestId, request, response, thrown, took);
            response.copyBodyToResponse();
        }
    }

    /* ====================== 요청 로그 ======================== */
    private void logRequest(HttpServletRequest request, String requestId) {
        final String method = request.getMethod();
        final String uri = request.getRequestURI();

        @SuppressWarnings("unchecked")
        Map<String, String> pathVars =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVars != null && !pathVars.isEmpty()) {
            log.info("[REQ PATH] id={} {} {} pathVars={}", requestId, method, uri, pathVars);
        }

        Map<String, String> params = extractRequestParams(request);
        if (!params.isEmpty()) {
            log.info("[REQ PARAM] id={} {} {} params={}", requestId, method, uri, params);
        }

        String body = readRequestBody(request);
        if (!body.isBlank() && !isProd()) {
            log.info("[REQ BODY] id={} {} {} body={}", requestId, method, uri, LoggingUtil.maskSensitiveFields(body));
        }

        if ((pathVars == null || pathVars.isEmpty()) && params.isEmpty() && body.isBlank()) {
            log.info("[REQ] id={} {} {}", requestId, method, uri);
        }
    }

    /* ====================== 응답 로그 ======================== */
    private void logResponse(String requestId, HttpServletRequest request,
                             ContentCachingResponseWrapper response, Throwable ex, long tookMs) {

        final String uri = request.getRequestURI();
        final int status = response.getStatus();
        final String rawBody = readResponseBody(response);

        if (isProd() && ex == null) {
            log.info("[RES] id={} {} → status={} took={}ms", requestId, uri, status, tookMs);
            return;
        }

        String pretty = LoggingUtil.toPrettyJson(rawBody);
        String safeBody = LoggingUtil.maskSensitiveFields(pretty);

        if (ex != null) {
            log.error("[RES] id={} {} → status={} took={}ms body={} (Exception: {})",
                    requestId, uri, status, tookMs, safeBody, ex.getClass().getSimpleName());
        } else {
            log.info("[RES] id={} {} → status={} took={}ms body={}",
                    requestId, uri, status, tookMs, safeBody);
        }
    }

    private Map<String, String> extractRequestParams(HttpServletRequest request) {
        Map<String, String> m = new LinkedHashMap<>();
        var names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String p = names.nextElement();
            m.put(p, request.getParameter(p));
        }
        return m;
    }

    private String readRequestBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper w)) return "";
        byte[] buf = w.getContentAsByteArray();
        if (buf == null || buf.length == 0) return "";
        return new String(buf, StandardCharsets.UTF_8).trim();
    }

    private String readResponseBody(ContentCachingResponseWrapper response) {
        byte[] buf = response.getContentAsByteArray();
        if (buf == null || buf.length == 0) return "";
        return new String(buf, StandardCharsets.UTF_8);
    }
}