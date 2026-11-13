package com.zapgest.backend.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zapgest.backend.models.Movimiento;
import com.zapgest.backend.models.TipoMovimiento;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.MovimientoRepository;

// HU-07: deja constancia de cada ingreso de mercancia (que, donde, cuanto, quien y cuando).
@Service
public class MovimientoService {

    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1

    private final MovimientoRepository movimientoRepository;

    public MovimientoService(MovimientoRepository movimientoRepository) {
        this.movimientoRepository = movimientoRepository;
    }

    // Registra un movimiento de tipo ENTRADA. Corre dentro de la transaccion del ingreso:
    // si algo falla despues, el movimiento y el stock se deshacen juntos.
    @Transactional
    public Movimiento registrarEntrada(Variante variante, Ubicacion ubicacion, int cantidad, Usuario usuario) {
        if (variante == null || ubicacion == null || usuario == null) {
            throw new IllegalArgumentException("Faltan datos para registrar la entrada.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }

        Movimiento movimiento = new Movimiento();
        movimiento.setNegocioId(NEGOCIO_ID);
        movimiento.setVariante(variante);
        movimiento.setUbicacion(ubicacion);
        movimiento.setTipo(TipoMovimiento.ENTRADA);
        movimiento.setCantidad(cantidad);
        movimiento.setUsuario(usuario);
        return movimientoRepository.save(movimiento);
    }
}
