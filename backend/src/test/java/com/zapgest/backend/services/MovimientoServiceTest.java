package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.zapgest.backend.models.Movimiento;
import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.TipoMovimiento;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.MovimientoRepository;

// HU-07: el ingreso de mercancia deja un movimiento ENTRADA con quien, donde, cuanto.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovimientoServiceTest {

    @Mock
    private MovimientoRepository movimientoRepository;

    private MovimientoService service;
    private Variante variante;
    private Ubicacion ubicacion;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        service = new MovimientoService(movimientoRepository);

        variante = new Variante();
        variante.setId(7L);
        ubicacion = new Ubicacion();
        ubicacion.setId(3L);
        usuario = new Usuario();
        usuario.setId(2L);
        usuario.setRol(Rol.ADMIN);

        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void registraUnaEntradaConVarianteUbicacionCantidadYUsuario() {
        Movimiento m = service.registrarEntrada(variante, ubicacion, 12, usuario);

        assertThat(m.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
        assertThat(m.getVariante()).isSameAs(variante);
        assertThat(m.getUbicacion()).isSameAs(ubicacion);
        assertThat(m.getCantidad()).isEqualTo(12);
        assertThat(m.getUsuario()).isSameAs(usuario);
        assertThat(m.getNegocioId()).isEqualTo(1L);
        verify(movimientoRepository).save(m);
    }

    @Test
    void rechazaCantidadCeroONegativa() {
        assertThatThrownBy(() -> service.registrarEntrada(variante, ubicacion, 0, usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La cantidad debe ser mayor que cero.");
        assertThatThrownBy(() -> service.registrarEntrada(variante, ubicacion, -3, usuario))
                .isInstanceOf(IllegalArgumentException.class);
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void rechazaDatosFaltantes() {
        assertThatThrownBy(() -> service.registrarEntrada(null, ubicacion, 5, usuario))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registrarEntrada(variante, null, 5, usuario))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registrarEntrada(variante, ubicacion, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(movimientoRepository, never()).save(any());
    }
}
