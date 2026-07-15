package web.restaurant.swp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JsonCharsetFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        filterChain.doFilter(request, response);

        String contentType = response.getContentType();
        if (contentType != null && contentType.startsWith("application/json") && !contentType.contains("charset")) {
            response.setContentType("application/json;charset=UTF-8");
        }
    }
}
