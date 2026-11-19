package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.zapgest.backend.dto.CrearUsuarioRequest;
import com.zapgest.backend.dto.UsuarioResponse;
import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.repositories.UsuarioRepository;

// HU-01: reglas del servicio de usuarios (alta y activar/desactivar). Repositorio simulado.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService service;

    @BeforeEach
    void preparar() {
        service = new UsuarioService(repository, passwordEncoder);
        when(repository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hash");
    }

    private Usuario usuario(Long id, String username, Rol rol, boolean activo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNegocioId(1L);
        u.setNombre("Nombre " + username);
        u.setUsername(username);
        u.setRol(rol);
        u.setActivo(activo);
        when(repository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    // ---------- activar / desactivar ----------

    @Test
    void elDuenoDesactivaAUnVendedor() {
        usuario(5L, "sandra", Rol.VENDEDOR, true);

        UsuarioResponse r = service.cambiarEstado(5L, false, "dueno");

        assertThat(r.activo()).isFalse();
        assertThat(r.username()).isEqualTo("sandra");
        verify(repository).save(any(Usuario.class));
    }

    @Test
    void elDuenoReactivaAUnUsuario() {
        usuario(5L, "sandra", Rol.VENDEDOR, false);

        assertThat(service.cambiarEstado(5L, true, "dueno").activo()).isTrue();
    }

    @Test
    void elDuenoNoPuedeDesactivarseASiMismo() {
        usuario(1L, "dueno", Rol.DUENO, true);

        assertThatThrownBy(() -> service.cambiarEstado(1L, false, "dueno"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes desactivar tu propio usuario.");
        verify(repository, never()).save(any());
    }

    @Test
    void sinCampoActivoDa400() {
        usuario(5L, "sandra", Rol.VENDEDOR, true);

        assertThatThrownBy(() -> service.cambiarEstado(5L, null, "dueno"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes indicar si el usuario queda activo o inactivo.");
        verify(repository, never()).save(any());
    }

    @Test
    void usuarioInexistenteDa404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(99L, false, "dueno"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró el usuario.");
    }

    @Test
    void usuarioDeOtroNegocioSeTrataComoInexistente() {
        Usuario otro = usuario(7L, "ajeno", Rol.VENDEDOR, true);
        otro.setNegocioId(2L);

        assertThatThrownBy(() -> service.cambiarEstado(7L, false, "dueno"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    // ---------- alta (pendiente del Sprint 1) ----------

    @Test
    void creaUsuarioActivoConContrasenaCifradaYUsernameEnMinuscula() {
        UsuarioResponse r = service.crear(new CrearUsuarioRequest("Ana", "  ANA ", "secreto1", Rol.VENDEDOR));

        assertThat(r.username()).isEqualTo("ana");
        assertThat(r.activo()).isTrue();
        verify(passwordEncoder).encode("secreto1");
    }

    @Test
    void rechazaCamposObligatoriosVaciosYContrasenaCorta() {
        assertThatThrownBy(() -> service.crear(new CrearUsuarioRequest(" ", "ana", "secreto1", Rol.VENDEDOR)))
                .hasMessage("El nombre es obligatorio.");
        assertThatThrownBy(() -> service.crear(new CrearUsuarioRequest("Ana", "", "secreto1", Rol.VENDEDOR)))
                .hasMessage("El usuario es obligatorio.");
        assertThatThrownBy(() -> service.crear(new CrearUsuarioRequest("Ana", "ana", "12345", Rol.VENDEDOR)))
                .hasMessage("La contraseña debe tener al menos 6 caracteres.");
        assertThatThrownBy(() -> service.crear(new CrearUsuarioRequest("Ana", "ana", "secreto1", null)))
                .hasMessage("El rol es obligatorio (DUENO, ADMIN o VENDEDOR).");
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaUsernameRepetido() {
        when(repository.existsByUsername("ana")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(new CrearUsuarioRequest("Ana", "Ana", "secreto1", Rol.VENDEDOR)))
                .hasMessage("Ya existe un usuario con ese nombre de usuario.");
        verify(repository, never()).save(any());
    }
}
