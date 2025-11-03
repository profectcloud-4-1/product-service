package profect.group1.goormdotcom.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;

import java.io.IOException;

// 인증이 필요한 API에 대해 인증 실패 시 401 응답 (토큰 미첨부 또는 만료 시)
@Component
public class AuthenticationFailedEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(ErrorStatus._UNAUTHORIZED.getReasonHttpStatus().getHttpStatus().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        ApiResponse<Object> body = ApiResponse.onFailure(
                ErrorStatus._UNAUTHORIZED.getReason().getCode(),
                ErrorStatus._UNAUTHORIZED.getReason().getMessage(),
                null
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}


