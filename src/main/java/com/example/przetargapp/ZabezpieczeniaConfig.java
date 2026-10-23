package com.example.przetargapp;

import com.example.przetargapp.user.Rola;
import com.example.przetargapp.user.User;
import com.example.przetargapp.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class ZabezpieczeniaConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository userRepository) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                            } else {
                                response.sendRedirect("/Logowanie.html");
                            }
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/users/rejestracja", "/api/users/login").permitAll()
                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers(
                                "/PanelAdministratora.html",
                                "/api/users/admin/**",
                                "/api/zrodla/**",
                                "/api/slowa-kluczowe/**",
                                "/api/analytics/**"
                        ).hasRole("ADMIN")
                        .requestMatchers("/PanelPrzetargow.html").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new SessionUserAuthenticationFilter(userRepository), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static final class SessionUserAuthenticationFilter extends OncePerRequestFilter {
        private final UserRepository userRepository;

        private SessionUserAuthenticationFilter(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UUID userId = sessionUserId(request);
                if (userId != null) {
                    userRepository.findById(userId).ifPresent(this::authenticate);
                }
            }
            filterChain.doFilter(request, response);
        }

        private UUID sessionUserId(HttpServletRequest request) {
            var session = request.getSession(false);
            if (session == null) {
                return null;
            }
            Object value = session.getAttribute("userId");
            if (value instanceof UUID id) {
                return id;
            }
            return null;
        }

        private void authenticate(User user) {
            String authority = user.getRola() == Rola.ADMIN ? "ROLE_ADMIN" : "ROLE_USER";
            var authentication = new UsernamePasswordAuthenticationToken(
                    user.getId().toString(), null, List.of(new SimpleGrantedAuthority(authority)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}
