package com.zapgest.backend.services;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zapgest.backend.dto.catalogo.CatalogoRequest;
import com.zapgest.backend.dto.catalogo.CatalogoResponse;
import com.zapgest.backend.dto.catalogo.PrecioRequest;
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
import com.zapgest.backend.repositories.TallaRepository;

// HU-05: registrar referencias con sus colores, tallas y precios por calidad.
// Importante: guardar una referencia NO crea stock ni combinaciones color x talla x calidad.
@Service
public class CatalogoService {

    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1

    private final ReferenciaRepository referenciaRepository;
    private final ColorRepository colorRepository;
    private final TallaRepository tallaRepository;
    private final CalidadRepository calidadRepository;

    public CatalogoService(ReferenciaRepository referenciaRepository, ColorRepository colorRepository,
            TallaRepository tallaRepository, CalidadRepository calidadRepository) {
        this.referenciaRepository = referenciaRepository;
        this.colorRepository = colorRepository;
        this.tallaRepository = tallaRepository;
        this.calidadRepository = calidadRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogoResponse> listar() {
        return referenciaRepository.findByNegocioIdOrderByCodigoAsc(NEGOCIO_ID).stream()
                .map(CatalogoResponse::desde)
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
        String categoria = opcional(datos.categoria(), "La categoría", 60);

        // 2. Referencia unica dentro del negocio
        if (referenciaRepository.existsByNegocioIdAndCodigoIgnoreCase(NEGOCIO_ID, codigo)) {
            throw new ReferenciaDuplicadaException(codigo);
        }

        // 3. Colores, tallas y precios (se validan antes de guardar nada)
        Set<Color> colores = resolverColores(datos.colores());
        Set<Talla> tallas = resolverTallas(datos.tallas());
        List<PrecioRequest> precios = validarPrecios(datos.precios());

        // 4. Guardar la referencia
        Referencia referencia = new Referencia();
        referencia.setNegocioId(NEGOCIO_ID);
        referencia.setCodigo(codigo);
        referencia.setNombre(nombre);
        referencia.setMarca(marca);
        referencia.setCategoria(categoria);
        referencia.setActiva(true);
        referencia.setColores(colores);
        referencia.setTallas(tallas);
        referencia = referenciaRepository.save(referencia);

        // 5. Asociar cada precio con su calidad
        for (PrecioRequest p : precios) {
            Calidad calidad = calidadRepository.findById(p.calidadId())
                    .filter(c -> NEGOCIO_ID.equals(c.getNegocioId()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La calidad con id " + p.calidadId() + " no existe."));

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

    private List<PrecioRequest> validarPrecios(List<PrecioRequest> precios) {
        if (precios == null || precios.isEmpty()) {
            throw new IllegalArgumentException("Agrega los precios de al menos una calidad.");
        }
        Set<Long> calidades = new HashSet<>();
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
        }
        return precios;
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
