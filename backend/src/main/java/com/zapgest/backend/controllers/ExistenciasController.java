package com.zapgest.backend.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.inventario.ExistenciaResponse;
import com.zapgest.backend.services.InventarioService;

// GET /api/inventario/existencias?ubicacionId= : que hay en una ubicacion, para elegir que trasladar.
// Solo DUENO y ADMIN (ver SeguridadConfig). Va aparte para no tocar ConsultaInventarioController.
@RestController
@RequestMapping("/api/inventario/existencias")
public class ExistenciasController {

    private final InventarioService service;

    public ExistenciasController(InventarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExistenciaResponse> existencias(@RequestParam(required = false) Long ubicacionId) {
        return service.existenciasEn(ubicacionId);
    }
}
