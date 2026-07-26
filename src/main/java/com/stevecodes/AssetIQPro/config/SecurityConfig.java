package com.stevecodes.AssetIQPro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Permission;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.security.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {

    private final CustomUserDetailsService userDetailsService;
    private final AppUserRepository userRepository;

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

                    java.util.List<String> permissionNames = user.getPermissions().stream()
                            .map(Permission::getPermissionName)
                            .collect(java.util.stream.Collectors.toList());
                    session.setAttribute("permissionNames", permissionNames);

                    System.out.println("User: " + username + " has permissions: " + permissionNames);
                    System.out.println("Session ID: " + session.getId());
                }

                // ✅ Use request.getContextPath() dynamically
                String contextPath = request.getContextPath();
                response.sendRedirect(contextPath + "/dashboard");
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionFixation(sessionFixation -> sessionFixation.newSession())
                        .maximumSessions(10)
                        .expiredUrl("/login?expired=true")
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
                                // ✅ Use request.getContextPath() for login redirect
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
                                "/bookings/server-room/sign-out",
                                "/bookings/server-room/signout-thankyou",
                                "/bookings/server-room/signout-error",
                                "/bookings/server-room/thankyou",
                                "/bookings/server-room/error",
                                "/transfers/thankyou",
                                "/transfers/error",
                                "/swagger-ui/**",
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
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        return http.build();
    }
}