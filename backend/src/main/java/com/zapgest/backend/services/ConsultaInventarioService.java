package com.zapgest.backend.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zapgest.backend.dto.inventario.ResultadoBusquedaInventario;
import com.zapgest.backend.dto.inventario.UbicacionCantidadResponse;
import com.zapgest.backend.dto.inventario.VarianteDisponibleResponse;
import com.zapgest.backend.models.Inventario;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.InventarioBusquedaRepository;
import com.zapgest.backend.repositories.InventarioRepository;

// HU-09 y HU-10: buscar variantes, ver su total y en que ubicaciones estan.
@Service
public class ConsultaInventarioService {

    static final int TAMANO_POR_DEFECTO = 50;
    static final int TAMANO_MAXIMO = 100;
    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1

    private final InventarioBusquedaRepository busquedaRepository;
    private final InventarioRepository inventarioRepository;

    public ConsultaInventarioService(InventarioBusquedaRepository busquedaRepository,
            InventarioRepository inventarioRepository) {
        this.busquedaRepository = busquedaRepository;
        this.inventarioRepository = inventarioRepository;
    }

    // q busca por codigo, nombre o marca de la referencia. color, talla y calidad son ids.
    // Sin filtros devuelve todo lo disponible; con incluirAgotados tambien las variantes en 0.
    @Transactional(readOnly = true)
    public ResultadoBusquedaInventario buscar(String q, Long colorId, Long tallaId, Long calidadId,
            boolean incluirAgotados, int pagina, int tamano) {
        if (pagina < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa.");
        }
        if (tamano < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser al menos 1.");
        }
        int tamanoReal = Math.min(tamano, TAMANO_MAXIMO);

        Page<Variante> variantes = busquedaRepository.buscar(NEGOCIO_ID, patron(q), colorId, tallaId, calidadId,
                incluirAgotados, PageRequest.of(pagina, tamanoReal));
        if (variantes.isEmpty()) {
            return new ResultadoBusquedaInventario(List.of(), variantes.getTotalElements());
        }

        // Una sola consulta trae el stock de todas las variantes de la pagina
        List<Long> ids = variantes.getContent().stream().map(Variante::getId).toList();
        Map<Long, List<UbicacionCantidadResponse>> porVariante = new LinkedHashMap<>();
        for (Inventario fila : inventarioRepository.conStockDe(NEGOCIO_ID, ids)) {
            porVariante.computeIfAbsent(fila.getVariante().getId(), k -> new ArrayList<>())
                    .add(new UbicacionCantidadResponse(
                            fila.getUbicacion().getId(), fila.getUbicacion().getNombre(), fila.getCantidad()));
        }

        List<VarianteDisponibleResponse> items = variantes.getContent().stream()
                .map(v -> aRespuesta(v, porVariante.getOrDefault(v.getId(), List.of())))
                .toList();
        return new ResultadoBusquedaInventario(items, variantes.getTotalElements());
    }

    private static VarianteDisponibleResponse aRespuesta(Variante v, List<UbicacionCantidadResponse> ubicaciones) {
        int total = ubicaciones.stream().mapToInt(UbicacionCantidadResponse::cantidad).sum();
        return new VarianteDisponibleResponse(
                v.getId(),
                v.getReferencia().getCodigo(),
                v.getReferencia().getNombre(),
                v.getColor().getNombre(),
                v.getTalla().comoTexto(),
                v.getCalidad().getNombre(),
                v.getPrecioSugerido(),   // puede ser null: la pantalla muestra "Sin precio"
                total,
                ubicaciones);
    }

    // "  Air Force " -> "%air force%". Se quitan % _ \ para que el usuario no use comodines.
    // Sin texto devuelve "%%", que coincide con todo.
    static String patron(String q) {
        String limpio = q == null ? "" : q.replaceAll("[%_\\\\]", " ").trim().replaceAll("\\s+", " ");
        return "%" + limpio.toLowerCase() + "%";
    }
}
