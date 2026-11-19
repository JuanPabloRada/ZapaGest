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

// HU-07: ingresos SIMULTANEOS de la misma variante en la misma ubicacion no pierden unidades
// ni duplican la fila de inventario. Necesita PostgreSQL de verdad (usa INSERT ... ON CONFLICT),
// por eso, igual que BackendApplicationTests, solo corre si JWT_SECRET esta definida.
// Crea datos "zz-concurrencia" y los borra al terminar; usalo en una base de datos de desarrollo.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class InventarioConcurrenciaTest {

    private static final int HILOS = 20;
    private static final int UNIDADES_POR_INGRESO = 5;

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
        color.setNombre("zz-conc-" + sufijo.substring(sufijo.length() - 6));
        color = colorRepository.save(color);

        calidad = new Calidad();
        calidad.setNegocioId(1L);
        calidad.setNombre("zz-conc-" + sufijo.substring(sufijo.length() - 6));
        calidad = calidadRepository.save(calidad);

        referencia = new Referencia();
        referencia.setNegocioId(1L);
        referencia.setCodigo("zz-conc-" + sufijo.substring(sufijo.length() - 6));
        referencia.setNombre("Prueba concurrencia");
        referencia = referenciaRepository.save(referencia);

        ubicacion = new Ubicacion();
        ubicacion.setNegocioId(1L);
        ubicacion.setNombre("zz-conc-" + sufijo.substring(sufijo.length() - 6));
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
    void ingresosSimultaneosSumanTodasLasUnidadesYDejanUnaSolaFila() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();

        // Todos los hilos esperan la misma senal y entran a la vez, con la fila de inventario aun inexistente
        for (int i = 0; i < HILOS; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return inventarioService.sumarStock(variante.getId(), ubicacion.getId(), UNIDADES_POR_INGRESO);
            }));
        }
        salida.countDown();
        for (Future<Integer> r : resultados) {
            r.get(30, TimeUnit.SECONDS); // si un hilo fallo, aqui explota
        }
        pool.shutdown();

        int esperado = HILOS * UNIDADES_POR_INGRESO;
        assertThat(inventarioRepository.cantidadEn(variante.getId(), ubicacion.getId())).contains(esperado);

        // findAll necesita una transaccion abierta para leer la variante de cada fila (carga perezosa)
        Long filas = transacciones.execute(estado -> inventarioRepository.findAll().stream()
                .filter(i -> i.getVariante().getId().equals(variante.getId()))
                .count());
        assertThat(filas).isEqualTo(1L);
    }
}
