package com.zapgest.backend.dto;

import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Usuario;

public record LoginResponse(String token, UsuarioAutenticado usuario) {

    public static LoginResponse desde(String token, Usuario usuario) {
        return new LoginResponse(token,
                new UsuarioAutenticado(usuario.getId(), usuario.getNombre(), usuario.getUsername(), usuario.getRol()));
    }

    public record UsuarioAutenticado(Long id, String nombre, String username, Rol rol) {
    }
}
