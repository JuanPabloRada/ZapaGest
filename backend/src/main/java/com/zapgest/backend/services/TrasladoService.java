package com.zapgest.backend.services;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zapgest.backend.dto.traslado.TrasladoRequest;
import com.zapgest.backend.dto.traslado.TrasladoResponse;
import com.zapgest.backend.exceptions.EstadoNoPermitidoException;
import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.EstadoTraslado;
import com.zapgest.backend.models.Traslado;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.InventarioRepository;
import com.zapgest.backend.repositories.TrasladoRepository;
import com.zapgest.backend.repositories.UbicacionRepository;
import com.zapgest.backend.repositories.UsuarioRepository;
import com.zapgest.backend.repositories.VarianteRepository;

// Traslados entre ubicaciones (Sprint 3). Flujo:
//   solicitar -> SOLICITADO     (no mueve stock)
//   despachar -> EN_MOVIMIENTO  (sale del origen, movimiento TRASLADO -N en el origen)
//   recibir   -> LLEGO          (entra al destino, movimiento TRASLADO +N en el destino)
//   cancelar  -> CANCELADO      (solo desde SOLICITADO)
// Cada accion corre en UNA transaccion: si algo falla, no queda nada a medias.
@Service
public class TrasladoService {

    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1
    static final int MAX_CANTIDAD = 10_000;    // mismo tope que el ingreso de mercancia

    private final TrasladoRepository trasladoRepository;
    private final UbicacionRepository ubicacionRepository;
    private final VarianteRepository varianteRepository;
    private final InventarioRepository inventarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final InventarioService inventarioService;
    private final MovimientoService movimientoService;

    public TrasladoService(TrasladoRepository trasladoRepository, UbicacionRepository ubicacionRepository,
            VarianteRepository varianteRepository, InventarioRepository inventarioRepository,
            UsuarioRepository usuarioRepository, InventarioService inventarioService,
            MovimientoService movimientoService) {
        this.trasladoRepository = trasladoRepository;
        this.ubicacionRepository = ubicacionRepository;
        this.varianteRepository = varianteRepository;
        this.inventarioRepository = inventarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.inventarioService = inventarioService;
        this.movimientoService = movimientoService;
    }

    @Transactional(readOnly = true)
    public List<TrasladoResponse> listar() {
        return trasladoRepository.listar(NEGOCIO_ID).stream().map(TrasladoService::aRespuesta).toList();
    }

    @Transactional
    public TrasladoResponse solicitar(TrasladoRequest datos, String usernameActual) {
        if (datos == null) {
            throw new IllegalArgumentException("Faltan los datos del traslado.");
        }
        if (datos.origenId() == null) {
            throw new IllegalArgumentException("Elige el origen.");
        }
        if (datos.destinoId() == null) {
            throw new IllegalArgumentException("Elige el destino.");
        }
        if (Objects.equals(datos.origenId(), datos.destinoId())) {
            throw new IllegalArgumentException("El origen y el destino deben ser distintos.");
        }
        if (datos.varianteId() == null) {
            throw new IllegalArgumentException("Elige la variante que vas a trasladar.");
        }
        if (datos.cantidad() == null || datos.cantidad() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
        if (datos.cantidad() > MAX_CANTIDAD) {
            throw new IllegalArgumentException("La cantidad no puede superar " + MAX_CANTIDAD + " pares por traslado.");
        }

        Ubicacion origen = ubicacionActiva(datos.origenId(), "origen");
        Ubicacion destino = ubicacionActiva(datos.destinoId(), "destino");

        Variante variante = varianteRepository.findById(datos.varianteId())
                .filter(v -> NEGOCIO_ID.equals(v.getNegocioId()))
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la variante."));

        // Al solicitar solo se revisa que alcance; el stock se descuenta de verdad al despachar
        int hay = inventarioRepository.cantidadEn(variante.getId(), origen.getId()).orElse(0);
        if (datos.cantidad() > hay) {
            throw new IllegalArgumentException("No hay suficientes pares en el origen: hay " + hay + ".");
        }

        Traslado traslado = new Traslado();
        traslado.setNegocioId(NEGOCIO_ID);
        traslado.setOrigen(origen);
        traslado.setDestino(destino);
        traslado.setVariante(variante);
        traslado.setCantidad(datos.cantidad());
        traslado.setEstado(EstadoTraslado.SOLICITADO);
        traslado.setSolicitadoPor(usuario(usernameActual));
        traslado.setSolicitadoEn(Instant.now());
        return aRespuesta(trasladoRepository.save(traslado));
    }

    @Transactional
    public TrasladoResponse despachar(Long id, String usernameActual) {
        Traslado traslado = paraCambiar(id);
        if (traslado.getEstado() != EstadoTraslado.SOLICITADO) {
            throw new EstadoNoPermitidoException("Solo se puede despachar un traslado Solicitado.");
        }
        if (!Boolean.TRUE.equals(traslado.getOrigen().getActiva())) {
            throw new IllegalArgumentException("La ubicación de origen está inactiva.");
        }
        Usuario usuario = usuario(usernameActual);

        // Sale del origen (si ya no alcanza, falla y no cambia nada) y queda en transito
        inventarioService.restarStock(traslado.getVariante().getId(), traslado.getOrigen().getId(),
                traslado.getCantidad());
        movimientoService.registrarTraslado(traslado.getVariante(), traslado.getOrigen(),
                -traslado.getCantidad(), usuario);

        traslado.setEstado(EstadoTraslado.EN_MOVIMIENTO);
        traslado.setDespachadoPor(usuario);
        traslado.setDespachadoEn(Instant.now());
        return aRespuesta(trasladoRepository.save(traslado));
    }

    @Transactional
    public TrasladoResponse recibir(Long id, String usernameActual) {
        Traslado traslado = paraCambiar(id);
        if (traslado.getEstado() != EstadoTraslado.EN_MOVIMIENTO) {
            throw new EstadoNoPermitidoException("Solo se puede recibir un traslado En movimiento.");
        }
        Usuario usuario = usuario(usernameActual);

        // Entra al destino (sumarStock revisa que el destino siga activo)
        inventarioService.sumarStock(traslado.getVariante().getId(), traslado.getDestino().getId(),
                traslado.getCantidad());
        movimientoService.registrarTraslado(traslado.getVariante(), traslado.getDestino(),
                traslado.getCantidad(), usuario);

        traslado.setEstado(EstadoTraslado.LLEGO);
        traslado.setRecibidoPor(usuario);
        traslado.setRecibidoEn(Instant.now());
        return aRespuesta(trasladoRepository.save(traslado));
    }

    @Transactional
    public TrasladoResponse cancelar(Long id, String usernameActual) {
        Traslado traslado = paraCambiar(id);
        if (traslado.getEstado() != EstadoTraslado.SOLICITADO) {
            throw new EstadoNoPermitidoException("Solo se puede cancelar un traslado Solicitado.");
        }
        traslado.setEstado(EstadoTraslado.CANCELADO);
        traslado.setCanceladoPor(usuario(usernameActual));
        traslado.setCanceladoEn(Instant.now());
        return aRespuesta(trasladoRepository.save(traslado));
    }

    // ---------- ayudas ----------

    private Traslado paraCambiar(Long id) {
        return trasladoRepository.buscarParaCambiar(id, NEGOCIO_ID)
                .orElseThrow(() -> new RecursoNoEncontradoException("El traslado no existe."));
    }

    private Ubicacion ubicacionActiva(Long id, String rol) {
        Ubicacion u = ubicacionRepository.findByIdAndNegocioId(id, NEGOCIO_ID)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la ubicación de " + rol + "."));
        if (!Boolean.TRUE.equals(u.getActiva())) {
            throw new IllegalArgumentException("La ubicación de " + rol + " está inactiva.");
        }
        return u;
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("No se encontró el usuario de la sesión."));
    }

    static TrasladoResponse aRespuesta(Traslado t) {
        Variante v = t.getVariante();
        return new TrasladoResponse(
                t.getId(),
                t.getOrigen().getId(), t.getOrigen().getNombre(),
                t.getDestino().getId(), t.getDestino().getNombre(),
                v.getId(),
                v.getReferencia().getCodigo(),
                v.getReferencia().getNombre(),
                v.getColor().getNombre(),
                v.getTalla().comoTexto(),
                v.getCalidad().getNombre(),
                t.getCantidad(),
                t.getEstado().name(),
                t.getSolicitadoPor().getNombre(),
                t.getSolicitadoEn(),
                t.getDespachadoEn(),
                t.getRecibidoEn(),
                t.getCanceladoEn());
    }
}
