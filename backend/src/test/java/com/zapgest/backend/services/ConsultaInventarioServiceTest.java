package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.zapgest.backend.dto.inventario.ResultadoBusquedaInventario;
import com.zapgest.backend.dto.inventario.UbicacionCantidadResponse;
import com.zapgest.backend.dto.inventario.VarianteDisponibleResponse;
import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.models.Color;
import com.zapgest.backend.models.Inventario;
import com.zapgest.backend.models.Referencia;
import com.zapgest.backend.models.Talla;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.CatalogoBusquedaRepository;
import com.zapgest.backend.repositories.CombinacionCatalogo;
import com.zapgest.backend.repositories.InventarioBusquedaRepository;
import com.zapgest.backend.repositories.InventarioRepository;

// HU-09 y HU-10: busqueda, total y desglose por ubicacion (sin base de datos; repositorios simulados).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsultaInventarioServiceTest {

    @Mock
    private InventarioBusquedaRepository busquedaRepository;
    @Mock
    private InventarioRepository inventarioRepository;
    @Mock
    private CatalogoBusquedaRepository catalogoRepository;

    private ConsultaInventarioService service;

    private Variante blanco40;   // 3 en Bodega + 2 en Local = 5, con precio
    private Variante negro41;    // sin stock y sin precio

    @BeforeEach
    void preparar() {
        service = new ConsultaInventarioService(busquedaRepository, inventarioRepository, catalogoRepository);

        Referencia ref = new Referencia();
        ref.setId(1L);
        ref.setCodigo("AF1-001");
        ref.setNombre("Air Force");
        ref.setMarca("Nike");

        blanco40 = variante(100L, ref, "Blanco", "40", "Original", "180000");
        negro41 = variante(101L, ref, "Negro", "41.0", "AAA", null);

        when(inventarioRepository.conStockDe(eq(1L), anyCollection())).thenReturn(List.of(
                fila(blanco40, 5L, "Bodega", 3),
                fila(blanco40, 6L, "Local", 2)));
    }

    private void devuelvePagina(Variante... variantes) {
        Page<Variante> pagina = new PageImpl<>(Arrays.asList(variantes));
        when(busquedaRepository.buscar(anyLong(), anyString(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(pagina);
    }

    @Test
    void sumaElTotalYDaElDesglosePorUbicacion() {
        devuelvePagina(blanco40);

        ResultadoBusquedaInventario r = service.buscar(null, null, null, null, false, 0, 50);

        assertThat(r.items()).hasSize(1);
        VarianteDisponibleResponse v = r.items().get(0);
        assertThat(v.varianteId()).isEqualTo(100L);
        assertThat(v.referencia()).isEqualTo("AF1-001");
        assertThat(v.modelo()).isEqualTo("Air Force");
        assertThat(v.color()).isEqualTo("Blanco");
        assertThat(v.talla()).isEqualTo("40");
        assertThat(v.calidad()).isEqualTo("Original");
        assertThat(v.precioSugerido()).isEqualByComparingTo("180000");
        assertThat(v.total()).isEqualTo(5);
        assertThat(v.ubicaciones()).extracting(UbicacionCantidadResponse::ubicacion, UbicacionCantidadResponse::cantidad)
                .containsExactly(tuple("Bodega", 3), tuple("Local", 2));
        assertThat(r.totalResultados()).isEqualTo(1);
    }

    @Test
    void unaVarianteSinStockSaleConTotalCeroYSinUbicaciones() {
        devuelvePagina(negro41);

        VarianteDisponibleResponse v = service.buscar(null, null, null, null, false, 0, 50).items().get(0);

        assertThat(v.total()).isZero();
        assertThat(v.ubicaciones()).isEmpty();
    }

    @Test
    void unaVarianteSinPreciosLlegaConPrecioSugeridoNull() {
        devuelvePagina(negro41);

        VarianteDisponibleResponse v = service.buscar(null, null, null, null, false, 0, 50).items().get(0);

        assertThat(v.precioSugerido()).isNull();
        assertThat(v.talla()).isEqualTo("41"); // sin ceros de sobra
    }

    @Test
    void laRespuestaNoTieneCostoNiPrecioMinimo() {
        for (RecordComponent campo : VarianteDisponibleResponse.class.getRecordComponents()) {
            assertThat(campo.getName().toLowerCase()).doesNotContain("costo").doesNotContain("minimo");
        }
    }

    @Test
    void pasaLosFiltrosYElFlagDeAgotadosAlRepositorio() {
        devuelvePagina(blanco40);

        service.buscar("AF1", 10L, 20L, 30L, false, 2, 25);

        ArgumentCaptor<Pageable> pag = ArgumentCaptor.forClass(Pageable.class);
        verify(busquedaRepository).buscar(eq(1L), eq("%af1%"), eq(10L), eq(20L), eq(30L), eq(false), pag.capture());
        assertThat(pag.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pag.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    void sinParametrosNoFiltraPorNada() {
        devuelvePagina(blanco40);

        service.buscar(null, null, null, null, false, 0, 50);

        verify(busquedaRepository).buscar(eq(1L), eq("%%"), isNull(), isNull(), isNull(), eq(false), any(Pageable.class));
    }

    @Test
    void elTextoSeNormalizaYSeQuitanLosComodines() {
        assertThat(ConsultaInventarioService.patron("  Air   FORCE ")).isEqualTo("%air force%");
        assertThat(ConsultaInventarioService.patron("100%_\\")).isEqualTo("%100%");
        assertThat(ConsultaInventarioService.patron("   ")).isEqualTo("%%");
        assertThat(ConsultaInventarioService.patron(null)).isEqualTo("%%");
    }

    @Test
    void elTamanoDePaginaSeLimitaA100() {
        devuelvePagina(blanco40);

        service.buscar(null, null, null, null, false, 0, 5000);

        ArgumentCaptor<Pageable> pag = ArgumentCaptor.forClass(Pageable.class);
        verify(busquedaRepository).buscar(anyLong(), anyString(), any(), any(), any(), anyBoolean(), pag.capture());
        assertThat(pag.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void rechazaPaginaNegativaOTamanoCero() {
        assertThatThrownBy(() -> service.buscar(null, null, null, null, false, -1, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.buscar(null, null, null, null, false, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(busquedaRepository, never()).buscar(anyLong(), anyString(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void sinResultadosDevuelveListaVaciaYNoConsultaElStock() {
        when(busquedaRepository.buscar(anyLong(), anyString(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ResultadoBusquedaInventario r = service.buscar("zzz", null, null, null, false, 0, 50);

        assertThat(r.items()).isEmpty();
        assertThat(r.totalResultados()).isZero();
        verify(inventarioRepository, never()).conStockDe(anyLong(), anyCollection());
    }

    // ===== ayudas =====

    // ---------- con agotados: se recorre el catalogo ----------

    @Test
    void conAgotadosSaleTodoElCatalogoAunqueNuncaHayaTenidoIngresos() {
        blanco40.getColor().setId(10L);
        blanco40.getTalla().setId(20L);
        blanco40.getCalidad().setId(30L);
        when(catalogoRepository.buscar(anyLong(), anyString(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        // ya tiene variante con stock
                        new CombinacionCatalogo(1L, "AF1-001", "Air Force", 10L, "Blanco", 20L, new BigDecimal("40"),
                                30L, "Original", new BigDecimal("190000")),
                        // nunca ha tenido un ingreso
                        new CombinacionCatalogo(1L, "AF1-001", "Air Force", 11L, "Rojo", 21L, new BigDecimal("38.0"),
                                30L, "Original", new BigDecimal("190000")))));
        when(catalogoRepository.variantesDe(eq(1L), anyCollection())).thenReturn(List.of(blanco40));

        ResultadoBusquedaInventario r = service.buscar(null, null, null, null, true, 0, 50);

        assertThat(r.items()).hasSize(2);
        VarianteDisponibleResponse conStock = r.items().get(0);
        assertThat(conStock.varianteId()).isEqualTo(100L);
        assertThat(conStock.total()).isEqualTo(5);
        assertThat(conStock.precioSugerido()).isEqualByComparingTo("180000"); // manda el precio de la variante

        VarianteDisponibleResponse sinIngresos = r.items().get(1);
        assertThat(sinIngresos.varianteId()).isNull();
        assertThat(sinIngresos.color()).isEqualTo("Rojo");
        assertThat(sinIngresos.talla()).isEqualTo("38");
        assertThat(sinIngresos.total()).isZero();
        assertThat(sinIngresos.ubicaciones()).isEmpty();
        assertThat(sinIngresos.precioSugerido()).isEqualByComparingTo("190000"); // precio del catalogo
    }

    @Test
    void conAgotadosLosFiltrosVanAlCatalogoYNoALaBusquedaDeVariantes() {
        when(catalogoRepository.buscar(anyLong(), anyString(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ResultadoBusquedaInventario r = service.buscar("Chuck", 10L, 20L, 30L, true, 1, 25);

        assertThat(r.items()).isEmpty();
        ArgumentCaptor<Pageable> pag = ArgumentCaptor.forClass(Pageable.class);
        verify(catalogoRepository).buscar(eq(1L), eq("%chuck%"), eq(10L), eq(20L), eq(30L), pag.capture());
        assertThat(pag.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pag.getValue().getPageSize()).isEqualTo(25);
        verify(busquedaRepository, never()).buscar(anyLong(), anyString(), any(), any(), any(), anyBoolean(), any());
    }

    private static Variante variante(Long id, Referencia ref, String color, String talla, String calidad, String precio) {
        Variante v = new Variante();
        v.setId(id);
        v.setNegocioId(1L);
        v.setReferencia(ref);
        Color c = new Color();
        c.setNombre(color);
        v.setColor(c);
        Talla t = new Talla();
        t.setValor(new BigDecimal(talla));
        v.setTalla(t);
        Calidad q = new Calidad();
        q.setNombre(calidad);
        v.setCalidad(q);
        v.setCostoAdquisicion(new BigDecimal("100000")); // existe en la entidad pero nunca sale por la API
        v.setPrecioMinimo(new BigDecimal("150000"));
        v.setPrecioSugerido(precio == null ? null : new BigDecimal(precio));
        return v;
    }

    private static Inventario fila(Variante v, Long ubicacionId, String nombre, int cantidad) {
        Ubicacion u = new Ubicacion();
        u.setId(ubicacionId);
        u.setNombre(nombre);
        Inventario i = new Inventario();
        i.setNegocioId(1L);
        i.setVariante(v);
        i.setUbicacion(u);
        i.setCantidad(cantidad);
        return i;
    }
}
