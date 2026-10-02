package com.zapgest.backend.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.services.UbicacionService;

@RestController
@RequestMapping("/api/ubicaciones")
@CrossOrigin(origins = "*")
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
}
