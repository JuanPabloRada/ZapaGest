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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.repositories.CalidadRepository;

// HU-03: validaciones del servicio de calidades (sin base de datos).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalidadServiceTest {

    @Mock
    private CalidadRepository repository;

    private Calidad calidad(Long id, Long negocioId, String nombre, Boolean activa) {
        Calidad c = new Calidad();
        c.setId(id);
        c.setNegocioId(negocioId);
        c.setNombre(nombre);
        c.setActiva(activa);
        return c;
    }

    private Calidad guardada() {
        ArgumentCaptor<Calidad> captor = ArgumentCaptor.forClass(Calidad.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void creaUnaCalidadConNombreLibre() {
        when(repository.save(any(Calidad.class))).thenAnswer(i -> i.getArgument(0));

        Calidad creada = new CalidadService(repository).crear(calidad(null, null, "1.1", null));

        assertThat(creada.getNombre()).isEqualTo("1.1");
        assertThat(creada.getNegocioId()).isEqualTo(1L);
        assertThat(creada.getActiva()).isTrue();
    }

    @Test
    void recortaYUneEspaciosDelNombre() {
        when(repository.save(any(Calidad.class))).thenAnswer(i -> i.getArgument(0));

        new CalidadService(repository).crear(calidad(null, null, "   AAA    Plus  ", null));

        assertThat(guardada().getNombre()).isEqualTo("AAA Plus");
        verify(repository).existsByNegocioIdAndNombreIgnoreCase(1L, "AAA Plus");
    }

    @Test
    void ignoraIdNegocioYActivaEnviadosPorElCliente() {
        // Con un id existente, save() actualizaria (sobrescribiria) otra calidad.
        when(repository.save(any(Calidad.class))).thenAnswer(i -> i.getArgument(0));

        new CalidadService(repository).crear(calidad(99L, 2L, "Nacional", false));

        Calidad g = guardada();
        assertThat(g.getId()).isNull();
        assertThat(g.getNegocioId()).isEqualTo(1L);
        assertThat(g.getActiva()).isTrue();
    }

    @Test
    void rechazaNombreVacioONuloOCuerpoNulo() {
        CalidadService service = new CalidadService(repository);

        assertThatThrownBy(() -> service.crear(calidad(null, null, "", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> service.crear(calidad(null, null, "   ", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> service.crear(calidad(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> service.crear(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatorio");
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaNombreDeMasDe50Caracteres() {
        CalidadService service = new CalidadService(repository);

        assertThatThrownBy(() -> service.crear(calidad(null, null, "x".repeat(51), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("50");
        verify(repository, never()).save(any());
    }

    @Test
    void aceptaNombreDeExactamente50Caracteres() {
        when(repository.save(any(Calidad.class))).thenAnswer(i -> i.getArgument(0));

        new CalidadService(repository).crear(calidad(null, null, "x".repeat(50), null));

        assertThat(guardada().getNombre()).hasSize(50);
    }

    @Test
    void rechazaNombreRepetidoSinDistinguirMayusculas() {
        when(repository.existsByNegocioIdAndNombreIgnoreCase(1L, "aaa")).thenReturn(true);

        assertThatThrownBy(() -> new CalidadService(repository).crear(calidad(null, null, " aaa ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya existe una calidad con ese nombre.");
        verify(repository, never()).save(any());
    }

    @Test
    void listarSoloConsultaLasCalidadesDelNegocioActual() {
        List<Calidad> delNegocio = List.of(calidad(1L, 1L, "Original", true));
        when(repository.findAllByNegocioIdOrderByIdAsc(1L)).thenReturn(delNegocio);

        assertThat(new CalidadService(repository).listar()).isEqualTo(delNegocio);
        verify(repository, never()).findAll();
    }

    // ---------- editar (PUT /api/calidades/{id}) ----------

    @Test
    void editarCambiaSoloElNombre() {
        Calidad existente = calidad(5L, 1L, "Orignal", true);
        when(repository.findByIdAndNegocioId(5L, 1L)).thenReturn(Optional.of(existente));
        when(repository.save(any(Calidad.class))).thenAnswer(i -> i.getArgument(0));

        Calidad editada = new CalidadService(repository).editar(5L, "  Original ");

        assertThat(editada.getId()).isEqualTo(5L);
        assertThat(editada.getNombre()).isEqualTo("Original");
        assertThat(editada.getActiva()).isTrue();
        verify(repository).existsByNegocioIdAndNombreIgnoreCaseAndIdNot(1L, "Original", 5L);
    }

    @Test
    void editarPermiteGuardarElMismoNombreOCambiarMayusculas() {
        // El repetido se busca IGNORANDO el propio registro (IdNot)
        when(repository.findByIdAndNegocioId(5L, 1L)).thenReturn(Optional.of(calidad(5L, 1L, "aaa", true)));
        when(repository.existsByNegocioIdAndNombreIgnoreCaseAndIdNot(1L, "AAA", 5L)).thenReturn(false);
        when(repository.save(any(Calidad.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(new CalidadService(repository).editar(5L, "AAA").getNombre()).isEqualTo("AAA");
    }

    @Test
    void editarRechazaUnNombreQueTieneOtraCalidad() {
        when(repository.findByIdAndNegocioId(5L, 1L)).thenReturn(Optional.of(calidad(5L, 1L, "AA", true)));
        when(repository.existsByNegocioIdAndNombreIgnoreCaseAndIdNot(1L, "Original", 5L)).thenReturn(true);

        assertThatThrownBy(() -> new CalidadService(repository).editar(5L, "Original"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya existe una calidad con ese nombre.");
        verify(repository, never()).save(any());
    }

    @Test
    void editarValidaElNombreIgualQueAlCrear() {
        when(repository.findByIdAndNegocioId(5L, 1L)).thenReturn(Optional.of(calidad(5L, 1L, "AA", true)));

        assertThatThrownBy(() -> new CalidadService(repository).editar(5L, "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> new CalidadService(repository).editar(5L, "x".repeat(51)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("50");
        verify(repository, never()).save(any());
    }

    @Test
    void editarUnaCalidadQueNoExisteDa404() {
        when(repository.findByIdAndNegocioId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CalidadService(repository).editar(99L, "Nueva"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró la calidad.");
        verify(repository, never()).save(any());
    }
}
