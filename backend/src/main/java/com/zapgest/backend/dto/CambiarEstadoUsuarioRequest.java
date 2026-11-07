package com.zapgest.backend.dto;

// PATCH /api/usuarios/{id}: {"activo": true} o {"activo": false}
public record CambiarEstadoUsuarioRequest(Boolean activo) {
}
