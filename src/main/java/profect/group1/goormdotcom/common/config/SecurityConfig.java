package profect.group1.goormdotcom.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.security.UserHeaderAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    @Bean
    public UserHeaderAuthenticationFilter userHeaderAuthenticationFilter() {
        return new UserHeaderAuthenticationFilter();
    }

    // Security 레이어에서 발생하는 오류를 로깅
    private void writeSecurityError(
            HttpServletRequest req,
            HttpServletResponse res,
            HttpStatus status,
            String code,
            String message,
            Exception ex
    ) throws IOException {

        log.warn("[SEC-ERROR] {} {} → status={} code={} msg={} ex={}",
                req.getMethod(),
                req.getRequestURI(),
                status.value(),
                code,
                message,
                ex != null ? ex.getClass().getSimpleName() : "none"
        );

        ApiResponse<Object> body = ApiResponse.onFailure(code, message, null);

        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(
                """
                {"code":"%s","message":"%s","result":null}
                """.formatted(code, message)
        );
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            UserHeaderAuthenticationFilter userHeaderAuthenticationFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                // 익명사용자 비활성화
                .anonymous(anon -> anon.disable())

                .exceptionHandling(ex -> ex
                        // 인증 안 된 상태에서 보호된 리소스 접근
                        .authenticationEntryPoint((req, res, authEx) -> {
                            writeSecurityError(
                                    req,
                                    res,
                                    HttpStatus.UNAUTHORIZED,
                                    "COMMON401",
                                    "인증이 필요합니다.",
                                    authEx
                            );
                        })
                        // 인증은 되었는데 권한 부족
                        .accessDeniedHandler((req, res, accessDeniedEx) -> {
                            writeSecurityError(
                                    req,
                                    res,
                                    HttpStatus.FORBIDDEN,
                                    "COMMON403",
                                    "접근 권한이 없습니다.",
                                    accessDeniedEx
                            );
                        })
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // 스웨거 허용
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**",
                                "/api-docs",
                                "/swagger-ui.html",
                                "/h2-console/**"
                        ).permitAll()
                        // 내부 api 허용
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(userHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}