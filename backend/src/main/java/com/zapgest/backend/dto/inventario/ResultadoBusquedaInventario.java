package com.zapgest.backend.dto.inventario;

import java.util.List;

// Lo que devuelve el servicio de busqueda: la pagina pedida y cuantos resultados hay en total
// (el controlador responde la lista y pone el total en el header X-Total-Count).
public record ResultadoBusquedaInventario(List<VarianteDisponibleResponse> items, long totalResultados) {
}
