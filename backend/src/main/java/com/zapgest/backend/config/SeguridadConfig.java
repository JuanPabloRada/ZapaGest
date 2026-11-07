package com.zapgest.backend.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.zapgest.backend.security.JwtAuthenticationFilter;
import com.zapgest.backend.security.RestAccessDeniedHandler;
import com.zapgest.backend.security.RestAuthenticationEntryPoint;

@Configuration
public class SeguridadConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // /error debe ser publico: si no, un error real (400/404/500) que Spring
                        // reenvia a /error llega como 401 y el frontend cierra la sesion.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/**").hasRole("DUENO")
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/**").hasRole("DUENO")
                        .requestMatchers(HttpMethod.PATCH, "/api/usuarios/**").hasRole("DUENO")
                        .requestMatchers(HttpMethod.GET, "/api/calidades/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/calidades/**").hasRole("DUENO")
                        .requestMatchers(HttpMethod.PUT, "/api/calidades/**").hasRole("DUENO")
                        .requestMatchers(HttpMethod.GET, "/api/ubicaciones/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/ubicaciones/**").hasAnyRole("DUENO", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/ubicaciones/**").hasAnyRole("DUENO", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/ubicaciones/**").hasAnyRole("DUENO", "ADMIN")
                        // Opciones para el ingreso de mercancia (HU-07): solo quien ingresa mercancia
                        .requestMatchers(HttpMethod.GET, "/api/catalogo/*/opciones").hasAnyRole("DUENO", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/catalogo/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/catalogo/**").hasAnyRole("DUENO", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/catalogo/**").hasAnyRole("DUENO", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
