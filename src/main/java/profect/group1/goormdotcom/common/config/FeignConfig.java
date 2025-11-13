package profect.group1.goormdotcom.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import feign.RequestInterceptor;
import feign.RequestTemplate;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class FeignConfig implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;

        var req = attrs.getRequest();

        String userId = req.getHeader("user-id");
        if (userId != null) {
            template.header("user-id", userId);
        }

        String role = req.getHeader("user-roles");
        if (role != null) {
            template.header("user-roles", role);
        }
    }

    @Bean
    public feign.Retryer feignRetryer() {
        return new feign.Retryer.Default(200, 1000, 3);
    }
}