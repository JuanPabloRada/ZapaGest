// =====================================================================
// MOCK DEL SPRINT 2 (datos de prueba del inventario).
// BORRAR este archivo y sus <script> cuando existan las rutas /inventario/*.
// Guarda los datos en sessionStorage para que un ingreso se vea en el buscador.
// =====================================================================
const mockInventario = (() => {
    const CLAVE = "mock_inventario";

    function guardar(datos) {
        sessionStorage.setItem(CLAVE, JSON.stringify(datos));
    }

    function leer() {
        try {
            const datos = JSON.parse(sessionStorage.getItem(CLAVE));
            if (datos) return datos;
        } catch { /* se crea el inicial */ }
        const inicial = {
            ingresos: [],
            existencias: [
                { referencia: "AF1-001", modelo: "Nike Air Force", color: "Blanco", talla: "38", calidad: "Original", precioSugerido: 380000, ubicacion: "Bodega 1", cantidad: 2 },
                { referencia: "AF1-001", modelo: "Nike Air Force", color: "Blanco", talla: "38", calidad: "Original", precioSugerido: 380000, ubicacion: "Local Principal", cantidad: 1 },
                { referencia: "AF1-001", modelo: "Nike Air Force", color: "Negro", talla: "40", calidad: "1.1", precioSugerido: 190000, ubicacion: "Local Principal", cantidad: 3 },
                { referencia: "SAM-010", modelo: "Adidas Samba", color: "Negro", talla: "39", calidad: "Original", precioSugerido: 320000, ubicacion: "Bodega 1", cantidad: 0 },
            ],
        };
        guardar(inicial);
        return inicial;
    }

    const esperar = ms => new Promise(r => setTimeout(r, ms));
    const mismaVariante = (a, b) =>
        a.referencia === b.referencia && a.color === b.color && a.talla === b.talla && a.calidad === b.calidad;

    // datos = lo que enviaría la API; info = nombres para armar la fila de prueba
    async function registrarIngreso(datos, info) {
        await esperar(150);
        const bd = leer();
        const fila = {
            referencia: info.referencia, modelo: info.modelo, color: datos.color, talla: datos.talla,
            calidad: info.calidad, precioSugerido: info.precioSugerido, ubicacion: info.ubicacion, cantidad: 0,
        };
        let existente = bd.existencias.find(e => mismaVariante(e, fila) && e.ubicacion === fila.ubicacion);
        if (!existente) { existente = fila; bd.existencias.push(existente); }
        existente.cantidad += datos.cantidad;

        bd.ingresos.unshift({
            fecha: new Date().toISOString(), referencia: fila.referencia, color: fila.color, talla: fila.talla,
            calidad: fila.calidad, ubicacion: fila.ubicacion, cantidad: datos.cantidad, usuario: info.usuario,
        });
        guardar(bd);
        return { stockEnUbicacion: existente.cantidad };
    }

    async function ingresos() {
        await esperar(100);
        return leer().ingresos.slice(0, 15);
    }

    // filtros: { q, color, talla, calidad } (nombres, no ids)
    async function buscar(filtros) {
        await esperar(150);
        const q = (filtros.q || "").toLowerCase();
        const grupos = [];
        for (const e of leer().existencias) {
            if (q && !`${e.referencia} ${e.modelo}`.toLowerCase().includes(q)) continue;
            if (filtros.color && e.color !== filtros.color) continue;
            if (filtros.talla && e.talla !== filtros.talla) continue;
            if (filtros.calidad && e.calidad !== filtros.calidad) continue;

            let g = grupos.find(x => mismaVariante(x, e));
            if (!g) {
                g = { referencia: e.referencia, modelo: e.modelo, color: e.color, talla: e.talla,
                      calidad: e.calidad, precioSugerido: e.precioSugerido, total: 0, ubicaciones: [] };
                grupos.push(g);
            }
            g.total += e.cantidad;
            g.ubicaciones.push({ ubicacion: e.ubicacion, cantidad: e.cantidad });
        }
        return grupos.sort((a, b) => (b.total > 0) - (a.total > 0));
    }

    return { registrarIngreso, ingresos, buscar };
})();