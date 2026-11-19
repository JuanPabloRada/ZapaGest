package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.zapgest.backend.dto.catalogo.CatalogoRequest;
import com.zapgest.backend.dto.catalogo.CatalogoResponse;
import com.zapgest.backend.dto.catalogo.OpcionResponse;
import com.zapgest.backend.dto.catalogo.PrecioRequest;
import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.exceptions.ReferenciaDuplicadaException;
import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.models.Color;
import com.zapgest.backend.models.Referencia;
import com.zapgest.backend.models.ReferenciaCalidad;
import com.zapgest.backend.models.ReferenciaCalidadId;
import com.zapgest.backend.models.Talla;
import com.zapgest.backend.repositories.CalidadRepository;
import com.zapgest.backend.repositories.ColorRepository;
import com.zapgest.backend.repositories.ReferenciaRepository;
import com.zapgest.backend.repositories.StockPorOpcion;
import com.zapgest.backend.repositories.TallaRepository;
import com.zapgest.backend.repositories.VarianteRepository;

// HU-05: reglas del servicio de catalogo (sin base de datos; los repositorios se simulan).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogoServiceTest {

    @Mock
    private ReferenciaRepository referenciaRepository;
    @Mock
    private ColorRepository colorRepository;
    @Mock
    private TallaRepository tallaRepository;
    @Mock
    private CalidadRepository calidadRepository;
    @Mock
    private VarianteRepository varianteRepository;

    private CatalogoService service;
    private final List<Talla> tablaTallas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        service = new CatalogoService(referenciaRepository, colorRepository, tallaRepository, calidadRepository,
                varianteRepository);

        // Tabla talla: 34, 34.5 ... 46
        long id = 1;
        for (int medias = 68; medias <= 92; medias++) {
            Talla t = new Talla();
            t.setId(id++);
            t.setValor(new BigDecimal(medias).divide(new BigDecimal(2)));
            tablaTallas.add(t);
        }
        when(tallaRepository.findByValor(any(BigDecimal.class))).thenAnswer(inv -> {
            BigDecimal pedido = inv.getArgument(0);
            return tablaTallas.stream().filter(t -> t.getValor().compareTo(pedido) == 0).findFirst();
        });

        // Calidades: 1 y 3 del negocio 1; 9 es de OTRO negocio; 5 no existe
        when(calidadRepository.findById(1L)).thenReturn(Optional.of(calidad(1L, 1L, "Original")));
        when(calidadRepository.findById(3L)).thenReturn(Optional.of(calidad(3L, 1L, "AAA")));
        when(calidadRepository.findById(9L)).thenReturn(Optional.of(calidad(9L, 2L, "De otro negocio")));

        when(referenciaRepository.existsByNegocioIdAndCodigoIgnoreCase(any(), any())).thenReturn(false);
        when(referenciaRepository.save(any(Referencia.class))).thenAnswer(i -> i.getArgument(0));
        when(referenciaRepository.saveAndFlush(any(Referencia.class))).thenAnswer(i -> i.getArgument(0));
        when(colorRepository.save(any(Color.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- helpers ----------

    private Calidad calidad(Long id, Long negocioId, String nombre) {
        Calidad c = new Calidad();
        c.setId(id);
        c.setNegocioId(negocioId);
        c.setNombre(nombre);
        return c;
    }

    private PrecioRequest precio(Long calidadId, String costo, String minimo, String sugerido) {
        return new PrecioRequest(calidadId,
                costo == null ? null : new BigDecimal(costo),
                minimo == null ? null : new BigDecimal(minimo),
                sugerido == null ? null : new BigDecimal(sugerido));
    }

    private List<PrecioRequest> preciosValidos() {
        return List.of(precio(1L, "250000", "330000", "380000"), precio(3L, "120000", "160000", "190000"));
    }

    private CatalogoRequest valida() {
        return new CatalogoRequest("AF1-001", "Nike Air Force", "Nike",
                List.of("Blanco", "Negro"), List.of("38", "38.5"), preciosValidos());
    }

    // El 4o parametro era la categoria: ya no existe (solo se guarda la marca) y se ignora.
    private CatalogoRequest con(String referencia, String nombre, String marca, String sinUso,
            List<String> colores, List<String> tallas, List<PrecioRequest> precios) {
        return new CatalogoRequest(referencia, nombre, marca, colores, tallas, precios);
    }

    private Referencia referenciaGuardada() {
        ArgumentCaptor<Referencia> captor = ArgumentCaptor.forClass(Referencia.class);
        verify(referenciaRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void assertRechaza(CatalogoRequest datos, String fragmentoDelMensaje) {
        assertThatThrownBy(() -> service.crear(datos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(fragmentoDelMensaje);
        verify(referenciaRepository, never()).save(any());
        verify(referenciaRepository, never()).saveAndFlush(any());
    }

    // ---------- caso feliz ----------

    @Test
    void registraUnaReferenciaConColoresTallasYPreciosPorCalidad() {
        CatalogoResponse r = service.crear(valida());

        assertThat(r.referencia()).isEqualTo("AF1-001");
        assertThat(r.nombre()).isEqualTo("Nike Air Force");
        assertThat(r.marca()).isEqualTo("Nike");
        assertThat(r.activa()).isTrue();
        assertThat(r.colores()).containsExactly("Blanco", "Negro");
        assertThat(r.tallas()).containsExactly("38", "38.5");
        assertThat(r.precios()).hasSize(2);
        assertThat(r.precios().get(0).calidadId()).isEqualTo(1L);
        assertThat(r.precios().get(0).costo()).isEqualByComparingTo("250000");
        assertThat(r.precios().get(0).precioMinimo()).isEqualByComparingTo("330000");
        assertThat(r.precios().get(0).precioSugerido()).isEqualByComparingTo("380000");
        assertThat(r.precios().get(1).calidadId()).isEqualTo(3L);
        assertThat(r.precios().get(1).precioSugerido()).isEqualByComparingTo("190000");
    }

    @Test
    void laReferenciaQuedaEnElNegocioActualYConSusPreciosAsociados() {
        service.crear(valida());

        Referencia guardada = referenciaGuardada();
        assertThat(guardada.getNegocioId()).isEqualTo(1L);
        assertThat(guardada.getCodigo()).isEqualTo("AF1-001");
        assertThat(guardada.getColores()).hasSize(2);
        assertThat(guardada.getTallas()).hasSize(2);
        assertThat(guardada.getPrecios()).hasSize(2);
        assertThat(guardada.getPrecios()).allSatisfy(p -> {
            assertThat(p.getReferencia()).isSameAs(guardada);
            assertThat(p.getCalidad()).isNotNull();
        });
    }

    @Test
    void registrarUnaReferenciaNoCreaCombinacionesNiTocaCalidadesNiTallas() {
        // HU-05: no se crean variantes ni stock; solo se guarda la referencia (y sus colores nuevos).
        service.crear(valida());

        verify(referenciaRepository, times(1)).save(any(Referencia.class));
        verify(referenciaRepository, times(1)).saveAndFlush(any(Referencia.class));
        verify(calidadRepository, never()).save(any());
        verify(tallaRepository, never()).save(any());
    }

    @Test
    void laMarcaEsOpcional() {
        service.crear(con("X-1", "Modelo", " ", null, List.of("Negro"), List.of("40"), preciosValidos()));

        Referencia guardada = referenciaGuardada();
        assertThat(guardada.getMarca()).isNull();
    }

    @Test
    void recortaEspaciosDelCodigoYDelNombre() {
        service.crear(con("  AF1-002  ", "  Nike Dunk ", "Nike", "Tenis",
                List.of("Negro"), List.of("40"), preciosValidos()));

        Referencia guardada = referenciaGuardada();
        assertThat(guardada.getCodigo()).isEqualTo("AF1-002");
        assertThat(guardada.getNombre()).isEqualTo("Nike Dunk");
        verify(referenciaRepository).existsByNegocioIdAndCodigoIgnoreCase(1L, "AF1-002");
    }

    // ---------- codigo unico ----------

    @Test
    void rechazaUnaReferenciaConCodigoRepetido() {
        when(referenciaRepository.existsByNegocioIdAndCodigoIgnoreCase(1L, "AF1-001")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(valida()))
                .isInstanceOf(ReferenciaDuplicadaException.class)
                .hasMessageContaining("AF1-001");
        verify(referenciaRepository, never()).save(any());
        verify(colorRepository, never()).save(any());
    }

    @Test
    void codigoYNombreSonObligatorios() {
        assertRechaza(con(null, "N", null, null, List.of("Negro"), List.of("40"), preciosValidos()), "referencia");
        assertRechaza(con("  ", "N", null, null, List.of("Negro"), List.of("40"), preciosValidos()), "referencia");
        assertRechaza(con("A-1", null, null, null, List.of("Negro"), List.of("40"), preciosValidos()), "nombre");
        assertRechaza(con("A-1", " ", null, null, List.of("Negro"), List.of("40"), preciosValidos()), "nombre");
    }

    @Test
    void rechazaTextosMasLargosQueLasColumnas() {
        assertRechaza(con("x".repeat(41), "N", null, null, List.of("Negro"), List.of("40"), preciosValidos()), "40");
        assertRechaza(con("A-1", "x".repeat(101), null, null, List.of("Negro"), List.of("40"), preciosValidos()), "100");
        assertRechaza(con("A-1", "N", "x".repeat(61), null, List.of("Negro"), List.of("40"), preciosValidos()), "60");
    }

    // ---------- colores ----------

    @Test
    void exigeAlMenosUnColor() {
        assertRechaza(con("A-1", "N", null, null, null, List.of("40"), preciosValidos()), "al menos un color");
        assertRechaza(con("A-1", "N", null, null, List.of(), List.of("40"), preciosValidos()), "al menos un color");
        assertRechaza(con("A-1", "N", null, null, List.of(" "), List.of("40"), preciosValidos()), "color vacío");
    }

    @Test
    void creaElColorSiNoExisteYReutilizaElQueYaExiste() {
        Color existente = new Color();
        existente.setNegocioId(1L);
        existente.setNombre("Negro");
        when(colorRepository.findByNegocioIdAndNombreIgnoreCase(1L, "Negro")).thenReturn(Optional.of(existente));

        service.crear(con("A-1", "N", null, null, List.of("Negro", "Verde"), List.of("40"), preciosValidos()));

        ArgumentCaptor<Color> captor = ArgumentCaptor.forClass(Color.class);
        verify(colorRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("Verde");
        assertThat(captor.getValue().getNegocioId()).isEqualTo(1L);
        assertThat(referenciaGuardada().getColores()).hasSize(2).contains(existente);
    }

    @Test
    void ignoraColoresRepetidosEnLaListaSinDistinguirMayusculas() {
        service.crear(con("A-1", "N", null, null, List.of("Blanco", "blanco", " BLANCO "),
                List.of("40"), preciosValidos()));

        assertThat(referenciaGuardada().getColores()).hasSize(1);
        verify(colorRepository, times(1)).save(any(Color.class));
    }

    // ---------- tallas ----------

    @Test
    void exigeAlMenosUnaTalla() {
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), null, preciosValidos()), "al menos una talla");
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of(), preciosValidos()), "al menos una talla");
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of(" "), preciosValidos()), "talla vacía");
    }

    @Test
    void soloAceptaTallasEuropeasDel34Al46() {
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of("33.5"), preciosValidos()), "no es válida");
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of("46.5"), preciosValidos()), "no es válida");
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of("50"), preciosValidos()), "no es válida");
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of("38.3"), preciosValidos()), "no es válida");
    }

    @Test
    void rechazaTallasQueNoSonNumeros() {
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of("abc"), preciosValidos()),
                "no es un número válido");
    }

    @Test
    void aceptaTallaConComaDecimalYLosExtremos() {
        service.crear(con("A-1", "N", null, null, List.of("Negro"), List.of("34", "38,5", "46"), preciosValidos()));

        assertThat(referenciaGuardada().getTallas()).extracting(Talla::comoTexto)
                .containsExactlyInAnyOrder("34", "38.5", "46");
    }

    // ---------- precios por calidad ----------

    @Test
    void exigeAlMenosUnaCalidadConPrecios() {
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of("40"), null), "al menos una calidad");
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of("40"), List.of()), "al menos una calidad");
    }

    @Test
    void cadaPrecioDebeIndicarSuCalidad() {
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of("40"),
                List.of(precio(null, "1", "2", "3"))), "indicar su calidad");
    }

    @Test
    void noPermiteLaMismaCalidadDosVeces() {
        assertRechaza(con("A-1", "N", null, null, List.of("Negro"), List.of("40"),
                List.of(precio(1L, "1", "2", "3"), precio(1L, "1", "2", "3"))), "repetida");
    }

    @Test
    void costoPrecioMinimoYSugeridoSonObligatorios() {
        assertRechaza(unPrecio(precio(1L, null, "2", "3")), "costo es obligatorio");
        assertRechaza(unPrecio(precio(1L, "1", null, "3")), "precio mínimo es obligatorio");
        assertRechaza(unPrecio(precio(1L, "1", "2", null)), "precio sugerido es obligatorio");
    }

    @Test
    void rechazaValoresNegativosOEnormes() {
        assertRechaza(unPrecio(precio(1L, "-1", "2", "3")), "no puede ser negativo");
        assertRechaza(unPrecio(precio(1L, "1", "-2", "3")), "no puede ser negativo");
        assertRechaza(unPrecio(precio(1L, "1", "2", "-3")), "no puede ser negativo");
        assertRechaza(unPrecio(precio(1L, "1", "2", "10000000000")), "demasiado grande");
    }

    @Test
    void elPrecioMinimoNoPuedeSerMayorQueElSugerido() {
        assertRechaza(unPrecio(precio(1L, "100", "500", "400")), "mayor que el precio sugerido");
    }

    @Test
    void permiteMinimoIgualAlSugeridoYPreciosDistintosPorCalidad() {
        CatalogoResponse r = service.crear(con("A-1", "N", null, null, List.of("Negro"), List.of("40"),
                List.of(precio(1L, "100", "300", "300"), precio(3L, "50", "80", "120"))));

        assertThat(r.precios()).extracting(p -> p.precioSugerido().intValue()).containsExactly(300, 120);
    }

    // Las calidades se validan ANTES de guardar: si una no existe (o es de otro negocio)
    // no se guarda la referencia ni se crean colores nuevos.
    @Test
    void rechazaUnaCalidadQueNoExisteSinGuardarNada() {
        assertThatThrownBy(() -> service.crear(unPrecio(precio(5L, "1", "2", "3"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
        verify(referenciaRepository, never()).save(any());
        verify(referenciaRepository, never()).saveAndFlush(any());
        verify(colorRepository, never()).save(any());
    }

    @Test
    void rechazaUnaCalidadDeOtroNegocioSinGuardarNada() {
        assertThatThrownBy(() -> service.crear(unPrecio(precio(9L, "1", "2", "3"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
        verify(referenciaRepository, never()).save(any());
        verify(colorRepository, never()).save(any());
    }

    @Test
    void unaTallaInvalidaTampocoCreaColoresNuevos() {
        assertRechaza(con("A-1", "N", null, null, List.of("Fucsia"), List.of("50"), preciosValidos()), "no es válida");
        verify(colorRepository, never()).save(any());
    }

    private CatalogoRequest unPrecio(PrecioRequest p) {
        return con("A-1", "N", null, null, List.of("Negro"), List.of("40"), List.of(p));
    }

    // ---------- listar ----------

    @Test
    void listarSoloConsultaLasReferenciasDelNegocioActual() {
        Referencia ref = new Referencia();
        ref.setId(1L);
        ref.setNegocioId(1L);
        ref.setCodigo("AF1-001");
        ref.setNombre("Nike Air Force");
        ref.setActiva(true);
        when(referenciaRepository.findByNegocioIdOrderByCodigoAsc(1L)).thenReturn(List.of(ref));

        List<CatalogoResponse> lista = service.listar();

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).referencia()).isEqualTo("AF1-001");
        assertThat(lista.get(0).colores()).isEmpty();
        verify(referenciaRepository, never()).findAll();
    }

    // ---------- listas para los desplegables ----------

    @Test
    void listaLosColoresDelNegocioComoOpciones() {
        Color blanco = new Color();
        blanco.setId(4L);
        blanco.setNegocioId(1L);
        blanco.setNombre("Blanco");
        when(colorRepository.findByNegocioIdOrderByNombreAsc(1L)).thenReturn(List.of(blanco));

        assertThat(service.listarColores()).containsExactly(new OpcionResponse(4L, "Blanco"));
    }

    @Test
    void listaLasTallasComoTextoSinCerosDeSobra() {
        when(tallaRepository.findAllByOrderByValorAsc()).thenReturn(tablaTallas);

        List<OpcionResponse> tallas = service.listarTallas();

        assertThat(tallas).hasSize(25);
        assertThat(tallas.get(0).nombre()).isEqualTo("34");
        assertThat(tallas.get(1).nombre()).isEqualTo("34.5");
        assertThat(tallas.get(24).nombre()).isEqualTo("46");
    }

    // ---------- editar (PUT /api/catalogo/{id}) ----------

    // Referencia ya guardada: id 10, "AF1-001", color Blanco, talla 38 y precio para la calidad 1
    private Referencia existente() {
        Referencia ref = new Referencia();
        ref.setId(10L);
        ref.setNegocioId(1L);
        ref.setCodigo("AF1-001");
        ref.setNombre("Nike Air Force");
        ref.setMarca("Nike");
        ref.setActiva(true);
        Color blanco = new Color();
        blanco.setNegocioId(1L);
        blanco.setNombre("Blanco");
        ref.getColores().add(blanco);
        ref.getTallas().add(tablaTallas.get(8)); // 38
        ReferenciaCalidad rc = new ReferenciaCalidad();
        rc.setId(new ReferenciaCalidadId(10L, 1L));
        rc.setReferencia(ref);
        rc.setCalidad(calidad(1L, 1L, "Original"));
        rc.setCostoAdquisicion(new BigDecimal("100"));
        rc.setPrecioMinimo(new BigDecimal("150"));
        rc.setPrecioSugerido(new BigDecimal("200"));
        ref.getPrecios().add(rc);
        when(referenciaRepository.findByIdAndNegocioId(10L, 1L)).thenReturn(Optional.of(ref));
        return ref;
    }

    @Test
    void editarActualizaDatosColoresTallasYPrecios() {
        Referencia ref = existente();

        CatalogoResponse r = service.editar(10L, con("AF1-002", "Nike AF1 Low", "Nike", null,
                List.of("Negro", "Rojo"), List.of("40", "40.5"),
                List.of(precio(1L, "110", "160", "210"), precio(3L, "50", "70", "90"))));

        assertThat(r.id()).isEqualTo(10L);
        assertThat(r.referencia()).isEqualTo("AF1-002");
        assertThat(r.nombre()).isEqualTo("Nike AF1 Low");
        assertThat(r.colores()).containsExactly("Negro", "Rojo");
        assertThat(r.tallas()).containsExactly("40", "40.5");
        assertThat(r.precios()).hasSize(2);
        assertThat(r.precios().get(0).calidadId()).isEqualTo(1L);
        assertThat(r.precios().get(0).costo()).isEqualByComparingTo("110");
        assertThat(r.precios().get(1).calidadId()).isEqualTo(3L);
        assertThat(r.precios().get(1).precioSugerido()).isEqualByComparingTo("90");
        // El precio de la calidad 1 se modifico EN SU SITIO (mismo objeto, misma llave)
        assertThat(ref.getPrecios()).anySatisfy(p -> assertThat(p.getId()).isEqualTo(new ReferenciaCalidadId(10L, 1L)));
        verify(referenciaRepository).saveAndFlush(ref);
        verify(referenciaRepository, never()).save(any());
    }

    @Test
    void editarQuitaLasCalidadesQueYaNoVienen() {
        Referencia ref = existente();

        service.editar(10L, con("AF1-001", "Nike Air Force", "Nike", null,
                List.of("Blanco"), List.of("38"), List.of(precio(3L, "50", "70", "90"))));

        assertThat(ref.getPrecios()).hasSize(1);
        assertThat(ref.getPrecios().get(0).getCalidad().getId()).isEqualTo(3L);
        assertThat(ref.getPrecios().get(0).getId()).isEqualTo(new ReferenciaCalidadId(10L, 3L));
    }

    @Test
    void editarPermiteDejarElMismoCodigoPorqueSeIgnoraElPropioRegistro() {
        existente();

        service.editar(10L, con("af1-001", "Nike Air Force", "Nike", null,
                List.of("Blanco"), List.of("38"), preciosValidos()));

        verify(referenciaRepository).existsByNegocioIdAndCodigoIgnoreCaseAndIdNot(1L, "af1-001", 10L);
        verify(referenciaRepository, never()).existsByNegocioIdAndCodigoIgnoreCase(any(), any());
    }

    @Test
    void editarRechazaUnCodigoQueTieneOtraReferencia() {
        Referencia ref = existente();
        when(referenciaRepository.existsByNegocioIdAndCodigoIgnoreCaseAndIdNot(1L, "AF1-999", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.editar(10L, con("AF1-999", "N", null, null,
                List.of("Negro"), List.of("40"), preciosValidos())))
                .isInstanceOf(ReferenciaDuplicadaException.class)
                .hasMessageContaining("AF1-999");
        assertThat(ref.getCodigo()).isEqualTo("AF1-001");
        verify(referenciaRepository, never()).saveAndFlush(any());
    }

    @Test
    void editarConDatosInvalidosNoCambiaNada() {
        Referencia ref = existente();

        assertThatThrownBy(() -> service.editar(10L, con("AF1-001", "Otro nombre", "Nike", null,
                List.of("Morado"), List.of("50"), preciosValidos())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es válida");
        assertThatThrownBy(() -> service.editar(10L, con("AF1-001", "Otro nombre", "Nike", null,
                List.of("Morado"), List.of("40"), List.of(precio(5L, "1", "2", "3")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");

        assertThat(ref.getNombre()).isEqualTo("Nike Air Force");
        assertThat(ref.getPrecios()).hasSize(1);
        assertThat(ref.getPrecios().get(0).getCostoAdquisicion()).isEqualByComparingTo("100");
        verify(colorRepository, never()).save(any());
        verify(referenciaRepository, never()).saveAndFlush(any());
    }

    @Test
    void editarUnaReferenciaQueNoExisteDa404() {
        when(referenciaRepository.findByIdAndNegocioId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(99L, valida()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró la referencia.");
        verify(referenciaRepository, never()).saveAndFlush(any());
    }

    // ---------- Sprint 2: no quitar opciones que tienen stock ----------

    @Test
    void editarNoDejaQuitarUnColorQueTienePares() {
        Referencia ref = existente();
        ref.getColores().iterator().next().setId(7L); // Blanco
        when(varianteRepository.stockPorColor(10L)).thenReturn(List.of(new StockPorOpcion(7L, 12L)));

        assertThatThrownBy(() -> service.editar(10L, con("AF1-001", "Nike Air Force", "Nike", null,
                List.of("Negro"), List.of("38"), List.of(precio(1L, "100", "150", "200")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes quitar el color Blanco: hay 12 pares en inventario.");
        assertThat(ref.getColores()).extracting(Color::getNombre).containsExactly("Blanco");
        verify(colorRepository, never()).save(any()); // ni siquiera alcanza a crear "Negro"
        verify(referenciaRepository, never()).saveAndFlush(any());
    }

    @Test
    void editarNoDejaQuitarUnaTallaQueTienePares() {
        existente();
        Long talla38 = tablaTallas.get(8).getId();
        when(varianteRepository.stockPorTalla(10L)).thenReturn(List.of(new StockPorOpcion(talla38, 1L)));

        assertThatThrownBy(() -> service.editar(10L, con("AF1-001", "Nike Air Force", "Nike", null,
                List.of("Blanco"), List.of("40"), List.of(precio(1L, "100", "150", "200")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes quitar la talla 38: hay 1 par en inventario.");
        verify(referenciaRepository, never()).saveAndFlush(any());
    }

    @Test
    void editarNoDejaQuitarUnaCalidadQueTienePares() {
        existente();
        when(varianteRepository.stockPorCalidad(10L)).thenReturn(List.of(new StockPorOpcion(1L, 3L)));

        assertThatThrownBy(() -> service.editar(10L, con("AF1-001", "Nike Air Force", "Nike", null,
                List.of("Blanco"), List.of("38"), List.of(precio(3L, "50", "70", "90")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes quitar la calidad Original: hay 3 pares en inventario.");
        verify(referenciaRepository, never()).saveAndFlush(any());
    }

    @Test
    void editarSiDejaQuitarOpcionesSinPares() {
        Referencia ref = existente();
        ref.getColores().iterator().next().setId(7L);
        // Hay stock, pero de OTRO color (8): quitar Blanco (7) si se puede
        when(varianteRepository.stockPorColor(10L)).thenReturn(List.of(new StockPorOpcion(8L, 5L)));

        service.editar(10L, con("AF1-001", "Nike Air Force", "Nike", null,
                List.of("Negro"), List.of("38"), List.of(precio(1L, "100", "150", "200"))));

        verify(referenciaRepository).saveAndFlush(ref);
    }

    @Test
    void siNoSeQuitaNadaNoConsultaElInventario() {
        Referencia ref = existente();
        ref.getColores().iterator().next().setId(7L);

        service.editar(10L, con("AF1-001", "Nike Air Force", "Nike", null,
                List.of("blanco", "Rojo"), List.of("38", "39"), preciosValidos()));

        verify(varianteRepository, never()).stockPorColor(any());
        verify(varianteRepository, never()).stockPorTalla(any());
        verify(varianteRepository, never()).stockPorCalidad(any());
    }
}
