package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.models.Color;
import com.zapgest.backend.models.Referencia;
import com.zapgest.backend.models.ReferenciaCalidad;
import com.zapgest.backend.models.Talla;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.ReferenciaRepository;
import com.zapgest.backend.repositories.VarianteRepository;

// HU-06: reglas de "obtener o crear variante" (sin base de datos; los repositorios se simulan).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VarianteServiceTest {

    private static final Long REF = 1L, BLANCO = 10L, NEGRO = 11L, T40 = 20L, T41 = 21L, ORIGINAL = 30L, AAA = 31L;

    @Mock
    private VarianteRepository varianteRepository;
    @Mock
    private ReferenciaRepository referenciaRepository;

    private VarianteService service;
    private Referencia referencia;

    @BeforeEach
    void preparar() {
        service = new VarianteService(varianteRepository, referenciaRepository);

        // Referencia AF1-001: colores Blanco, tallas 40, calidad Original con precios
        referencia = new Referencia();
        referencia.setId(REF);
        referencia.setNegocioId(1L);
        referencia.setCodigo("AF1-001");
        referencia.setNombre("Air Force");
        referencia.setActiva(true);
        referencia.getColores().add(color(BLANCO, "Blanco"));
        referencia.getTallas().add(talla(T40, "40"));
        referencia.getPrecios().add(precio(ORIGINAL, "Original", "100000", "150000", "180000"));

        when(referenciaRepository.findByIdAndNegocioId(REF, 1L)).thenReturn(Optional.of(referencia));
        when(varianteRepository.findByReferenciaIdAndColorIdAndTallaIdAndCalidadId(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(varianteRepository.save(any(Variante.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void creaLaVarianteYHeredaLosPreciosDeSuCalidad() {
        Variante v = service.obtenerOCrear(REF, BLANCO, T40, ORIGINAL);

        assertThat(v.getReferencia()).isSameAs(referencia);
        assertThat(v.getColor().getId()).isEqualTo(BLANCO);
        assertThat(v.getTalla().getId()).isEqualTo(T40);
        assertThat(v.getCalidad().getId()).isEqualTo(ORIGINAL);
        assertThat(v.getCostoAdquisicion()).isEqualByComparingTo("100000");
        assertThat(v.getPrecioMinimo()).isEqualByComparingTo("150000");
        assertThat(v.getPrecioSugerido()).isEqualByComparingTo("180000");
        assertThat(v.getNegocioId()).isEqualTo(1L);
        assertThat(v.getActiva()).isTrue();
        verify(varianteRepository).save(any(Variante.class));
    }

    @Test
    void siLaCombinacionYaExisteLaReutilizaSinCrearOtra() {
        Variante existente = new Variante();
        existente.setId(99L);
        existente.setPrecioSugerido(new BigDecimal("200000")); // precio ya ajustado: no se pisa
        when(varianteRepository.findByReferenciaIdAndColorIdAndTallaIdAndCalidadId(REF, BLANCO, T40, ORIGINAL))
                .thenReturn(Optional.of(existente));

        Variante v = service.obtenerOCrear(REF, BLANCO, T40, ORIGINAL);

        assertThat(v).isSameAs(existente);
        assertThat(v.getPrecioSugerido()).isEqualByComparingTo("200000");
        verify(varianteRepository, never()).save(any());
    }

    @Test
    void rechazaDatosIncompletos() {
        assertThatThrownBy(() -> service.obtenerOCrear(REF, null, T40, ORIGINAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes elegir referencia, color, talla y calidad.");
        verify(varianteRepository, never()).save(any());
    }

    @Test
    void referenciaInexistenteDa404() {
        when(referenciaRepository.findByIdAndNegocioId(777L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerOCrear(777L, BLANCO, T40, ORIGINAL))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró la referencia.");
        verify(varianteRepository, never()).save(any());
    }

    @Test
    void rechazaReferenciaInactiva() {
        referencia.setActiva(false);

        assertThatThrownBy(() -> service.obtenerOCrear(REF, BLANCO, T40, ORIGINAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La referencia está inactiva.");
        verify(varianteRepository, never()).save(any());
    }

    @Test
    void rechazaColorQueNoEsDeLaReferencia() {
        assertThatThrownBy(() -> service.obtenerOCrear(REF, NEGRO, T40, ORIGINAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El color no pertenece a esta referencia.");
        verify(varianteRepository, never()).save(any());
    }

    @Test
    void rechazaTallaQueNoEsDeLaReferencia() {
        assertThatThrownBy(() -> service.obtenerOCrear(REF, BLANCO, T41, ORIGINAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La talla no pertenece a esta referencia.");
        verify(varianteRepository, never()).save(any());
    }

    @Test
    void rechazaCalidadQueNoEsDeLaReferencia() {
        assertThatThrownBy(() -> service.obtenerOCrear(REF, BLANCO, T40, AAA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La calidad no pertenece a esta referencia.");
        verify(varianteRepository, never()).save(any());
    }

    @Test
    void cadaCalidadHeredaSusPropiosPrecios() {
        referencia.getPrecios().add(precio(AAA, "AAA", "40000", "60000", "75000"));

        Variante v = service.obtenerOCrear(REF, BLANCO, T40, AAA);

        assertThat(v.getCalidad().getId()).isEqualTo(AAA);
        assertThat(v.getCostoAdquisicion()).isEqualByComparingTo("40000");
        assertThat(v.getPrecioSugerido()).isEqualByComparingTo("75000");
    }

    // ===== ayudas =====

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

    private ReferenciaCalidad precio(Long calidadId, String nombre, String costo, String minimo, String sugerido) {
        Calidad calidad = new Calidad();
        calidad.setId(calidadId);
        calidad.setNegocioId(1L);
        calidad.setNombre(nombre);
        calidad.setActiva(true);

        ReferenciaCalidad p = new ReferenciaCalidad();
        p.setReferencia(referencia);
        p.setCalidad(calidad);
        p.setCostoAdquisicion(new BigDecimal(costo));
        p.setPrecioMinimo(new BigDecimal(minimo));
        p.setPrecioSugerido(new BigDecimal(sugerido));
        return p;
    }
}
