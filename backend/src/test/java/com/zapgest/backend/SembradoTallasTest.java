package com.zapgest.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zapgest.backend.config.SembradoTallas;
import com.zapgest.backend.models.Talla;
import com.zapgest.backend.repositories.TallaRepository;

// Sembrado de la tabla talla (HU-05): tallas europeas 34, 34.5, 35 ... 46 = 25 tallas.
@ExtendWith(MockitoExtension.class)
class SembradoTallasTest {

    @Mock
    private TallaRepository repository;

    private Talla talla(Long id, String valor) {
        Talla t = new Talla();
        t.setId(id);
        t.setValor(new BigDecimal(valor));
        return t;
    }

    private List<Talla> guardadas() {
        ArgumentCaptor<Talla> captor = ArgumentCaptor.forClass(Talla.class);
        verify(repository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void conLaTablaVaciaCreaLas25TallasDel34Al46() {
        when(repository.findAll()).thenReturn(List.of());

        int creadas = new SembradoTallas(repository).sembrar();

        List<Talla> guardadas = guardadas();
        assertThat(creadas).isEqualTo(25);
        assertThat(guardadas).hasSize(25);
        assertThat(guardadas.get(0).getValor()).isEqualByComparingTo("34");
        assertThat(guardadas.get(24).getValor()).isEqualByComparingTo("46");
        assertThat(guardadas).anySatisfy(t -> assertThat(t.getValor()).isEqualByComparingTo("38.5"));
        assertThat(guardadas).extracting(Talla::comoTexto).contains("34", "34.5", "40", "45.5", "46");
    }

    @Test
    void losIdsGeneradosSonUnicosYEmpiezanEnUno() {
        when(repository.findAll()).thenReturn(List.of());

        new SembradoTallas(repository).sembrar();

        List<Long> ids = guardadas().stream().map(Talla::getId).toList();
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids.get(0)).isEqualTo(1L);
    }

    @Test
    void siFaltanAlgunasSoloCreaLasQueFaltanSinRepetirNiPisarIds() {
        when(repository.findAll()).thenReturn(new ArrayList<>(List.of(
                talla(7L, "34.0"),
                talla(9L, "38.5"))));

        int creadas = new SembradoTallas(repository).sembrar();

        List<Talla> guardadas = guardadas();
        assertThat(creadas).isEqualTo(23);
        assertThat(guardadas).extracting(Talla::comoTexto).doesNotContain("34", "38.5");
        assertThat(guardadas).extracting(Talla::getId).allSatisfy(id -> assertThat(id).isGreaterThan(9L));
    }

    @Test
    void siYaEstanTodasNoCreaNada() {
        List<Talla> todas = new ArrayList<>();
        long id = 1;
        for (int medias = 68; medias <= 92; medias++) {
            todas.add(talla(id++, new BigDecimal(medias).divide(new BigDecimal(2)).toPlainString()));
        }
        when(repository.findAll()).thenReturn(todas);

        int creadas = new SembradoTallas(repository).sembrar();

        assertThat(creadas).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void runSiembraYEsIdempotente() {
        when(repository.findAll()).thenReturn(List.of());

        new SembradoTallas(repository).run();

        verify(repository, times(25)).save(any(Talla.class));
    }
}
