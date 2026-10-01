package com.zapgest.backend.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.repositories.CalidadRepository;

@Service
public class CalidadService {

    private final CalidadRepository repository;

    public CalidadService(CalidadRepository repository) {
        this.repository = repository;
    }

    public List<Calidad> listar() {
        return repository.findAll();
    }

    public Calidad crear(Calidad calidad) {
        calidad.setNegocioId(1L);
        return repository.save(calidad);
    }
}