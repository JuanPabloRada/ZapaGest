package com.zapgest.backend.controllers;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.LoginRequest;
import com.zapgest.backend.dto.UsuarioResponse;
import com.zapgest.backend.services.UsuarioService;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UsuarioService service;

    public AuthController(UsuarioService service) {
        this.service = service;
    }

    // Si el usuario y la contraseña son correctos devuelve sus datos (con su rol)
    @PostMapping("/login")
    public UsuarioResponse login(@RequestBody LoginRequest datos) {
        return service.login(datos);
    }
}
