package com.zapgest.backend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.catalogo.CatalogoRequest;
import com.zapgest.backend.dto.catalogo.CatalogoResponse;
import com.zapgest.backend.dto.catalogo.OpcionesReferenciaResponse;
import com.zapgest.backend.services.CatalogoService;

// HU-05: GET, POST y PUT /api/catalogo (requieren token; ver SeguridadConfig)
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping
    public List<CatalogoResponse> listar() {
        return service.listar();
    }

    // HU-07: colores, tallas y calidades de una referencia (sin costos). DUENO o ADMIN
    @GetMapping("/{id}/opciones")
    public OpcionesReferenciaResponse opciones(@PathVariable Long id) {
        return service.opciones(id);
    }

    @PostMapping
    public ResponseEntity<CatalogoResponse> crear(@RequestBody CatalogoRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(datos));
    }

    // Editar una referencia: mismo cuerpo que el POST (DUENO o ADMIN)
    @PutMapping("/{id}")
    public CatalogoResponse editar(@PathVariable Long id, @RequestBody CatalogoRequest datos) {
        return service.editar(id, datos);
    }
}
