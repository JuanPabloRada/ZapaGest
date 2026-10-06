package com.zapgest.backend.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.zapgest.backend.models.Talla;
import com.zapgest.backend.repositories.TallaRepository;

// La tabla "talla" es un catalogo fijo de tallas europeas (34 a 46, de media en media)
// que la API no permite modificar. Si la tabla esta vacia (base de datos nueva) o le
// faltan tallas, este componente las crea al arrancar. Si ya existen, no toca nada.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SembradoTallas implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SembradoTallas.class);

    // En medias tallas: 68 / 2 = 34 y 92 / 2 = 46
    static final int PRIMERA_EN_MEDIAS = 68;
    static final int ULTIMA_EN_MEDIAS = 92;

    private final TallaRepository repository;

    public SembradoTallas(TallaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        int creadas = sembrar();
        if (creadas > 0) {
            log.info("Se crearon {} tallas europeas (34 a 46).", creadas);
        }
    }

    // Devuelve cuantas tallas creo. Es idempotente: puede ejecutarse en cada arranque.
    int sembrar() {
        List<Talla> existentes = repository.findAll();

        Set<String> valoresExistentes = new HashSet<>();
        long siguienteId = 1;
        for (Talla t : existentes) {
            valoresExistentes.add(normalizar(t.getValor()));
            if (t.getId() != null && t.getId() >= siguienteId) {
                siguienteId = t.getId() + 1;
            }
        }

        int creadas = 0;
        for (int medias = PRIMERA_EN_MEDIAS; medias <= ULTIMA_EN_MEDIAS; medias++) {
            BigDecimal valor = new BigDecimal(medias).divide(new BigDecimal(2), 1, RoundingMode.UNNECESSARY);
            if (valoresExistentes.contains(normalizar(valor))) {
                continue;
            }
            Talla talla = new Talla();
            talla.setId(siguienteId++);
            talla.setValor(valor);
            repository.save(talla);
            creadas++;
        }
        return creadas;
    }

    // 38.0 -> "38", 38.5 -> "38.5"
    private static String normalizar(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
