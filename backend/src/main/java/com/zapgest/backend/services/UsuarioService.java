package com.zapgest.backend.services;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.zapgest.backend.dto.CrearUsuarioRequest;
import com.zapgest.backend.dto.LoginRequest;
import com.zapgest.backend.dto.UsuarioResponse;
import com.zapgest.backend.exceptions.CredencialesInvalidasException;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.repositories.UsuarioRepository;

@Service
public class UsuarioService {

    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1
    private static final int MIN_PASSWORD = 6;

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioResponse> listar() {
        return repository.findAll().stream().map(UsuarioResponse::desde).toList();
    }

    public UsuarioResponse crear(CrearUsuarioRequest datos) {
        if (vacio(datos.nombre())) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }
        if (vacio(datos.username())) {
            throw new IllegalArgumentException("El usuario es obligatorio.");
        }
        if (datos.password() == null || datos.password().length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + MIN_PASSWORD + " caracteres.");
        }
        if (datos.rol() == null) {
            throw new IllegalArgumentException("El rol es obligatorio (DUENO, ADMIN o VENDEDOR).");
        }
        String username = datos.username().trim().toLowerCase();
        if (repository.existsByUsername(username)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre de usuario.");
        }

        Usuario usuario = new Usuario();
        usuario.setNegocioId(NEGOCIO_ID);
        usuario.setNombre(datos.nombre().trim());
        usuario.setUsername(username);
        usuario.setPasswordHash(passwordEncoder.encode(datos.password()));
        usuario.setRol(datos.rol());
        usuario.setActivo(true);
        return UsuarioResponse.desde(repository.save(usuario));
    }

    public UsuarioResponse login(LoginRequest datos) {
        if (vacio(datos.username()) || vacio(datos.password())) {
            throw new CredencialesInvalidasException();
        }
        Usuario usuario = repository.findByUsername(datos.username().trim().toLowerCase())
                .orElseThrow(CredencialesInvalidasException::new);

        // Mismo mensaje para todo: asi nadie sabe si fallo el usuario o la contraseña
        if (!Boolean.TRUE.equals(usuario.getActivo())
                || !passwordEncoder.matches(datos.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        return UsuarioResponse.desde(usuario);
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
