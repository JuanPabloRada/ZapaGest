package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.repositories.InventarioRepository;
import com.zapgest.backend.repositories.UbicacionRepository;
import com.zapgest.backend.repositories.VarianteRepository;

// Traslados: InventarioService.restarStock y existenciasEn (sin base de datos).
// La prueba con despachos simultaneos de verdad esta en TrasladoConcurrenciaTest (necesita PostgreSQL).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventarioRestarTest {

    @Mock
    private InventarioRepository inventarioRepository;
    @Mock
    private VarianteRepository varianteRepository;
    @Mock
    private UbicacionRepository ubicacionRepository;

    private InventarioService service;

    @BeforeEach
    void preparar() {
        service = new InventarioService(inventarioRepository, varianteRepository, ubicacionRepository);
    }

    @Test
    void siAlcanzaRestaYDevuelveLoQueQueda() {
        when(inventarioRepository.restarSiAlcanza(7L, 3L, 5)).thenReturn(1);
        when(inventarioRepository.cantidadEn(7L, 3L)).thenReturn(Optional.of(19));

        assertThat(service.restarStock(7L, 3L, 5)).isEqualTo(19);
    }

    @Test
    void siNoAlcanzaAvisaCuantasHay() {
        when(inventarioRepository.restarSiAlcanza(7L, 3L, 30)).thenReturn(0);
        when(inventarioRepository.cantidadEn(7L, 3L)).thenReturn(Optional.of(24));

        assertThatThrownBy(() -> service.restarStock(7L, 3L, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No hay suficientes pares en el origen: hay 24.");
    }

    @Test
    void unaCantidadCeroNoTocaLaBaseDeDatos() {
        assertThatThrownBy(() -> service.restarStock(7L, 3L, 0))
                .hasMessage("La cantidad debe ser mayor que cero.");
        verify(inventarioRepository, never()).restarSiAlcanza(anyLong(), anyLong(), anyInt());
    }

    @Test
    void existenciasDeUnaUbicacionQueNoExisteDa404() {
        when(ubicacionRepository.findByIdAndNegocioId(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.existenciasEn(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
