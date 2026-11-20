// =====================================================================
// MOCK DE TRASLADOS (datos de prueba mientras el backend no tenga /api/traslados).
// BORRAR este archivo y su <script> cuando existan las rutas reales.
// Guarda todo en sessionStorage, así que sobrevive a recargar la página.
// =====================================================================
const mockTraslados = (() => {
    const CLAVE = "mock_traslados";
    const esperar = ms => new Promise(r => setTimeout(r, ms));
    const falla = mensaje => { throw new Error(mensaje); };

    function leer() {
        try {
            const datos = JSON.parse(sessionStorage.getItem(CLAVE));
            if (datos) return datos;
        } catch { /* se crea el inicial */ }
        return { existencias: {}, traslados: [], siguiente: 1 };
    }
    const guardar = datos => sessionStorage.setItem(CLAVE, JSON.stringify(datos));

    // Cada ubicación recibe existencias de ejemplo la primera vez que se consulta
    function existenciasDe(bd, ubicacionId) {
        if (!bd.existencias[ubicacionId]) {
            bd.existencias[ubicacionId] = [
                { varianteId: 101, referencia: "AF1-001", modelo: "Nike Air Force", color: "Blanco", talla: "38", calidad: "Original", cantidad: 24 },
                { varianteId: 102, referencia: "SAM-010", modelo: "Adidas Samba", color: "Negro", talla: "40", calidad: "AAA", cantidad: 10 },
            ];
        }
        return bd.existencias[ubicacionId];
    }

    function sumar(bd, ubicacionId, t, cantidad) {
        const lista = existenciasDe(bd, ubicacionId);
        const fila = lista.find(e => e.varianteId === t.varianteId);
        if (fila) fila.cantidad += cantidad;
        else lista.push({ varianteId: t.varianteId, referencia: t.referencia, modelo: t.modelo, color: t.color,
                          talla: t.talla, calidad: t.calidad, cantidad });
    }

    async function existencias(ubicacionId) {
        await esperar(100);
        const bd = leer();
        const lista = existenciasDe(bd, ubicacionId).filter(e => e.cantidad > 0);
        guardar(bd);
        return lista;
    }

    async function listar() {
        await esperar(100);
        return leer().traslados.slice().reverse();
    }

    // datos = { origenId, destinoId, varianteId, cantidad }; info = { origen, destino, usuario } (nombres)
    async function crear(datos, info) {
        await esperar(150);
        const bd = leer();
        if (datos.origenId === datos.destinoId) falla("El origen y el destino deben ser distintos.");
        const fila = existenciasDe(bd, datos.origenId).find(e => e.varianteId === datos.varianteId);
        if (!fila) falla("Esa variante no existe en el origen.");
        if (datos.cantidad < 1 || datos.cantidad > fila.cantidad) {
            falla(`No hay suficientes pares en el origen: hay ${fila.cantidad}.`);
        }
        const t = {
            id: bd.siguiente++, origenId: datos.origenId, origen: info.origen, destinoId: datos.destinoId, destino: info.destino,
            varianteId: fila.varianteId, referencia: fila.referencia, modelo: fila.modelo, color: fila.color,
            talla: fila.talla, calidad: fila.calidad, cantidad: datos.cantidad, estado: "SOLICITADO",
            solicitadoPor: info.usuario, solicitadoEn: new Date().toISOString(),
        };
        bd.traslados.push(t);
        guardar(bd);
        return t;
    }

    async function cambiarEstado(id, accion) {
        await esperar(150);
        const bd = leer();
        const t = bd.traslados.find(x => x.id === Number(id));
        if (!t) falla("El traslado no existe.");

        if (accion === "despachar") {
            if (t.estado !== "SOLICITADO") falla("Solo se puede despachar un traslado Solicitado.");
            const fila = existenciasDe(bd, t.origenId).find(e => e.varianteId === t.varianteId);
            if (!fila || fila.cantidad < t.cantidad) falla("El origen ya no tiene suficientes pares.");
            fila.cantidad -= t.cantidad; // sale del origen y queda en tránsito
            t.estado = "EN_MOVIMIENTO";
            t.despachadoEn = new Date().toISOString();
        } else if (accion === "recibir") {
            if (t.estado !== "EN_MOVIMIENTO") falla("Solo se puede recibir un traslado En movimiento.");
            sumar(bd, t.destinoId, t, t.cantidad); // entra al destino
            t.estado = "LLEGO";
            t.recibidoEn = new Date().toISOString();
        } else if (accion === "cancelar") {
            if (t.estado !== "SOLICITADO") falla("Solo se puede cancelar un traslado Solicitado.");
            t.estado = "CANCELADO";
        } else {
            falla("Acción desconocida.");
        }
        guardar(bd);
        return t;
    }

    return { existencias, listar, crear, cambiarEstado };
})();