package com.zapgest.backend.controllers;

import java.util.List;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.CambiarEstadoUsuarioRequest;
import com.zapgest.backend.dto.CrearUsuarioRequest;
import com.zapgest.backend.dto.UsuarioResponse;
import com.zapgest.backend.services.UsuarioService;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return service.listar();
    }

    @PostMapping
    public UsuarioResponse crear(@RequestBody CrearUsuarioRequest datos) {
        return service.crear(datos);
    }

    // Activar o desactivar: {"activo": true} o {"activo": false}. Solo DUENO.
    // principal.getName() es el usuario que hace la peticion (sale del token).
    @PatchMapping("/{id}")
    public UsuarioResponse cambiarEstado(@PathVariable Long id, @RequestBody CambiarEstadoUsuarioRequest datos,
            Principal principal) {
        return service.cambiarEstado(id, datos.activo(), principal.getName());
    }
}
