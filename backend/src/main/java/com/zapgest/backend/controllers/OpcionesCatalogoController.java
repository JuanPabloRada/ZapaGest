package com.zapgest.backend.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.catalogo.OpcionResponse;
import com.zapgest.backend.services.CatalogoService;

// Listas para los desplegables del catalogo (requieren token, cualquier rol):
// GET /api/colores -> colores ya guardados del negocio
// GET /api/tallas  -> tallas europeas permitidas (34 a 46)
@RestController
@RequestMapping("/api")
public class OpcionesCatalogoController {

    private final CatalogoService service;

    public OpcionesCatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping("/colores")
    public List<OpcionResponse> colores() {
        return service.listarColores();
    }

    @GetMapping("/tallas")
    public List<OpcionResponse> tallas() {
        return service.listarTallas();
    }
}
