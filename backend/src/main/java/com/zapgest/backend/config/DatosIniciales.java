package com.zapgest.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.repositories.UsuarioRepository;

// Se ejecuta una vez cada vez que arranca el backend.
// Si todavia no existe ningun Dueño, lo crea.
@Component
public class DatosIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${DUENO_PASSWORD:}")
    private String passwordDueno;

    public DatosIniciales(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (repository.existsByRol(Rol.DUENO)) {
            return;
        }
        if (passwordDueno == null || passwordDueno.length() < 6) {
            throw new IllegalStateException("DUENO_PASSWORD debe tener al menos 6 caracteres.");
        }
        Usuario dueno = new Usuario();
        dueno.setNegocioId(1L);
        dueno.setNombre("Dueño");
        dueno.setUsername("dueno");
        dueno.setPasswordHash(passwordEncoder.encode(passwordDueno));
        dueno.setRol(Rol.DUENO);
        dueno.setActivo(true);
        repository.save(dueno);
        log.info("Se creo el primer Dueño con usuario 'dueno'.");
    }
}
