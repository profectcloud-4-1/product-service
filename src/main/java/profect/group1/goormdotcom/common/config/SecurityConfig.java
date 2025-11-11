package profect.group1.goormdotcom.common.config;

import org.springframework.http.HttpMethod;
import profect.group1.goormdotcom.common.security.UserHeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public UserHeaderAuthenticationFilter userHeaderAuthenticationFilter() {
        return new UserHeaderAuthenticationFilter();
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // 스웨거 허용
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**", "/api-docs", "/swagger-ui.html", "/h2-console/**").permitAll()
                        // 회원가입, 로그인 허용
                        .requestMatchers("/api/v1/users/register").permitAll()
                        .requestMatchers("/api/v1/users/login").permitAll()
                        .requestMatchers("/api/v1/payments/toss/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/payment/success").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/payment/fail").permitAll()
                        // 내부api 허용
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(userHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}