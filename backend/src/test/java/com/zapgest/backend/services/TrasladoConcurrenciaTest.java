package com.zapgest.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.models.Color;
import com.zapgest.backend.models.Referencia;
import com.zapgest.backend.models.Talla;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.CalidadRepository;
import com.zapgest.backend.repositories.ColorRepository;
import com.zapgest.backend.repositories.InventarioRepository;
import com.zapgest.backend.repositories.ReferenciaRepository;
import com.zapgest.backend.repositories.TallaRepository;
import com.zapgest.backend.repositories.UbicacionRepository;
import com.zapgest.backend.repositories.VarianteRepository;

// Traslados: despachos SIMULTANEOS que sacan de la misma variante y ubicacion nunca dejan el stock
// en negativo: con 10 pares y 20 despachos de 1 a la vez, salen exactamente 10 y el stock queda en 0.
// Necesita PostgreSQL de verdad (UPDATE ... WHERE cantidad >= N), por eso solo corre si JWT_SECRET esta definida.
// Crea datos "zz-concurrencia" y los borra al terminar; usalo en una base de datos de desarrollo.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class TrasladoConcurrenciaTest {

    private static final int HILOS = 20;

    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private InventarioRepository inventarioRepository;
    @Autowired
    private VarianteRepository varianteRepository;
    @Autowired
    private ReferenciaRepository referenciaRepository;
    @Autowired
    private ColorRepository colorRepository;
    @Autowired
    private TallaRepository tallaRepository;
    @Autowired
    private CalidadRepository calidadRepository;
    @Autowired
    private UbicacionRepository ubicacionRepository;
    @Autowired
    private TransactionTemplate transacciones;

    private Variante variante;
    private Ubicacion ubicacion;
    private Referencia referencia;
    private Color color;
    private Calidad calidad;

    @BeforeEach
    void preparar() {
        String sufijo = String.valueOf(System.nanoTime());

        color = new Color();
        color.setNegocioId(1L);
        color.setNombre("zz-tras-" + sufijo.substring(sufijo.length() - 6));
        color = colorRepository.save(color);

        calidad = new Calidad();
        calidad.setNegocioId(1L);
        calidad.setNombre("zz-tras-" + sufijo.substring(sufijo.length() - 6));
        calidad = calidadRepository.save(calidad);

        referencia = new Referencia();
        referencia.setNegocioId(1L);
        referencia.setCodigo("zz-tras-" + sufijo.substring(sufijo.length() - 6));
        referencia.setNombre("Prueba concurrencia");
        referencia = referenciaRepository.save(referencia);

        ubicacion = new Ubicacion();
        ubicacion.setNegocioId(1L);
        ubicacion.setNombre("zz-tras-" + sufijo.substring(sufijo.length() - 6));
        ubicacion.setActiva(true);
        ubicacion = ubicacionRepository.save(ubicacion);

        Talla talla = tallaRepository.findAll().get(0); // las tallas las siembra SembradoTallas al arrancar

        variante = new Variante();
        variante.setNegocioId(1L);
        variante.setReferencia(referencia);
        variante.setColor(color);
        variante.setTalla(talla);
        variante.setCalidad(calidad);
        variante.setPrecioSugerido(new BigDecimal("1000"));
        variante = varianteRepository.save(variante);
    }

    @AfterEach
    void limpiar() {
        transacciones.executeWithoutResult(estado -> inventarioRepository.findAll().stream()
                .filter(i -> i.getVariante().getId().equals(variante.getId()))
                .forEach(inventarioRepository::delete));
        varianteRepository.deleteById(variante.getId());
        referenciaRepository.deleteById(referencia.getId());
        colorRepository.deleteById(color.getId());
        calidadRepository.deleteById(calidad.getId());
        ubicacion.setActiva(false); // la API no borra ubicaciones; queda inactiva
        ubicacionRepository.save(ubicacion);
    }

    @Test
    void despachosSimultaneosNuncaDejanElStockEnNegativo() throws Exception {
        inventarioService.sumarStock(variante.getId(), ubicacion.getId(), 10);

        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        // 20 hilos intentan sacar 1 par a la vez; solo hay 10
        for (int i = 0; i < HILOS; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                try {
                    inventarioService.restarStock(variante.getId(), ubicacion.getId(), 1);
                    return true;
                } catch (IllegalArgumentException noAlcanzo) {
                    return false;
                }
            }));
        }
        salida.countDown();
        int exitosos = 0;
        for (Future<Boolean> r : resultados) {
            if (r.get(30, TimeUnit.SECONDS)) {
                exitosos++;
            }
        }
        pool.shutdown();

        assertThat(exitosos).isEqualTo(10);
        assertThat(inventarioRepository.cantidadEn(variante.getId(), ubicacion.getId())).contains(0);
    }
}
