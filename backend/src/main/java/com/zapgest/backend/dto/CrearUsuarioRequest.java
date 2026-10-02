package com.zapgest.backend.dto;

import com.zapgest.backend.models.Rol;

// Lo que envia la pantalla para crear un usuario
public record CrearUsuarioRequest(String nombre, String username, String password, Rol rol) {
}
