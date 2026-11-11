package com.zapgest.backend.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.inventario.ResultadoBusquedaInventario;
import com.zapgest.backend.dto.inventario.VarianteDisponibleResponse;
import com.zapgest.backend.services.ConsultaInventarioService;

// HU-09 y HU-10: consulta de disponibilidad y ubicacion. La puede usar cualquier usuario con login
// (DUENO, ADMIN y VENDEDOR; ver SeguridadConfig). El ingreso de mercancia (POST) es de Joseph.
@RestController
@RequestMapping("/api/inventario")
public class ConsultaInventarioController {

    public static final String HEADER_TOTAL = "X-Total-Count";

    private final ConsultaInventarioService service;

    public ConsultaInventarioController(ConsultaInventarioService service) {
        this.service = service;
    }

    // GET /api/inventario/buscar?q=&color=&talla=&calidad=&incluirAgotados=&pagina=&tamano=
    // Todos los parametros son opcionales. La lista va en el cuerpo; el total de resultados
    // (sin paginar) va en el header X-Total-Count.
    @GetMapping("/buscar")
    public ResponseEntity<List<VarianteDisponibleResponse>> buscar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long color,
            @RequestParam(required = false) Long talla,
            @RequestParam(required = false) Long calidad,
            @RequestParam(defaultValue = "false") boolean incluirAgotados,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        ResultadoBusquedaInventario resultado = service.buscar(q, color, talla, calidad, incluirAgotados, pagina, tamano);
        return ResponseEntity.ok()
                .header(HEADER_TOTAL, String.valueOf(resultado.totalResultados()))
                .body(resultado.items());
    }
}
