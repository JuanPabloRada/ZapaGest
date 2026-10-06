package com.zapgest.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.repositories.UsuarioRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            autenticar(authorization.substring(7));
        }
        filterChain.doFilter(request, response);
    }

    private void autenticar(String token) {
        try {
            String username = jwtService.extraerUsername(token);
            Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
            if (usuario == null || !Boolean.TRUE.equals(usuario.getActivo())) {
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    usuario.getUsername(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }
    }
}
