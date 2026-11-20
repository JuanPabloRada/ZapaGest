package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.zapgest.backend.dto.traslado.TrasladoRequest;
import com.zapgest.backend.dto.traslado.TrasladoResponse;
import com.zapgest.backend.exceptions.EstadoNoPermitidoException;
import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.models.Color;
import com.zapgest.backend.models.EstadoTraslado;
import com.zapgest.backend.models.Referencia;
import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Talla;
import com.zapgest.backend.models.Traslado;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.InventarioRepository;
import com.zapgest.backend.repositories.TrasladoRepository;
import com.zapgest.backend.repositories.UbicacionRepository;
import com.zapgest.backend.repositories.UsuarioRepository;
import com.zapgest.backend.repositories.VarianteRepository;

// Traslados: reglas de solicitar, despachar, recibir y cancelar. Todo simulado (sin base de datos).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrasladoServiceTest {

    @Mock
    private TrasladoRepository trasladoRepository;
    @Mock
    private UbicacionRepository ubicacionRepository;
    @Mock
    private VarianteRepository varianteRepository;
    @Mock
    private InventarioRepository inventarioRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private InventarioService inventarioService;
    @Mock
    private MovimientoService movimientoService;

    private TrasladoService service;
    private Ubicacion bodega;
    private Ubicacion local;
    private Variante variante;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        service = new TrasladoService(trasladoRepository, ubicacionRepository, varianteRepository,
                inventarioRepository, usuarioRepository, inventarioService, movimientoService);

        bodega = ubicacion(8L, "Bodega 8", true);
        local = ubicacion(9L, "Local 9", true);

        Referencia ref = new Referencia();
        ref.setCodigo("AF1-001");
        ref.setNombre("Air Force");
        Color color = new Color();
        color.setNombre("Blanco");
        Talla talla = new Talla();
        talla.setValor(new BigDecimal("38.0"));
        Calidad calidad = new Calidad();
        calidad.setNombre("Original");

        variante = new Variante();
        variante.setId(101L);
        variante.setNegocioId(1L);
        variante.setReferencia(ref);
        variante.setColor(color);
        variante.setTalla(talla);
        variante.setCalidad(calidad);

        admin = new Usuario();
        admin.setId(3L);
        admin.setNombre("Juan");
        admin.setUsername("admin1");
        admin.setRol(Rol.ADMIN);

        when(ubicacionRepository.findByIdAndNegocioId(8L, 1L)).thenReturn(Optional.of(bodega));
        when(ubicacionRepository.findByIdAndNegocioId(9L, 1L)).thenReturn(Optional.of(local));
        when(varianteRepository.findById(101L)).thenReturn(Optional.of(variante));
        when(inventarioRepository.cantidadEn(101L, 8L)).thenReturn(Optional.of(24));
        when(usuarioRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));
        when(trasladoRepository.save(any(Traslado.class))).thenAnswer(i -> {
            Traslado t = i.getArgument(0);
            if (t.getId() == null) {
                t.setId(1L);
            }
            return t;
        });
    }

    private static Ubicacion ubicacion(Long id, String nombre, boolean activa) {
        Ubicacion u = new Ubicacion();
        u.setId(id);
        u.setNegocioId(1L);
        u.setNombre(nombre);
        u.setActiva(activa);
        return u;
    }

    private Traslado traslado(EstadoTraslado estado) {
        Traslado t = new Traslado();
        t.setId(1L);
        t.setNegocioId(1L);
        t.setOrigen(bodega);
        t.setDestino(local);
        t.setVariante(variante);
        t.setCantidad(5);
        t.setEstado(estado);
        t.setSolicitadoPor(admin);
        t.setSolicitadoEn(java.time.Instant.now());
        when(trasladoRepository.buscarParaCambiar(1L, 1L)).thenReturn(Optional.of(t));
        return t;
    }

    // ---------- solicitar ----------

    @Test
    void solicitarCreaElTrasladoSinMoverStock() {
        TrasladoResponse r = service.solicitar(new TrasladoRequest(8L, 9L, 101L, 5), "admin1");

        assertThat(r.estado()).isEqualTo("SOLICITADO");
        assertThat(r.cantidad()).isEqualTo(5);
        assertThat(r.origen()).isEqualTo("Bodega 8");
        assertThat(r.destino()).isEqualTo("Local 9");
        assertThat(r.referencia()).isEqualTo("AF1-001");
        assertThat(r.talla()).isEqualTo("38");
        assertThat(r.solicitadoPor()).isEqualTo("Juan");
        assertThat(r.solicitadoEn()).isNotNull();
        assertThat(r.despachadoEn()).isNull();
        verifyNoInteractions(inventarioService, movimientoService);
    }

    @Test
    void origenYDestinoIgualesSeRechazan() {
        assertThatThrownBy(() -> service.solicitar(new TrasladoRequest(8L, 8L, 101L, 5), "admin1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El origen y el destino deben ser distintos.");
        verify(trasladoRepository, never()).save(any());
    }

    @Test
    void cantidadCeroOSuperiorAlTopeSeRechaza() {
        assertThatThrownBy(() -> service.solicitar(new TrasladoRequest(8L, 9L, 101L, 0), "admin1"))
                .hasMessage("La cantidad debe ser mayor que cero.");
        assertThatThrownBy(() -> service.solicitar(new TrasladoRequest(8L, 9L, 101L, 10_001), "admin1"))
                .hasMessage("La cantidad no puede superar 10000 pares por traslado.");
    }

    @Test
    void noSeSolicitaMasDeLoQueHayEnElOrigen() {
        assertThatThrownBy(() -> service.solicitar(new TrasladoRequest(8L, 9L, 101L, 25), "admin1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No hay suficientes pares en el origen: hay 24.");
        verify(trasladoRepository, never()).save(any());
    }

    @Test
    void unOrigenInactivoNoParticipa() {
        bodega.setActiva(false);
        assertThatThrownBy(() -> service.solicitar(new TrasladoRequest(8L, 9L, 101L, 5), "admin1"))
                .hasMessage("La ubicación de origen está inactiva.");
    }

    @Test
    void unDestinoInactivoNoParticipa() {
        local.setActiva(false);
        assertThatThrownBy(() -> service.solicitar(new TrasladoRequest(8L, 9L, 101L, 5), "admin1"))
                .hasMessage("La ubicación de destino está inactiva.");
    }

    @Test
    void unaVarianteQueNoExisteDa404() {
        when(varianteRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.solicitar(new TrasladoRequest(8L, 9L, 999L, 5), "admin1"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- despachar ----------

    @Test
    void despacharSacaDelOrigenYDejaMovimientoNegativo() {
        traslado(EstadoTraslado.SOLICITADO);

        TrasladoResponse r = service.despachar(1L, "admin1");

        assertThat(r.estado()).isEqualTo("EN_MOVIMIENTO");
        assertThat(r.despachadoEn()).isNotNull();
        verify(inventarioService).restarStock(101L, 8L, 5);
        verify(movimientoService).registrarTraslado(variante, bodega, -5, admin);
        verify(inventarioService, never()).sumarStock(anyLong(), anyLong(), anyInt());
    }

    @Test
    void siYaNoAlcanzaAlDespacharNoCambiaElEstado() {
        Traslado t = traslado(EstadoTraslado.SOLICITADO);
        when(inventarioService.restarStock(101L, 8L, 5))
                .thenThrow(new IllegalArgumentException("No hay suficientes pares en el origen: hay 2."));

        assertThatThrownBy(() -> service.despachar(1L, "admin1"))
                .hasMessage("No hay suficientes pares en el origen: hay 2.");
        assertThat(t.getEstado()).isEqualTo(EstadoTraslado.SOLICITADO);
        verifyNoInteractions(movimientoService);
    }

    @Test
    void soloSeDespachaUnTrasladoSolicitado() {
        traslado(EstadoTraslado.EN_MOVIMIENTO);
        assertThatThrownBy(() -> service.despachar(1L, "admin1"))
                .isInstanceOf(EstadoNoPermitidoException.class)
                .hasMessage("Solo se puede despachar un traslado Solicitado.");
        verifyNoInteractions(inventarioService, movimientoService);
    }

    // ---------- recibir ----------

    @Test
    void recibirSumaEnElDestinoYDejaMovimientoPositivo() {
        traslado(EstadoTraslado.EN_MOVIMIENTO);

        TrasladoResponse r = service.recibir(1L, "admin1");

        assertThat(r.estado()).isEqualTo("LLEGO");
        assertThat(r.recibidoEn()).isNotNull();
        verify(inventarioService).sumarStock(101L, 9L, 5);
        verify(movimientoService).registrarTraslado(variante, local, 5, admin);
        verify(inventarioService, never()).restarStock(anyLong(), anyLong(), anyInt());
    }

    @Test
    void noSeRecibeUnTrasladoQueNoSeHaDespachado() {
        traslado(EstadoTraslado.SOLICITADO);
        assertThatThrownBy(() -> service.recibir(1L, "admin1"))
                .isInstanceOf(EstadoNoPermitidoException.class)
                .hasMessage("Solo se puede recibir un traslado En movimiento.");
    }

    @Test
    void unTrasladoQueYaLlegoNoSeRecibeDosVeces() {
        traslado(EstadoTraslado.LLEGO);
        assertThatThrownBy(() -> service.recibir(1L, "admin1"))
                .isInstanceOf(EstadoNoPermitidoException.class);
        verifyNoInteractions(inventarioService, movimientoService);
    }

    // ---------- cancelar ----------

    @Test
    void cancelarDesdeSolicitadoNoMueveStock() {
        traslado(EstadoTraslado.SOLICITADO);

        TrasladoResponse r = service.cancelar(1L, "admin1");

        assertThat(r.estado()).isEqualTo("CANCELADO");
        assertThat(r.canceladoEn()).isNotNull();
        verifyNoInteractions(inventarioService, movimientoService);
    }

    @Test
    void unTrasladoEnMovimientoYaNoSeCancela() {
        traslado(EstadoTraslado.EN_MOVIMIENTO);
        assertThatThrownBy(() -> service.cancelar(1L, "admin1"))
                .isInstanceOf(EstadoNoPermitidoException.class)
                .hasMessage("Solo se puede cancelar un traslado Solicitado.");
    }

    @Test
    void unTrasladoQueNoExisteDa404() {
        when(trasladoRepository.buscarParaCambiar(77L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.despachar(77L, "admin1"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El traslado no existe.");
    }

    // ---------- listar ----------

    @Test
    void listarDevuelveLosTrasladosConSusNombres() {
        Traslado t = traslado(EstadoTraslado.SOLICITADO);
        when(trasladoRepository.listar(1L)).thenReturn(List.of(t));

        List<TrasladoResponse> lista = service.listar();

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).modelo()).isEqualTo("Air Force");
        assertThat(lista.get(0).color()).isEqualTo("Blanco");
        assertThat(lista.get(0).calidad()).isEqualTo("Original");
    }
}
