package com.zapgest.backend.controllers;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.traslado.TrasladoRequest;
import com.zapgest.backend.dto.traslado.TrasladoResponse;
import com.zapgest.backend.services.TrasladoService;

// Traslados entre ubicaciones: solo DUENO y ADMIN (ver SeguridadConfig).
// Contrato acordado con la pantalla de Angie (Frontend/js/traslados.js).
@RestController
@RequestMapping("/api/traslados")
public class TrasladoController {

    private final TrasladoService service;

    public TrasladoController(TrasladoService service) {
        this.service = service;
    }

    @GetMapping
    public List<TrasladoResponse> listar() {
        return service.listar();
    }

    @PostMapping
    public ResponseEntity<TrasladoResponse> solicitar(@RequestBody TrasladoRequest datos, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.solicitar(datos, principal.getName()));
    }

    @PatchMapping("/{id}/despachar")
    public TrasladoResponse despachar(@PathVariable Long id, Principal principal) {
        return service.despachar(id, principal.getName());
    }

    @PatchMapping("/{id}/recibir")
    public TrasladoResponse recibir(@PathVariable Long id, Principal principal) {
        return service.recibir(id, principal.getName());
    }

    @PatchMapping("/{id}/cancelar")
    public TrasladoResponse cancelar(@PathVariable Long id, Principal principal) {
        return service.cancelar(id, principal.getName());
    }
}
