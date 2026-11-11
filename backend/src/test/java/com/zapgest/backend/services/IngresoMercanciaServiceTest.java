package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.zapgest.backend.dto.inventario.IngresoRequest;
import com.zapgest.backend.dto.inventario.IngresoResponse;
import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.UbicacionRepository;
import com.zapgest.backend.repositories.UsuarioRepository;

// HU-07: el ingreso de mercancia une variante + stock + movimiento. Todo simulado.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngresoMercanciaServiceTest {

    @Mock
    private VarianteService varianteService;
    @Mock
    private InventarioService inventarioService;
    @Mock
    private MovimientoService movimientoService;
    @Mock
    private UbicacionRepository ubicacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private IngresoMercanciaService service;
    private Ubicacion bodega;
    private Usuario admin;
    private Variante variante;

    @BeforeEach
    void preparar() {
        service = new IngresoMercanciaService(varianteService, inventarioService, movimientoService,
                ubicacionRepository, usuarioRepository);

        bodega = new Ubicacion();
        bodega.setId(2L);
        bodega.setNegocioId(1L);
        bodega.setNombre("Bodega 1");
        bodega.setActiva(true);

        admin = new Usuario();
        admin.setId(3L);
        admin.setUsername("admin1");
        admin.setRol(Rol.ADMIN);

        variante = new Variante();
        variante.setId(50L);

        when(ubicacionRepository.findByIdAndNegocioId(2L, 1L)).thenReturn(Optional.of(bodega));
        when(usuarioRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));
        when(varianteService.obtenerOCrear(1L, 10L, 20L, 30L)).thenReturn(variante);
        when(inventarioService.sumarStock(50L, 2L, 12)).thenReturn(12);
    }

    private static IngresoRequest ingreso(Integer cantidad) {
        return new IngresoRequest(2L, 1L, 10L, 20L, 30L, cantidad);
    }

    @Test
    void ingresaYDevuelveLaVarianteYElStockEnLaUbicacion() {
        IngresoResponse r = service.ingresar(ingreso(12), "admin1");

        assertThat(r.varianteId()).isEqualTo(50L);
        assertThat(r.stockEnUbicacion()).isEqualTo(12);

        // Orden: primero la variante, luego el stock, al final el movimiento con quien lo hizo
        InOrder orden = inOrder(varianteService, inventarioService, movimientoService);
        orden.verify(varianteService).obtenerOCrear(1L, 10L, 20L, 30L);
        orden.verify(inventarioService).sumarStock(50L, 2L, 12);
        orden.verify(movimientoService).registrarEntrada(variante, bodega, 12, admin);
    }

    @Test
    void siYaHabiaStockDevuelveElTotalAcumulado() {
        when(inventarioService.sumarStock(50L, 2L, 12)).thenReturn(20); // ya habia 8

        assertThat(service.ingresar(ingreso(12), "admin1").stockEnUbicacion()).isEqualTo(20);
    }

    @Test
    void rechazaCantidadCeroNegativaOVacia() {
        for (Integer cantidad : new Integer[] { 0, -5, null }) {
            assertThatThrownBy(() -> service.ingresar(ingreso(cantidad), "admin1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La cantidad debe ser mayor que cero.");
        }
        verifyNoInteractions(varianteService, inventarioService, movimientoService);
    }

    @Test
    void rechazaCantidadExagerada() {
        assertThatThrownBy(() -> service.ingresar(ingreso(IngresoMercanciaService.MAX_CANTIDAD + 1), "admin1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La cantidad no puede superar 10000 pares por ingreso.");
        verifyNoInteractions(varianteService, inventarioService, movimientoService);
    }

    @Test
    void ubicacionInexistenteDa404SinCrearVariante() {
        when(ubicacionRepository.findByIdAndNegocioId(2L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingresar(ingreso(12), "admin1"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró la ubicación.");
        verifyNoInteractions(varianteService, inventarioService, movimientoService);
    }

    @Test
    void ubicacionInactivaNoCreaVarianteNiStock() {
        bodega.setActiva(false);

        assertThatThrownBy(() -> service.ingresar(ingreso(12), "admin1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La ubicación está inactiva.");
        verifyNoInteractions(varianteService, inventarioService, movimientoService);
    }

    @Test
    void sinUbicacionDa400() {
        assertThatThrownBy(() -> service.ingresar(new IngresoRequest(null, 1L, 10L, 20L, 30L, 5), "admin1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes elegir la ubicación.");
    }

    @Test
    void siLaVarianteNoEsValidaNoSeTocaElStockNiElMovimiento() {
        when(varianteService.obtenerOCrear(1L, 99L, 20L, 30L))
                .thenThrow(new IllegalArgumentException("El color no pertenece a esta referencia."));

        assertThatThrownBy(() -> service.ingresar(new IngresoRequest(2L, 1L, 99L, 20L, 30L, 12), "admin1"))
                .hasMessage("El color no pertenece a esta referencia.");
        verify(inventarioService, never()).sumarStock(anyLong(), anyLong(), anyInt());
        verify(movimientoService, never()).registrarEntrada(any(), any(), anyInt(), any());
    }
}
