package profect.group1.goormdotcom.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // Custom headers as API keys in header
        SecurityScheme userIdHeader = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("User-Id")
                .description("User ID header for internal calls");

        SecurityScheme userRolesHeader = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("User-Roles")
                .description("Comma-separated user roles header");

        Components components = new Components()
                .addSecuritySchemes("User-Id", userIdHeader)
                .addSecuritySchemes("User-Roles", userRolesHeader);

        // Make headers show up in Swagger UI's Authorize dialog globally
        SecurityRequirement headersRequirement = new SecurityRequirement()
                .addList("User-Id")
                .addList("User-Roles");

        return new OpenAPI()
                .components(components)
                .addSecurityItem(headersRequirement)
                .info(apiInfo());
    }

    private Info apiInfo() {
        return new Info()
                .title("Goormdotcom Swagger")
                .description("Goormdotcom Swagger REST API")
                .version("1.0.0");
    }
}
