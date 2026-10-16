package com.zapgest.backend.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.dto.CambiarEstadoUbicacionRequest;
import com.zapgest.backend.dto.EditarNombreRequest;
import com.zapgest.backend.services.UbicacionService;

@RestController
@RequestMapping("/api/ubicaciones")
public class UbicacionController {

    private final UbicacionService service;

    public UbicacionController(UbicacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<Ubicacion> listar() {
        return service.listar();
    }

    @PostMapping
    public Ubicacion crear(@RequestBody Ubicacion ubicacion) {
        return service.crear(ubicacion);
    }

    @PatchMapping("/{id}")
    public Ubicacion cambiarEstado(
            @PathVariable Long id,
            @RequestBody CambiarEstadoUbicacionRequest datos) {
        return service.cambiarEstado(id, datos.activa());
    }

    // Editar el nombre (DUENO o ADMIN). No cambia el estado ni cuenta para el limite de 10.
    @PutMapping("/{id}")
    public Ubicacion editar(@PathVariable Long id, @RequestBody EditarNombreRequest datos) {
        return service.editar(id, datos == null ? null : datos.nombre());
    }
}
