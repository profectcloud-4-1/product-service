package profect.group1.goormdotcom.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import profect.group1.goormdotcom.common.dto.UserContext;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class UserHeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "User-Id";
    public static final String USER_ROLES_HEADER = "User-Roles";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            String userIdHeader = request.getHeader(USER_ID_HEADER);
            String roleHeader = request.getHeader(USER_ROLES_HEADER);

            if (userIdHeader != null && roleHeader != null) {
                try {
                    UUID userId = UUID.fromString(userIdHeader.trim());
                    String role = roleHeader.trim(); // "CUSTOMER", "MASTER"

                    var authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + role)
                    );

                    UserContext principal = new UserContext(userId, role);

                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authorities
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (IllegalArgumentException e) {
                    //log.warn("Invalid User-Id header: {}", userIdHeader, e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}