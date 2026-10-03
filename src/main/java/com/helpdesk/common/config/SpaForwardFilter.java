package com.helpdesk.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Serves the SPA: any GET to a non-API, non-asset path is forwarded to
 * index.html so client-side routes (/tickets/5, /metrics, ...) work on
 * hard refresh. Everything under the excluded prefixes keeps normal
 * API/doc/actuator behaviour (including 404 JSON).
 */
@Component
public class SpaForwardFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PREFIXES =
            List.of("/api", "/v3", "/swagger", "/actuator", "/error");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean isGet = "GET".equals(request.getMethod());
        boolean excluded = EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
        boolean looksLikeAsset = path.contains(".");
        if (isGet && !excluded && !looksLikeAsset) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
