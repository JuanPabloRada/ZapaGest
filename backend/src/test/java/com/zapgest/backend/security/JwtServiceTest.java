package com.zapgest.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Usuario;

class JwtServiceTest {

    @Test
    void issuedTokenCanBeParsedBackToUsername() {
        String testKey = Base64.getEncoder().encodeToString(new byte[32]);
        JwtService jwtService = new JwtService(testKey);
        Usuario usuario = new Usuario();
        usuario.setUsername("juanp");
        usuario.setRol(Rol.ADMIN);

        String token = jwtService.emitir(usuario);

        assertEquals("juanp", jwtService.extraerUsername(token));
    }
}
