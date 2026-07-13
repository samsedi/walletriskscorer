package com.walletriskscorer.security;

import com.walletriskscorer.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    
    private static final String API_KEY_HEADER = "x-api-key";

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            
        String path = request.getRequestURI();
        
        // Secure all /api/ endpoints. For external integrations, we'll use /api/v1/
        if (path.startsWith("/api/")) {
            String apiKey = request.getHeader(API_KEY_HEADER);
            
            if (apiKey == null || apiKey.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Missing API Key");
                return;
            }
            
            boolean isValid = apiKeyRepository.findByKeyValueAndActiveTrue(apiKey).isPresent();
            if (!isValid) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid or Inactive API Key");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
