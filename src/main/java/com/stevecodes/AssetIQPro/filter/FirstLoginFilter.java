package com.stevecodes.AssetIQPro.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class FirstLoginFilter extends OncePerRequestFilter {

    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
            "/login",
            "/change-password",
            "/logout",
            "/css",
            "/js",
            "/images",
            "/error",
            "/api/auth/login",
            "/api/auth/web-login",
            "/api/auth/validate",
            "/api/auth/change-password",
            "/api/auth/change-password-first-login"
    ));

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        // Remove context path if present
        String contextPath = request.getContextPath();
        if (path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !isExcludedPath(path)) {
            // Check if user must change password via session attributes
            Boolean mustChangePassword = (Boolean) request.getSession().getAttribute("mustChangePassword");
            Boolean isFirstLogin = (Boolean) request.getSession().getAttribute("isFirstLogin");

            if ((mustChangePassword != null && mustChangePassword) ||
                    (isFirstLogin != null && isFirstLogin)) {
                response.sendRedirect(request.getContextPath() + "/change-password?firstLogin=true");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }
}