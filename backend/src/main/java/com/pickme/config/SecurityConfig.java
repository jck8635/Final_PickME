package com.pickme.config;

import java.io.IOException;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pickme.member.DeletedMemberFilter;
import com.pickme.member.LegacyAwarePasswordEncoder;
import com.pickme.member.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserRepository users) throws Exception {

        http
            .csrf(csrf -> csrf.spa())
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            .addFilterBefore(
                    new DeletedMemberFilter(users),
                    AuthorizationFilter.class)
            .cors(Customizer.withDefaults())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/admin/auth/me").permitAll()
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/admin/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/health",
                        "/api/ai/health",
                        "/api/auth/me").permitAll()
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/logout").permitAll()
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/chat",
                        "/api/decisions/consult",
                        "/api/decisions/restyle",
                        "/api/decisions/questions").permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/decisions/popular",
                        "/api/community/**").permitAll()
                .requestMatchers(
                        "/actuator/health",
                        "/error").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    writeJson(
                            res,
                            HttpStatus.UNAUTHORIZED,
                            "로그인이 필요합니다."))
                .accessDeniedHandler((req, res, e) ->
                    writeJson(
                            res,
                            HttpStatus.FORBIDDEN,
                            "권한이 없거나 요청이 만료되었습니다. 로그인 상태와 권한을 확인해주세요.")));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new LegacyAwarePasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(PickmeProperties props) {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(props.corsAllowedOrigins());
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", config);

        return source;
    }

    private static void writeJson(
            HttpServletResponse res,
            HttpStatus status,
            String message) throws IOException {

        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {

            CsrfToken token =
                    (CsrfToken) request.getAttribute(CsrfToken.class.getName());

            if (token != null) {
                token.getToken();
            }

            chain.doFilter(request, response);
        }
    }
}
