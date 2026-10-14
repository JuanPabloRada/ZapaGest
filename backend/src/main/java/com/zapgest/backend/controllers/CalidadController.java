package com.zapgest.backend.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.EditarNombreRequest;
import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.services.CalidadService;

@RestController
@RequestMapping("/api/calidades")
public class CalidadController {

    private final CalidadService service;

    public CalidadController(CalidadService service) {
        this.service = service;
    }

    @GetMapping
    public List<Calidad> listar() {
        return service.listar();
    }

    @PostMapping
    public Calidad crear(@RequestBody Calidad calidad) {
        return service.crear(calidad);
    }

    // Editar el nombre (solo DUENO, ver SeguridadConfig)
    @PutMapping("/{id}")
    public Calidad editar(@PathVariable Long id, @RequestBody EditarNombreRequest datos) {
        return service.editar(id, datos == null ? null : datos.nombre());
    }
}