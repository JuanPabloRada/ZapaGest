package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.repositories.UbicacionRepository;

// HU-04 / RN-01: maximo 10 ubicaciones ACTIVAS por negocio.
// No necesita base de datos: el repositorio se simula con Mockito.
@ExtendWith(MockitoExtension.class)
class UbicacionServiceTest {

    private static final String MENSAJE_LIMITE = "Ha alcanzado el límite máximo permitido de 10 ubicaciones";
    private static final Long NEGOCIO = 1L;

    @Mock
    private UbicacionRepository repository;

    private UbicacionService service() {
        return new UbicacionService(repository);
    }

    private Ubicacion ubicacion(Long id, String nombre, boolean activa) {
        Ubicacion u = new Ubicacion();
        u.setId(id);
        u.setNegocioId(NEGOCIO);
        u.setNombre(nombre);
        u.setActiva(activa);
        return u;
    }

    // ---------- crear ----------

    @Test
    void crearConNueveActivasEsPermitido() {
        when(repository.countByNegocioIdAndActivaTrue(NEGOCIO)).thenReturn(9L);
        when(repository.save(any(Ubicacion.class))).thenAnswer(i -> i.getArgument(0));

        Ubicacion creada = service().crear(ubicacion(null, "Bodega 10", true));

        assertThat(creada.getNombre()).isEqualTo("Bodega 10");
        assertThat(creada.getActiva()).isTrue();
    }

    @Test
    void crearConDiezActivasSeBloqueaConElMensajeAcordado() {
        when(repository.countByNegocioIdAndActivaTrue(NEGOCIO)).thenReturn(10L);

        assertThatThrownBy(() -> service().crear(ubicacion(null, "Bodega 11", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MENSAJE_LIMITE);
        verify(repository, never()).save(any());
    }

    @Test
    void elLimiteSoloCuentaActivasPorqueSeConsultaElConteoDeActivas() {
        // Si hubiera 12 ubicaciones en total pero solo 3 activas, el conteo de activas es 3
        // y la creacion debe pasar. El servicio nunca debe usar findAll().size().
        when(repository.countByNegocioIdAndActivaTrue(NEGOCIO)).thenReturn(3L);
        when(repository.save(any(Ubicacion.class))).thenAnswer(i -> i.getArgument(0));

        service().crear(ubicacion(null, "Nueva", true));

        verify(repository).countByNegocioIdAndActivaTrue(NEGOCIO);
        verify(repository, never()).findAll();
    }

    @Test
    void crearIgnoraIdNegocioYEstadoEnviadosPorElCliente() {
        when(repository.countByNegocioIdAndActivaTrue(NEGOCIO)).thenReturn(0L);
        when(repository.save(any(Ubicacion.class))).thenAnswer(i -> i.getArgument(0));

        Ubicacion maliciosa = ubicacion(99L, "  Bodega X  ", false);
        maliciosa.setNegocioId(2L); // intenta crearla en otro negocio

        service().crear(maliciosa);

        ArgumentCaptor<Ubicacion> captor = ArgumentCaptor.forClass(Ubicacion.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getNegocioId()).isEqualTo(NEGOCIO);
        assertThat(captor.getValue().getActiva()).isTrue();
        assertThat(captor.getValue().getNombre()).isEqualTo("Bodega X");
    }

    @Test
    void crearConNombreVacioONuloSeRechaza() {
        assertThatThrownBy(() -> service().crear(ubicacion(null, "   ", true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().crear(ubicacion(null, null, true)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    // ---------- cambiarEstado ----------

    @Test
    void desactivarSiemprePuedeAunqueHayaDiezActivas() {
        Ubicacion u = ubicacion(5L, "Bodega 5", true);
        when(repository.findByIdAndNegocioId(5L, NEGOCIO)).thenReturn(Optional.of(u));
        when(repository.save(any(Ubicacion.class))).thenAnswer(i -> i.getArgument(0));

        Ubicacion resultado = service().cambiarEstado(5L, false);

        assertThat(resultado.getActiva()).isFalse();
    }

    @Test
    void reactivarConNueveActivasEsPermitido() {
        Ubicacion u = ubicacion(7L, "Bodega 7", false);
        when(repository.findByIdAndNegocioId(7L, NEGOCIO)).thenReturn(Optional.of(u));
        when(repository.countByNegocioIdAndActivaTrue(NEGOCIO)).thenReturn(9L);
        when(repository.save(any(Ubicacion.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service().cambiarEstado(7L, true).getActiva()).isTrue();
    }

    @Test
    void reactivarConDiezActivasSeBloqueaConElMensajeAcordado() {
        Ubicacion u = ubicacion(7L, "Bodega 7", false);
        when(repository.findByIdAndNegocioId(7L, NEGOCIO)).thenReturn(Optional.of(u));
        when(repository.countByNegocioIdAndActivaTrue(NEGOCIO)).thenReturn(10L);

        assertThatThrownBy(() -> service().cambiarEstado(7L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MENSAJE_LIMITE);
        verify(repository, never()).save(any());
        assertThat(u.getActiva()).isFalse();
    }

    @Test
    void activarUnaYaActivaNoConsultaElLimiteNiFalla() {
        // Con 10 activas, "activar" una que ya esta activa es idempotente y no debe bloquearse.
        Ubicacion u = ubicacion(3L, "Bodega 3", true);
        when(repository.findByIdAndNegocioId(3L, NEGOCIO)).thenReturn(Optional.of(u));
        when(repository.save(any(Ubicacion.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service().cambiarEstado(3L, true).getActiva()).isTrue();
        verify(repository, never()).countByNegocioIdAndActivaTrue(any());
    }

    @Test
    void cambiarEstadoSinValorActivaSeRechaza() {
        assertThatThrownBy(() -> service().cambiarEstado(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    // ---------- aislamiento por negocio ----------

    @Test
    void unaUbicacionDeOtroNegocioNoSePuedeModificar() {
        // El repositorio solo devuelve la ubicacion si pertenece al negocio 1.
        // Una ubicacion del negocio 2 llega aqui como Optional.empty().
        when(repository.findByIdAndNegocioId(50L, NEGOCIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().cambiarEstado(50L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se encontró la ubicación.");
        verify(repository, never()).save(any());
        verify(repository, never()).findById(any());
    }

    @Test
    void listarSoloConsultaElNegocioActual() {
        List<Ubicacion> delNegocio = List.of(ubicacion(1L, "Local", true));
        when(repository.findAllByNegocioId(NEGOCIO)).thenReturn(delNegocio);

        assertThat(service().listar()).isEqualTo(delNegocio);
        verify(repository, never()).findAll();
    }

    // ---------- nombres repetidos al crear ----------

    @Test
    void crearRechazaUnNombreRepetidoSinImportarMayusculas() {
        when(repository.existsByNegocioIdAndNombreIgnoreCase(NEGOCIO, "LOCAL")).thenReturn(true);

        assertThatThrownBy(() -> service().crear(ubicacion(null, " LOCAL ", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya existe una ubicación con ese nombre.");
        verify(repository, never()).save(any());
    }

    // ---------- editar (PUT /api/ubicaciones/{id}) ----------

    @Test
    void editarCambiaSoloElNombreYNoCuentaParaElLimite() {
        Ubicacion u = ubicacion(3L, "Bodga 1", false);
        when(repository.findByIdAndNegocioId(3L, NEGOCIO)).thenReturn(Optional.of(u));
        when(repository.save(any(Ubicacion.class))).thenAnswer(i -> i.getArgument(0));

        Ubicacion editada = service().editar(3L, "  Bodega   1 ");

        assertThat(editada.getNombre()).isEqualTo("Bodega 1");
        assertThat(editada.getActiva()).isFalse(); // el estado no cambia
        verify(repository, never()).countByNegocioIdAndActivaTrue(any());
        verify(repository).existsByNegocioIdAndNombreIgnoreCaseAndIdNot(NEGOCIO, "Bodega 1", 3L);
    }

    @Test
    void editarRechazaUnNombreQueTieneOtraUbicacion() {
        when(repository.findByIdAndNegocioId(3L, NEGOCIO)).thenReturn(Optional.of(ubicacion(3L, "Bodega 1", true)));
        when(repository.existsByNegocioIdAndNombreIgnoreCaseAndIdNot(NEGOCIO, "Local", 3L)).thenReturn(true);

        assertThatThrownBy(() -> service().editar(3L, "Local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya existe una ubicación con ese nombre.");
        verify(repository, never()).save(any());
    }

    @Test
    void editarValidaNombreVacioYLargo() {
        when(repository.findByIdAndNegocioId(3L, NEGOCIO)).thenReturn(Optional.of(ubicacion(3L, "Bodega 1", true)));

        assertThatThrownBy(() -> service().editar(3L, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre de la ubicación es obligatorio.");
        assertThatThrownBy(() -> service().editar(3L, "x".repeat(81)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("80");
        verify(repository, never()).save(any());
    }

    @Test
    void editarUnaUbicacionQueNoExisteDa404() {
        when(repository.findByIdAndNegocioId(50L, NEGOCIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().editar(50L, "Nueva"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró la ubicación.");
        verify(repository, never()).save(any());
    }

    // ---------- Sprint 2: no desactivar ubicaciones con stock ----------

    @Test
    void noSeDesactivaUnaUbicacionConParesEnInventario() {
        when(repository.findByIdAndNegocioId(5L, NEGOCIO)).thenReturn(Optional.of(ubicacion(5L, "Bodega 1", true)));
        when(repository.paresEnInventario(5L)).thenReturn(24L);

        assertThatThrownBy(() -> service().cambiarEstado(5L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se puede desactivar la ubicación: tiene 24 pares en inventario. Primero deben salir o trasladarse.");
        verify(repository, never()).save(any());
    }

    @Test
    void conUnSoloParElMensajeVaEnSingular() {
        when(repository.findByIdAndNegocioId(5L, NEGOCIO)).thenReturn(Optional.of(ubicacion(5L, "Bodega 1", true)));
        when(repository.paresEnInventario(5L)).thenReturn(1L);

        assertThatThrownBy(() -> service().cambiarEstado(5L, false))
                .hasMessageContaining("tiene 1 par en inventario");
    }

    @Test
    void siDesactivaUnaUbicacionSinStock() {
        when(repository.findByIdAndNegocioId(5L, NEGOCIO)).thenReturn(Optional.of(ubicacion(5L, "Bodega 1", true)));
        when(repository.paresEnInventario(5L)).thenReturn(0L);
        when(repository.save(any(Ubicacion.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service().cambiarEstado(5L, false).getActiva()).isFalse();
    }
}
