package com.zapgest.backend.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRol(Rol rol);
}
