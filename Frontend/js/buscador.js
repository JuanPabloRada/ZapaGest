// El buscador lo usan todos los roles (HU-09 y HU-10): no pide requerirRol, solo sesión (menu-rol.js).

// ===== ELEMENTOS DEL HTML =====
const inputBusqueda = document.getElementById("q");
const filtroColor = document.getElementById("filtroColor");
const filtroTalla = document.getElementById("filtroTalla");
const filtroCalidad = document.getElementById("filtroCalidad");
const estado = document.getElementById("estado");
const mensaje = document.getElementById("mensaje");
const tbody = document.querySelector("#tablaResultados tbody");

const ESPERA_MS = 300;
const MAX_FILAS = 100; // tope en pantalla; la búsqueda sirve para afinar
let temporizador = null;
let numeroBusqueda = 0; // para ignorar respuestas viejas

// ===== ACCESO A DATOS =====
// GET /api/inventario/buscar (los tres roles). Sin parámetros devuelve solo lo que tiene stock, hasta 100 por página.
// Respuesta (sin costos): [{ referencia, modelo, color, talla, calidad,
//   precioSugerido, total, ubicaciones: [{ ubicacion, cantidad }] }]
function buscarVariantes(filtros) {
    const parametros = new URLSearchParams();
    for (const [clave, valor] of Object.entries(filtros)) {
        if (valor) parametros.set(clave, valor);
    }
    return apiFetch(`/inventario/buscar?${parametros}`);
}

// ===== UTILIDADES =====

function textoError(error) {
    return error instanceof TypeError ? "No se pudo conectar con el servidor." : error.message;
}

function escapar(texto) {
    const div = document.createElement("div");
    div.textContent = texto ?? "";
    return div.innerHTML.replace(/"/g, "&quot;");
}

function dinero(numero) {
    return "$" + Number(numero).toLocaleString("es-CO");
}

function llenarFiltro(select, nombres, textoInicial) {
    select.innerHTML = `<option value="">${textoInicial}</option>` +
        nombres.map(n => `<option value="${escapar(n)}">${escapar(n)}</option>`).join("");
}

// ===== RESULTADOS =====

function textoDisponibilidad(v) {
    if (v.total <= 0) return "<strong>Agotado</strong>";
    const conStock = v.ubicaciones.filter(u => u.cantidad > 0);
    // Desglose por ubicación: siempre con la cantidad de cada una
    const donde = conStock.map(u => `${escapar(u.ubicacion)}: ${u.cantidad}`).join(" · ");
    return `<strong>Sí hay ${v.total} ${v.total === 1 ? "par" : "pares"}</strong><br><small>${donde}</small>`;
}

function dibujarResultados(lista) {
    tbody.innerHTML = lista.slice(0, MAX_FILAS).map(v => `<tr class="${v.total <= 0 ? "agotado" : ""}">
        <td>${escapar(v.referencia)}</td>
        <td>${escapar(v.modelo)}</td>
        <td>${escapar(v.color)}</td>
        <td>${escapar(v.talla)}</td>
        <td>${escapar(v.calidad)}</td>
        <td class="disponibilidad">${textoDisponibilidad(v)}</td>
        <td>${v.precioSugerido == null ? "<small>Sin precio</small>" : dinero(v.precioSugerido)}</td>
    </tr>`).join("");
}

async function buscar() {
    const filtros = {
        q: inputBusqueda.value.trim(),
        color: filtroColor.value,
        talla: filtroTalla.value,
        calidad: filtroCalidad.value,
    };

    const hayCriterio = Boolean(filtros.q || filtros.color || filtros.talla || filtros.calidad);

    const mia = ++numeroBusqueda;
    const inicio = performance.now();
    estado.textContent = "Buscando...";
    mensaje.textContent = "";

    try {
        const lista = await buscarVariantes(filtros);
        if (mia !== numeroBusqueda) return; // llegó una respuesta más nueva
        dibujarResultados(lista);
        const ms = Math.round(performance.now() - inicio);
        if (lista.length === 0) {
            estado.textContent = hayCriterio
                ? `Sin resultados (${ms} ms). Revisa la referencia o quita algún filtro.`
                : "Aún no hay mercancía registrada. Se carga en Inventario.";
        } else {
            estado.textContent = `${lista.length} resultado${lista.length === 1 ? "" : "s"} en ${ms} ms` +
                (lista.length >= MAX_FILAS ? ` · puede haber más; usa la búsqueda o los filtros para afinar` : "");
        }
    } catch (error) {
        if (mia !== numeroBusqueda) return;
        estado.textContent = "";
        mensaje.textContent = textoError(error);
        mensaje.className = "mensaje error";
    }
}

function buscarConEspera() {
    clearTimeout(temporizador);
    temporizador = setTimeout(buscar, ESPERA_MS);
}

// ===== EVENTOS =====

inputBusqueda.addEventListener("input", buscarConEspera);
for (const filtro of [filtroColor, filtroTalla, filtroCalidad]) {
    filtro.addEventListener("change", buscar);
}

document.addEventListener("DOMContentLoaded", async () => {
    llenarFiltro(filtroColor, [], "Todos los colores");
    llenarFiltro(filtroTalla, [], "Todas las tallas");
    llenarFiltro(filtroCalidad, [], "Todas las calidades");

    // Permite llegar con la búsqueda lista: buscador.html?q=AF1-001
    const busquedaInicial = new URLSearchParams(location.search).get("q");
    if (busquedaInicial) inputBusqueda.value = busquedaInicial;

    buscar(); // muestra todo lo disponible aunque no se haya escrito nada
    inputBusqueda.focus();

    // Las listas de los filtros no traen costos (a diferencia de /catalogo)
    const [colores, tallas, calidades] = await Promise.allSettled([
        apiFetch("/colores"), apiFetch("/tallas"), apiFetch("/calidades"),
    ]);
    if (colores.status === "fulfilled") llenarFiltro(filtroColor, colores.value.map(c => c.nombre), "Todos los colores");
    if (tallas.status === "fulfilled") llenarFiltro(filtroTalla, tallas.value.map(t => String(t.nombre)), "Todas las tallas");
    if (calidades.status === "fulfilled") llenarFiltro(filtroCalidad, calidades.value.map(c => c.nombre), "Todas las calidades");
});