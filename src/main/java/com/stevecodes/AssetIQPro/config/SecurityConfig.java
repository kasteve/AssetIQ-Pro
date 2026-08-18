package com.stevecodes.AssetIQPro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Permission;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.repository.SystemSettingRepository;
import com.stevecodes.AssetIQPro.security.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.security.web.session.SimpleRedirectSessionInformationExpiredStrategy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {

    private final CustomUserDetailsService userDetailsService;
    private final AppUserRepository userRepository;
    private final SystemSettingRepository systemSettingRepository;

    private static final String KEY_SESSION_TIMEOUT = "SESSION_TIMEOUT";
    private static final int DEFAULT_SESSION_TIMEOUT_SECONDS = 600; // 10 minutes

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");

        registry.addResourceHandler("/uploads/disposal/policies/**")
                .addResourceLocations("file:./uploads/disposal/policies/");

        registry.addResourceHandler("/uploads/disposal/proofs/**")
                .addResourceLocations("file:./uploads/disposal/proofs/");

        registry.addResourceHandler("/uploads/infra/quotations/**")
                .addResourceLocations("file:./uploads/infra/quotations/");

        registry.addResourceHandler("/uploads/infra/reports/**")
                .addResourceLocations("file:./uploads/infra/reports/");

        registry.addResourceHandler("/uploads/invoices/**")
                .addResourceLocations("file:./uploads/invoices/");

        registry.addResourceHandler("/uploads/resources/reports/**")
                .addResourceLocations("file:./uploads/resources/reports/");
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                                Authentication authentication) throws java.io.IOException {
                String username = authentication.getName();
                AppUser user = userRepository.findByUsernameOrEmail(username, username).orElse(null);

                if (user != null) {
                    HttpSession session = request.getSession(true);
                    session.setAttribute("userId", user.getUserId());
                    session.setAttribute("username", user.getUsername());
                    session.setAttribute("fullName", user.getFullName());
                    session.setAttribute("role", user.getRole());
                    session.setAttribute("permissions", user.getPermissions());
                    session.setAttribute("isFirstLogin", user.isFirstLogin());
                    session.setAttribute("mustChangePassword", user.isMustChangePassword());
                    session.setAttribute("sessionCreatedAt", System.currentTimeMillis());

                    java.util.List<String> permissionNames = user.getPermissions().stream()
                            .map(Permission::getPermissionName)
                            .collect(java.util.stream.Collectors.toList());
                    session.setAttribute("permissionNames", permissionNames);

                    // ============================================================
                    // DEBUG: Check SESSION_TIMEOUT from database
                    // ============================================================
                    log.info("=========================================");
                    log.info("DEBUG: Checking SESSION_TIMEOUT from database");

                    int timeoutSeconds = DEFAULT_SESSION_TIMEOUT_SECONDS;
                    try {
                        Optional<String> timeoutValue = systemSettingRepository.findSettingValueByKey(KEY_SESSION_TIMEOUT);
                        log.info("findSettingValueByKey('SESSION_TIMEOUT') returned: {}", timeoutValue);

                        if (timeoutValue.isPresent()) {
                            String value = timeoutValue.get();
                            log.info("Raw value from database: '{}'", value);
                            try {
                                int timeoutMinutes = Integer.parseInt(value.trim());
                                timeoutSeconds = timeoutMinutes * 60;
                                log.info("✅ Parsed successfully: {} minutes = {} seconds", timeoutMinutes, timeoutSeconds);
                            } catch (NumberFormatException e) {
                                log.warn("⚠️ Failed to parse '{}' as integer: {}", value, e.getMessage());
                            }
                        } else {
                            log.warn("⚠️ SESSION_TIMEOUT not found in database, using default: {} seconds", DEFAULT_SESSION_TIMEOUT_SECONDS);
                        }
                    } catch (Exception e) {
                        log.error("❌ Error accessing SESSION_TIMEOUT: {}", e.getMessage());
                    }
                    log.info("=========================================");

                    session.setMaxInactiveInterval(timeoutSeconds);
                    session.setAttribute("sessionTimeoutSet", true);
                    session.setAttribute("sessionTimeoutSeconds", timeoutSeconds);

                    log.info("✅ User: {} logged in successfully", username);
                    log.info("🆔 Session ID: {}", session.getId());
                    log.info("⏱️ MaxInactiveInterval: {} seconds ({} minutes)",
                            session.getMaxInactiveInterval(),
                            session.getMaxInactiveInterval() / 60);
                    log.info("=========================================");
                }

                String contextPath = request.getContextPath();
                response.sendRedirect(contextPath + "/dashboard");
            }
        };
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SessionInformationExpiredStrategy sessionInformationExpiredStrategy() {
        return new SimpleRedirectSessionInformationExpiredStrategy("/login?expired=true");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/api/auth/**"),
                                new AntPathRequestMatcher("/api/public/**"),
                                new AntPathRequestMatcher("/admin/stock/api/public"),
                                new AntPathRequestMatcher("/transfer/sign/**"),
                                new AntPathRequestMatcher("/transfers/sign/**"),
                                new AntPathRequestMatcher("/infra-requests/sign"),
                                new AntPathRequestMatcher("/resources/sign"),
                                new AntPathRequestMatcher("/bookings/slot-request/**"),
                                new AntPathRequestMatcher("/bookings/server-room/**"),
                                new AntPathRequestMatcher("/api/resource-requests/**"),
                                new AntPathRequestMatcher("/api/infra-requests/**"),
                                new AntPathRequestMatcher("/admin/**"),
                                new AntPathRequestMatcher("/resources/**"),
                                new AntPathRequestMatcher("/bookings/room/**"),
                                new AntPathRequestMatcher("/bookings/driver-request/**"),
                                new AntPathRequestMatcher("/api/session/**")
                        )
                )
                .sessionManagement(session -> session
                        .sessionFixation(sessionFixation -> sessionFixation.newSession())
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(10)
                        .maxSessionsPreventsLogin(false)
                        .expiredSessionStrategy(sessionInformationExpiredStrategy())
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            Map<String, Object> errorResponse = new HashMap<>();
                            errorResponse.put("success", false);
                            errorResponse.put("error", "access_denied");
                            errorResponse.put("message", "You don't have permission to perform this action.");
                            errorResponse.put("timestamp", System.currentTimeMillis());
                            response.getWriter().write(new ObjectMapper().writeValueAsString(errorResponse));
                        })
                        .authenticationEntryPoint((request, response, authException) -> {
                            // Check if this is a session status API call
                            if (request.getRequestURI().contains("/api/session/")) {
                                response.setContentType("application/json");
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                Map<String, Object> errorResponse = new HashMap<>();
                                errorResponse.put("authenticated", false);
                                errorResponse.put("error", "unauthorized");
                                errorResponse.put("message", "Session expired. Please log in again.");
                                errorResponse.put("timestamp", System.currentTimeMillis());
                                response.getWriter().write(new ObjectMapper().writeValueAsString(errorResponse));
                                return;
                            }

                            if (request.getRequestURI().startsWith("/api/")) {
                                response.setContentType("application/json");
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                Map<String, Object> errorResponse = new HashMap<>();
                                errorResponse.put("success", false);
                                errorResponse.put("error", "unauthorized");
                                errorResponse.put("message", "You must be logged in to perform this action.");
                                errorResponse.put("timestamp", System.currentTimeMillis());
                                response.getWriter().write(new ObjectMapper().writeValueAsString(errorResponse));
                            } else {
                                response.sendRedirect(request.getContextPath() + "/login");
                            }
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/error",
                                "/forgot-password",
                                "/reset-password",
                                "/api/auth/**",
                                "/api/public/**",
                                "/api/session/**",
                                "/transfer/sign/**",
                                "/transfers/sign/**",
                                "/infra-requests/sign",
                                "/infra-requests/sign-thankyou",
                                "/infra-requests/sign-error",
                                "/resources/sign",
                                "/resources/sign-thankyou",
                                "/resources/sign-error",
                                "/bookings/slot-request/**",
                                "/bookings/server-room/**",
                                "/admin/stock/api/public",
                                "/bookings/server-room/sign-out",
                                "/bookings/server-room/signout-thankyou",
                                "/bookings/server-room/signout-error",
                                "/bookings/server-room/thankyou",
                                "/bookings/server-room/error",
                                "/transfers/thankyou",
                                "/transfers/error",
                                "/swagger-ui/**",
                                "/bookings/driver-rating/**",
                                "/admin/disposal/**",
                                "/v3/api-docs/**",
                                "/actuator/**",
                                "/uploads/**"
                        ).permitAll()
                        .requestMatchers(
                                "/assets/**",
                                "/transfers/**",
                                "/dashboard",
                                "/admin/**",
                                "/bookings/**",
                                "/infra-requests/**",
                                "/resources/**",
                                "/vouchers/**",
                                "/reports/**"
                        ).authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(authenticationSuccessHandler())
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "ASSETIQ_SESSION")
                        .permitAll()
                );

        return http.build();
    }
}