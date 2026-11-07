package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.zapgest.backend.dto.catalogo.OpcionResponse;
import com.zapgest.backend.dto.catalogo.OpcionesReferenciaResponse;
import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.models.Color;
import com.zapgest.backend.models.Referencia;
import com.zapgest.backend.models.ReferenciaCalidad;
import com.zapgest.backend.models.Talla;
import com.zapgest.backend.repositories.CalidadRepository;
import com.zapgest.backend.repositories.ColorRepository;
import com.zapgest.backend.repositories.ReferenciaRepository;
import com.zapgest.backend.repositories.TallaRepository;

// HU-07: GET /api/catalogo/{id}/opciones -> colores, tallas y calidades de una referencia, sin costos.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogoOpcionesTest {

    @Mock
    private ReferenciaRepository referenciaRepository;
    @Mock
    private ColorRepository colorRepository;
    @Mock
    private TallaRepository tallaRepository;
    @Mock
    private CalidadRepository calidadRepository;

    private CatalogoService service;
    private Referencia referencia;

    @BeforeEach
    void preparar() {
        service = new CatalogoService(referenciaRepository, colorRepository, tallaRepository, calidadRepository);

        referencia = new Referencia();
        referencia.setId(1L);
        referencia.setNegocioId(1L);
        referencia.setCodigo("AF1-001");
        referencia.setNombre("Air Force");
        referencia.setMarca("Nike");
        // Se agregan desordenados a proposito: la respuesta debe salir ordenada
        referencia.getColores().add(color(11L, "Negro"));
        referencia.getColores().add(color(10L, "blanco"));
        referencia.getTallas().add(talla(13L, "41"));
        referencia.getTallas().add(talla(12L, "38.5"));
        referencia.getPrecios().add(precio(31L, "Original"));
        referencia.getPrecios().add(precio(30L, "AAA"));

        when(referenciaRepository.findByIdAndNegocioId(1L, 1L)).thenReturn(Optional.of(referencia));
    }

    @Test
    void devuelveLosDatosDeLaReferenciaYSusOpcionesOrdenadas() {
        OpcionesReferenciaResponse r = service.opciones(1L);

        assertThat(r.referenciaId()).isEqualTo(1L);
        assertThat(r.codigo()).isEqualTo("AF1-001");
        assertThat(r.nombre()).isEqualTo("Air Force");
        assertThat(r.marca()).isEqualTo("Nike");
        assertThat(r.colores()).containsExactly(new OpcionResponse(10L, "blanco"), new OpcionResponse(11L, "Negro"));
        assertThat(r.tallas()).containsExactly(new OpcionResponse(12L, "38.5"), new OpcionResponse(13L, "41"));
        assertThat(r.calidades()).containsExactly(new OpcionResponse(30L, "AAA"), new OpcionResponse(31L, "Original"));
    }

    @Test
    void laRespuestaNoTraeCostosNiPrecios() {
        // El record solo tiene estos campos: si alguien agrega un costo, esta prueba lo delata
        assertThat(OpcionesReferenciaResponse.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly("referenciaId", "codigo", "nombre", "marca", "colores", "tallas", "calidades");
    }

    @Test
    void referenciaInexistenteDa404() {
        when(referenciaRepository.findByIdAndNegocioId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.opciones(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró la referencia.");
    }

    private static Color color(Long id, String nombre) {
        Color c = new Color();
        c.setId(id);
        c.setNegocioId(1L);
        c.setNombre(nombre);
        return c;
    }

    private static Talla talla(Long id, String valor) {
        Talla t = new Talla();
        t.setId(id);
        t.setValor(new BigDecimal(valor));
        return t;
    }

    private ReferenciaCalidad precio(Long calidadId, String nombre) {
        Calidad c = new Calidad();
        c.setId(calidadId);
        c.setNegocioId(1L);
        c.setNombre(nombre);
        ReferenciaCalidad p = new ReferenciaCalidad();
        p.setReferencia(referencia);
        p.setCalidad(c);
        p.setCostoAdquisicion(new BigDecimal("100"));
        p.setPrecioMinimo(new BigDecimal("150"));
        p.setPrecioSugerido(new BigDecimal("180"));
        return p;
    }
}
