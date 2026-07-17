package com.gympal.common;

import com.gympal.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,https://gympal-frontend.vercel.app}")
    private String allowedOrigins;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

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
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/signup", "/auth/signup",
                    "/api/v1/auth/login", "/auth/login",
                    "/api/v1/auth/refresh", "/auth/refresh",
                    "/api/v1/auth/forgot-password", "/auth/forgot-password",
                    "/api/v1/auth/reset-password", "/auth/reset-password",
                    "/api/v1/auth/oauth/google", "/auth/oauth/google",
                    "/api/v1/attendance/punches", "/attendance/punches",
                    "/api/v1/webhooks/cron/expiry-reminders", "/webhooks/cron/expiry-reminders",
                    "/iclock/**"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            String origin = request.getHeader("Origin");
            CorsConfiguration configuration = new CorsConfiguration();
            
            if (origin != null && (
                origin.endsWith(".vercel.app") || 
                origin.startsWith("http://localhost:") || 
                origin.startsWith("http://127.0.0.1:")
            )) {
                configuration.setAllowedOrigins(Collections.singletonList(origin));
            } else {
                java.util.List<String> origins = java.util.Arrays.asList(allowedOrigins.split(","));
                configuration.setAllowedOrigins(origins);
            }
            
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "X-Webhook-Secret"));
            configuration.setExposedHeaders(Collections.singletonList("Authorization"));
            configuration.setAllowCredentials(true);
            return configuration;
        };
    }
}
