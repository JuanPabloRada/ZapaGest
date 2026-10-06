package com.zapgest.backend.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.LoginRequest;
import com.zapgest.backend.dto.LoginResponse;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.security.JwtService;
import com.zapgest.backend.services.UsuarioService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioService service;
    private final JwtService jwtService;

    public AuthController(UsuarioService service, JwtService jwtService) {
        this.service = service;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest datos) {
        Usuario usuario = service.autenticar(datos);
        return LoginResponse.desde(jwtService.emitir(usuario), usuario);
    }
}
