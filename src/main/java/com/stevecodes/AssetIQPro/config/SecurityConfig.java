package com.stevecodes.AssetIQPro.config;

import com.stevecodes.AssetIQPro.filter.EdgeCompatibilityFilter;
import com.stevecodes.AssetIQPro.filter.FirstLoginFilter;
import com.stevecodes.AssetIQPro.filter.JwtAuthenticationFilter;
import com.stevecodes.AssetIQPro.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final FirstLoginFilter firstLoginFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/**",
                                "/transfers/**",
                                "/assets/**",
                                "/infra-requests/**",
                                "/bookings/**",
                                "/vouchers/**",
                                "/admin/**"
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/register",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/error",
                                "/assets/create",
                                "/change-password",
                                "/api/auth/**",
                                "/api/public/**",
                                "/transfer/sign/**",
                                "/transfers/sign/**",
                                "/transfers/create",
                                "/transfers/debug-transfer/**",
                                "/transfers/test-signing/**",
                                "/transfers/thankyou",
                                "/transfers/sign-error",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**",
                                "/admin/categories",
                                "/admin/companies",
                                "/admin/departments",
                                "/admin/employees",
                                "/admin/locations",
                                "/admin/suppliers",
                                "/admin/rooms",
                                "/admin/permissions",
                                "/admin/users",
                                "/bookings/rooms",
                                "/bookings/driver-requests",
                                "/bookings/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .addFilterBefore(new EdgeCompatibilityFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(firstLoginFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}