package com.zapgest.backend.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zapgest.backend.dto.catalogo.CatalogoRequest;
import com.zapgest.backend.dto.catalogo.CatalogoResponse;
import com.zapgest.backend.dto.catalogo.OpcionResponse;
import com.zapgest.backend.dto.catalogo.OpcionesReferenciaResponse;
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

// HU-05: registrar referencias con sus colores, tallas y precios por calidad.
// Importante: guardar una referencia NO crea stock ni combinaciones color x talla x calidad.
@Service
public class CatalogoService {

    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1

    private final ReferenciaRepository referenciaRepository;
    private final ColorRepository colorRepository;
    private final TallaRepository tallaRepository;
    private final CalidadRepository calidadRepository;
    private final VarianteRepository varianteRepository;

    public CatalogoService(ReferenciaRepository referenciaRepository, ColorRepository colorRepository,
            TallaRepository tallaRepository, CalidadRepository calidadRepository,
            VarianteRepository varianteRepository) {
        this.referenciaRepository = referenciaRepository;
        this.colorRepository = colorRepository;
        this.tallaRepository = tallaRepository;
        this.calidadRepository = calidadRepository;
        this.varianteRepository = varianteRepository;
    }

    // HU-07: colores, tallas y calidades de UNA referencia, para la pantalla de ingreso de mercancia.
    // Sin costos ni precios. Ordenados para que los desplegables salgan en orden.
    @Transactional(readOnly = true)
    public OpcionesReferenciaResponse opciones(Long id) {
        Referencia referencia = referenciaRepository.findByIdAndNegocioId(id, NEGOCIO_ID)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la referencia."));

        List<OpcionResponse> colores = referencia.getColores().stream()
                .sorted(java.util.Comparator.comparing(Color::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(c -> new OpcionResponse(c.getId(), c.getNombre()))
                .toList();
        List<OpcionResponse> tallas = referencia.getTallas().stream()
                .sorted(java.util.Comparator.comparing(Talla::getValor))
                .map(t -> new OpcionResponse(t.getId(), t.comoTexto()))
                .toList();
        List<OpcionResponse> calidades = referencia.getPrecios().stream()
                .map(ReferenciaCalidad::getCalidad)
                .sorted(java.util.Comparator.comparing(Calidad::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(c -> new OpcionResponse(c.getId(), c.getNombre()))
                .toList();

        return new OpcionesReferenciaResponse(referencia.getId(), referencia.getCodigo(), referencia.getNombre(),
                referencia.getMarca(), colores, tallas, calidades);
    }

    @Transactional(readOnly = true)
    public List<CatalogoResponse> listar() {
        return referenciaRepository.findByNegocioIdOrderByCodigoAsc(NEGOCIO_ID).stream()
                .map(CatalogoResponse::desde)
                .toList();
    }

    // Colores ya guardados del negocio (para los desplegables de la pantalla)
    @Transactional(readOnly = true)
    public List<OpcionResponse> listarColores() {
        return colorRepository.findByNegocioIdOrderByNombreAsc(NEGOCIO_ID).stream()
                .map(c -> new OpcionResponse(c.getId(), c.getNombre()))
                .toList();
    }

    // Tallas europeas permitidas (34 a 46), en orden
    @Transactional(readOnly = true)
    public List<OpcionResponse> listarTallas() {
        return tallaRepository.findAllByOrderByValorAsc().stream()
                .map(t -> new OpcionResponse(t.getId(), t.comoTexto()))
                .toList();
    }

    @Transactional
    public CatalogoResponse crear(CatalogoRequest datos) {
        if (datos == null) {
            throw new IllegalArgumentException("Faltan los datos de la referencia.");
        }

        // 1. Datos basicos
        String codigo = obligatorio(datos.referencia(), "La referencia (código) es obligatoria.", 40);
        String nombre = obligatorio(datos.nombre(), "El nombre es obligatorio.", 100);
        String marca = opcional(datos.marca(), "La marca", 60);

        // 2. Referencia unica dentro del negocio
        if (referenciaRepository.existsByNegocioIdAndCodigoIgnoreCase(NEGOCIO_ID, codigo)) {
            throw new ReferenciaDuplicadaException(codigo);
        }

        // 3. Validar TODO antes de guardar nada.
        //    Primero lo que solo se consulta (tallas, precios y calidades) y al final los
        //    colores, porque un color nuevo si se guarda en la base.
        Set<Talla> tallas = resolverTallas(datos.tallas());
        List<PrecioConCalidad> precios = validarPrecios(datos.precios());
        Set<Color> colores = resolverColores(datos.colores());

        // 4. Guardar la referencia
        Referencia referencia = new Referencia();
        referencia.setNegocioId(NEGOCIO_ID);
        referencia.setCodigo(codigo);
        referencia.setNombre(nombre);
        referencia.setMarca(marca);
        referencia.setActiva(true);
        referencia.setColores(colores);
        referencia.setTallas(tallas);
        referencia = referenciaRepository.save(referencia);

        // 5. Asociar cada precio con su calidad (ya validada en el paso 3)
        for (PrecioConCalidad pc : precios) {
            PrecioRequest p = pc.precio();
            Calidad calidad = pc.calidad();

            ReferenciaCalidad rc = new ReferenciaCalidad();
            rc.setId(new ReferenciaCalidadId(referencia.getId(), calidad.getId()));
            rc.setReferencia(referencia);
            rc.setCalidad(calidad);
            rc.setCostoAdquisicion(p.costo());
            rc.setPrecioMinimo(p.precioMinimo());
            rc.setPrecioSugerido(p.precioSugerido());
            referencia.getPrecios().add(rc);
        }
        referencia = referenciaRepository.saveAndFlush(referencia);

        return CatalogoResponse.desde(referencia);
    }

    // PUT /api/catalogo/{id}: recibe el mismo cuerpo que el POST y deja la referencia
    // exactamente como llega (datos, colores, tallas y precios por calidad).
    // Decisiones del grupo:
    //  - El codigo SI se puede cambiar, siempre que no lo tenga OTRA referencia (409).
    //  - Quitar colores, tallas o calidades se permite SOLO si no tienen pares en inventario
    //    (Sprint 2). Ver verificarQueSePuedenQuitar().
    @Transactional
    public CatalogoResponse editar(Long id, CatalogoRequest datos) {
        Referencia referencia = referenciaRepository.findByIdAndNegocioId(id, NEGOCIO_ID)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la referencia."));
        if (datos == null) {
            throw new IllegalArgumentException("Faltan los datos de la referencia.");
        }

        // 1. Datos basicos (mismas reglas que al crear)
        String codigo = obligatorio(datos.referencia(), "La referencia (código) es obligatoria.", 40);
        String nombre = obligatorio(datos.nombre(), "El nombre es obligatorio.", 100);
        String marca = opcional(datos.marca(), "La marca", 60);

        // 2. Codigo unico ignorando la propia referencia
        if (referenciaRepository.existsByNegocioIdAndCodigoIgnoreCaseAndIdNot(NEGOCIO_ID, codigo, id)) {
            throw new ReferenciaDuplicadaException(codigo);
        }

        // 3. Validar TODO antes de cambiar nada (los colores al final porque crean registros)
        //    La regla de stock va ANTES de resolver colores, que puede crear colores nuevos.
        Set<Talla> tallas = resolverTallas(datos.tallas());
        List<PrecioConCalidad> precios = validarPrecios(datos.precios());
        verificarQueSePuedenQuitar(referencia, datos.colores(), tallas, precios);
        Set<Color> colores = resolverColores(datos.colores());

        // 4. Datos basicos, colores y tallas
        referencia.setCodigo(codigo);
        referencia.setNombre(nombre);
        referencia.setMarca(marca);
        referencia.getColores().clear();
        referencia.getColores().addAll(colores);
        referencia.getTallas().clear();
        referencia.getTallas().addAll(tallas);

        // 5. Precios: se actualizan los de las calidades que siguen, se quitan los que ya
        //    no vienen y se agregan los nuevos. Los existentes se modifican en su sitio
        //    (no se reemplazan) porque su llave es (referencia, calidad).
        Map<Long, PrecioConCalidad> pedidos = precios.stream()
                .collect(Collectors.toMap(pc -> pc.calidad().getId(), Function.identity()));
        referencia.getPrecios().removeIf(rc -> !pedidos.containsKey(rc.getCalidad().getId()));
        for (ReferenciaCalidad rc : referencia.getPrecios()) {
            copiarPrecio(pedidos.remove(rc.getCalidad().getId()).precio(), rc);
        }
        for (PrecioConCalidad pc : pedidos.values()) {
            ReferenciaCalidad rc = new ReferenciaCalidad();
            rc.setId(new ReferenciaCalidadId(referencia.getId(), pc.calidad().getId()));
            rc.setReferencia(referencia);
            rc.setCalidad(pc.calidad());
            copiarPrecio(pc.precio(), rc);
            referencia.getPrecios().add(rc);
        }

        return CatalogoResponse.desde(referenciaRepository.saveAndFlush(referencia));
    }

    // Sprint 2: no se puede quitar de una referencia un color, una talla o una calidad
    // que todavia tiene pares en inventario (quedarian "huerfanos"). Con stock en cero si se puede.
    // Solo consulta el inventario si de verdad se esta quitando algo.
    private void verificarQueSePuedenQuitar(Referencia referencia, List<String> nombresColores,
            Set<Talla> tallas, List<PrecioConCalidad> precios) {
        Set<String> coloresPedidos = new HashSet<>();
        for (String n : nombresColores) {
            if (n != null) {
                coloresPedidos.add(n.trim().replaceAll("\\s+", " ").toLowerCase());
            }
        }
        List<Color> coloresQuitados = referencia.getColores().stream()
                .filter(c -> c.getId() != null && !coloresPedidos.contains(c.getNombre().toLowerCase()))
                .toList();

        Set<Long> tallasPedidas = new HashSet<>();
        tallas.forEach(t -> tallasPedidas.add(t.getId()));
        List<Talla> tallasQuitadas = referencia.getTallas().stream()
                .filter(t -> !tallasPedidas.contains(t.getId()))
                .toList();

        Set<Long> calidadesPedidas = new HashSet<>();
        precios.forEach(p -> calidadesPedidas.add(p.calidad().getId()));
        List<Calidad> calidadesQuitadas = referencia.getPrecios().stream()
                .map(ReferenciaCalidad::getCalidad)
                .filter(c -> !calidadesPedidas.contains(c.getId()))
                .toList();

        if (!coloresQuitados.isEmpty()) {
            Map<Long, Long> stock = aMapa(varianteRepository.stockPorColor(referencia.getId()));
            for (Color c : coloresQuitados) {
                rechazarSiHayStock(stock.get(c.getId()), "el color " + c.getNombre());
            }
        }
        if (!tallasQuitadas.isEmpty()) {
            Map<Long, Long> stock = aMapa(varianteRepository.stockPorTalla(referencia.getId()));
            for (Talla t : tallasQuitadas) {
                rechazarSiHayStock(stock.get(t.getId()), "la talla " + t.comoTexto());
            }
        }
        if (!calidadesQuitadas.isEmpty()) {
            Map<Long, Long> stock = aMapa(varianteRepository.stockPorCalidad(referencia.getId()));
            for (Calidad c : calidadesQuitadas) {
                rechazarSiHayStock(stock.get(c.getId()), "la calidad " + c.getNombre());
            }
        }
    }

    private static Map<Long, Long> aMapa(List<StockPorOpcion> filas) {
        return filas.stream().collect(Collectors.toMap(StockPorOpcion::opcionId, StockPorOpcion::cantidad));
    }

    private static void rechazarSiHayStock(Long pares, String queSeQuita) {
        if (pares != null && pares > 0) {
            throw new IllegalArgumentException("No puedes quitar " + queSeQuita + ": hay " + pares
                    + (pares == 1 ? " par" : " pares") + " en inventario.");
        }
    }

    private void copiarPrecio(PrecioRequest p, ReferenciaCalidad rc) {
        rc.setCostoAdquisicion(p.costo());
        rc.setPrecioMinimo(p.precioMinimo());
        rc.setPrecioSugerido(p.precioSugerido());
    }

    // ===== Validaciones =====

    private Set<Color> resolverColores(List<String> nombres) {
        if (nombres == null || nombres.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un color.");
        }
        Set<Color> resultado = new LinkedHashSet<>();
        Set<String> vistos = new HashSet<>();
        for (String n : nombres) {
            String nombre = obligatorio(n, "Hay un color vacío.", 40);
            if (!vistos.add(nombre.toLowerCase())) {
                continue; // color repetido en la lista: se ignora
            }
            // Si el color no existe en el negocio, se crea (opcion "Añadir otro" de la pantalla)
            Color color = colorRepository.findByNegocioIdAndNombreIgnoreCase(NEGOCIO_ID, nombre)
                    .orElseGet(() -> {
                        Color nuevo = new Color();
                        nuevo.setNegocioId(NEGOCIO_ID);
                        nuevo.setNombre(nombre);
                        return colorRepository.save(nuevo);
                    });
            resultado.add(color);
        }
        return resultado;
    }

    private Set<Talla> resolverTallas(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una talla.");
        }
        Set<Talla> resultado = new LinkedHashSet<>();
        for (String v : valores) {
            String texto = obligatorio(v, "Hay una talla vacía.", 10).replace(',', '.');
            BigDecimal valor;
            try {
                valor = new BigDecimal(texto);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La talla \"" + v + "\" no es un número válido.");
            }
            // Solo se aceptan las tallas europeas de la tabla talla (34 a 46, de media en media)
            Talla talla = tallaRepository.findByValor(valor)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La talla " + texto + " no es válida. Usa tallas europeas de 34 a 46."));
            resultado.add(talla);
        }
        return resultado;
    }

    // Un precio ya validado junto con su calidad (que existe y es del negocio)
    private record PrecioConCalidad(PrecioRequest precio, Calidad calidad) {
    }

    private List<PrecioConCalidad> validarPrecios(List<PrecioRequest> precios) {
        if (precios == null || precios.isEmpty()) {
            throw new IllegalArgumentException("Agrega los precios de al menos una calidad.");
        }
        Set<Long> calidades = new HashSet<>();
        List<PrecioConCalidad> resultado = new ArrayList<>();
        for (PrecioRequest p : precios) {
            if (p == null || p.calidadId() == null) {
                throw new IllegalArgumentException("Cada precio debe indicar su calidad.");
            }
            if (!calidades.add(p.calidadId())) {
                throw new IllegalArgumentException("La calidad con id " + p.calidadId() + " está repetida.");
            }
            noNegativo(p.costo(), "El costo");
            noNegativo(p.precioMinimo(), "El precio mínimo");
            noNegativo(p.precioSugerido(), "El precio sugerido");
            if (p.precioMinimo().compareTo(p.precioSugerido()) > 0) {
                throw new IllegalArgumentException("El precio mínimo no puede ser mayor que el precio sugerido.");
            }
            // La calidad debe existir y ser del negocio (se comprueba ANTES de guardar)
            Calidad calidad = calidadRepository.findById(p.calidadId())
                    .filter(c -> NEGOCIO_ID.equals(c.getNegocioId()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La calidad con id " + p.calidadId() + " no existe."));
            resultado.add(new PrecioConCalidad(p, calidad));
        }
        return resultado;
    }

    private void noNegativo(BigDecimal valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException(campo + " es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(campo + " no puede ser negativo.");
        }
        if (valor.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException(campo + " es demasiado grande.");
        }
    }

    private String obligatorio(String texto, String mensaje, int maximo) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        String limpio = texto.trim();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("El valor \"" + limpio + "\" supera los " + maximo + " caracteres.");
        }
        return limpio;
    }

    private String opcional(String texto, String campo, int maximo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException(campo + " supera los " + maximo + " caracteres.");
        }
        return limpio;
    }
}
