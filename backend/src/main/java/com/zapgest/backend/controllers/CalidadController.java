package com.zapgest.backend.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.services.CalidadService;

@RestController
@RequestMapping("/api/calidades")
@CrossOrigin(origins = "*")
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
}