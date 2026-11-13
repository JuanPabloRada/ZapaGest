package com.zapgest.backend.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zapgest.backend.dto.inventario.IngresoRequest;
import com.zapgest.backend.dto.inventario.IngresoResponse;
import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.UbicacionRepository;
import com.zapgest.backend.repositories.UsuarioRepository;

// HU-07: ingreso de mercancia. Une las piezas en UNA sola transaccion:
//   1) obtener o crear la variante (HU-06, hereda precios del catalogo)
//   2) sumar el stock en la ubicacion (InventarioService, seguro con ingresos simultaneos)
//   3) dejar el movimiento de ENTRADA con quien lo hizo (MovimientoService)
// Si cualquier paso falla, no queda nada a medias: se deshace todo.
@Service
public class IngresoMercanciaService {

    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1
    static final int MAX_CANTIDAD = 10_000;    // tope por ingreso para evitar errores de digitacion

    private final VarianteService varianteService;
    private final InventarioService inventarioService;
    private final MovimientoService movimientoService;
    private final UbicacionRepository ubicacionRepository;
    private final UsuarioRepository usuarioRepository;

    public IngresoMercanciaService(VarianteService varianteService, InventarioService inventarioService,
            MovimientoService movimientoService, UbicacionRepository ubicacionRepository,
            UsuarioRepository usuarioRepository) {
        this.varianteService = varianteService;
        this.inventarioService = inventarioService;
        this.movimientoService = movimientoService;
        this.ubicacionRepository = ubicacionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public IngresoResponse ingresar(IngresoRequest datos, String usernameActual) {
        if (datos == null) {
            throw new IllegalArgumentException("Faltan los datos del ingreso.");
        }
        if (datos.cantidad() == null || datos.cantidad() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
        if (datos.cantidad() > MAX_CANTIDAD) {
            throw new IllegalArgumentException("La cantidad no puede superar " + MAX_CANTIDAD + " pares por ingreso.");
        }
        if (datos.ubicacionId() == null) {
            throw new IllegalArgumentException("Debes elegir la ubicación.");
        }

        // La ubicacion se revisa ANTES de crear la variante: si esta inactiva no se toca nada
        Ubicacion ubicacion = ubicacionRepository.findByIdAndNegocioId(datos.ubicacionId(), NEGOCIO_ID)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la ubicación."));
        if (!Boolean.TRUE.equals(ubicacion.getActiva())) {
            throw new IllegalArgumentException("La ubicación está inactiva.");
        }

        Usuario usuario = usuarioRepository.findByUsername(usernameActual)
                .orElseThrow(() -> new IllegalStateException("No se encontró el usuario de la sesión."));

        Variante variante = varianteService.obtenerOCrear(
                datos.referenciaId(), datos.colorId(), datos.tallaId(), datos.calidadId());

        int stock = inventarioService.sumarStock(variante.getId(), ubicacion.getId(), datos.cantidad());

        movimientoService.registrarEntrada(variante, ubicacion, datos.cantidad(), usuario);

        return new IngresoResponse(variante.getId(), stock);
    }
}
