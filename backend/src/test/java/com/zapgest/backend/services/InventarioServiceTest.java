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
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.InventarioRepository;
import com.zapgest.backend.repositories.UbicacionRepository;
import com.zapgest.backend.repositories.VarianteRepository;

// HU-07: reglas de InventarioService.sumarStock (sin base de datos; los repositorios se simulan).
// La prueba con ingresos simultaneos de verdad esta en InventarioConcurrenciaTest (necesita PostgreSQL).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventarioServiceTest {

    private static final Long VARIANTE = 7L, BODEGA = 3L;

    @Mock
    private InventarioRepository inventarioRepository;
    @Mock
    private VarianteRepository varianteRepository;
    @Mock
    private UbicacionRepository ubicacionRepository;

    private InventarioService service;
    private Variante variante;
    private Ubicacion ubicacion;

    @BeforeEach
    void preparar() {
        service = new InventarioService(inventarioRepository, varianteRepository, ubicacionRepository);

        variante = new Variante();
        variante.setId(VARIANTE);
        variante.setNegocioId(1L);

        ubicacion = new Ubicacion();
        ubicacion.setId(BODEGA);
        ubicacion.setNegocioId(1L);
        ubicacion.setNombre("Bodega");
        ubicacion.setActiva(true);

        when(varianteRepository.findById(VARIANTE)).thenReturn(Optional.of(variante));
        when(ubicacionRepository.findByIdAndNegocioId(BODEGA, 1L)).thenReturn(Optional.of(ubicacion));
        when(inventarioRepository.cantidadEn(VARIANTE, BODEGA)).thenReturn(Optional.of(15));
    }

    @Test
    void sumaElStockYDevuelveLaCantidadQueQuedaEnLaUbicacion() {
        int resultado = service.sumarStock(VARIANTE, BODEGA, 5);

        assertThat(resultado).isEqualTo(15);
        verify(inventarioRepository).sumar(1L, VARIANTE, BODEGA, 5);
    }

    @Test
    void rechazaCantidadCeroONegativa() {
        for (int cantidad : new int[] { 0, -1, -50 }) {
            assertThatThrownBy(() -> service.sumarStock(VARIANTE, BODEGA, cantidad))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La cantidad debe ser mayor que cero.");
        }
        verify(inventarioRepository, never()).sumar(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void rechazaUbicacionInactiva() {
        ubicacion.setActiva(false);

        assertThatThrownBy(() -> service.sumarStock(VARIANTE, BODEGA, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La ubicación está inactiva.");
        verify(inventarioRepository, never()).sumar(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void ubicacionInexistenteODeOtroNegocioDa404() {
        // findByIdAndNegocioId ya filtra por negocio: una ubicacion de otro negocio llega como vacia
        when(ubicacionRepository.findByIdAndNegocioId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sumarStock(VARIANTE, 99L, 5))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró la ubicación.");
        verify(inventarioRepository, never()).sumar(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void varianteInexistenteDa404() {
        when(varianteRepository.findById(777L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sumarStock(777L, BODEGA, 5))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró la variante.");
        verify(inventarioRepository, never()).sumar(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void varianteDeOtroNegocioDa404() {
        variante.setNegocioId(2L);

        assertThatThrownBy(() -> service.sumarStock(VARIANTE, BODEGA, 5))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró la variante.");
        verify(inventarioRepository, never()).sumar(anyLong(), anyLong(), anyLong(), anyInt());
    }
}
