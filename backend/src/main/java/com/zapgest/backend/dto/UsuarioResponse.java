package com.zapgest.backend.dto;

import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Usuario;

// Lo que la API devuelve de un usuario. NO incluye la contraseña.
public record UsuarioResponse(Long id, String nombre, String username, Rol rol, Boolean activo) {

    public static UsuarioResponse desde(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getUsername(), u.getRol(), u.getActivo());
    }
}
