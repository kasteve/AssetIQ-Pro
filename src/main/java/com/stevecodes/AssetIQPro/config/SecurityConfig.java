package com.stevecodes.AssetIQPro.config;

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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
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
                    HttpSession session = request.getSession();
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
                }

                response.sendRedirect("/assetIQ-pro/dashboard");
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/error",
                                "/api/auth/**",
                                "/api/public/**",
                                "/transfer/sign/**",
                                "/transfers/sign/**",
                                // ============================================
                                // INFRASTRUCTURE REQUEST SIGN PAGES - PUBLIC
                                // ============================================
                                "/infra-requests/sign",
                                "/infra-requests/sign-thankyou",
                                "/infra-requests/sign-error",
                                // ============================================
                                // RESOURCE REQUEST SIGN PAGES - PUBLIC
                                // ============================================
                                "/resources/sign",
                                "/resources/sign-thankyou",
                                "/resources/sign-error",
                                // ============================================
                                // SLOT REQUEST PAGES - PUBLIC
                                // ============================================
                                "/bookings/slot-request/**",
                                // ============================================
                                // SERVER ROOM PAGES - PUBLIC
                                // ============================================
                                "/bookings/server-room/**",
                                "/bookings/server-room/sign-out",
                                "/bookings/server-room/signout-thankyou",
                                "/bookings/server-room/signout-error",
                                "/bookings/server-room/thankyou",
                                "/bookings/server-room/error",
                                // ============================================
                                // TRANSFER SIGN PAGES - PUBLIC
                                // ============================================
                                "/transfers/thankyou",
                                "/transfers/error",
                                // ============================================
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**",
                                "/bookings/driver-dashboard",
                                "/bookings/driver/**",
                                "/bookings/driver-requests",
                                "/uploads/**"
                        ).permitAll()
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