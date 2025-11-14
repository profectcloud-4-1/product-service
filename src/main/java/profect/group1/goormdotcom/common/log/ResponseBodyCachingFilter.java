package profect.group1.goormdotcom.common.log;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@WebFilter(urlPatterns = "/*")
public class ResponseBodyCachingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse httpResponse) {
            CachedHttpResponseWrapper wrappedResponse = new CachedHttpResponseWrapper(httpResponse);

            try {
                chain.doFilter(request, wrappedResponse);

                byte[] responseBody = wrappedResponse.getCachedBody();
                if (responseBody != null && responseBody.length > 0) {
                    response.getOutputStream().write(responseBody);
                }
            } finally {
                response.getOutputStream().flush();
            }

        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}