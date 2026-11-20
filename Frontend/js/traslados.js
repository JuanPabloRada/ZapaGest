// ===== PERMISOS =====
// Quién puede hacer cada acción es una decisión del Product Owner: ajústalo aquí.
const ACCIONES = {
    solicitar: ["DUENO", "ADMIN"],
    despachar: ["DUENO", "ADMIN"],
    recibir: ["DUENO", "ADMIN"],
    cancelar: ["DUENO", "ADMIN"],
};
const tienePermiso = requerirRol(...new Set(Object.values(ACCIONES).flat()));
if (!tienePermiso) document.querySelector(".dashboard").hidden = true;

const rolActual = obtenerUsuarioSesion()?.rol;
const puede = accion => ACCIONES[accion].includes(rolActual);

// MOCK: pasa a true cuando existan las rutas /api/traslados y /api/inventario/existencias
const USAR_API_TRASLADOS = false;

// Los tres estados de un traslado (y el de cancelado)
const ESTADOS = {
    SOLICITADO: { texto: "Solicitado", icono: "fa-clipboard-list", paso: 0 },
    EN_MOVIMIENTO: { texto: "En movimiento", icono: "fa-truck-fast", paso: 1 },
    LLEGO: { texto: "Llegó al local", icono: "fa-circle-check", paso: 2 },
    CANCELADO: { texto: "Cancelado", icono: "fa-ban", paso: -1 },
};

// ===== ELEMENTOS DEL HTML =====
const form = document.getElementById("trasladoForm");
const selOrigen = document.getElementById("origen");
const selDestino = document.getElementById("destino");
const selVariante = document.getElementById("variante");
const inputCantidad = document.getElementById("cantidad");
const disponibleHint = document.getElementById("disponible");
const botonGuardar = document.getElementById("guardarBtn");
const mensaje = document.getElementById("mensaje");
const contador = document.getElementById("contador");
const tbody = document.querySelector("#tablaTraslados tbody");
const textoVacio = document.getElementById("vacio");
const chips = document.querySelectorAll(".chips-estado button");

let ubicaciones = [];
let existencias = [];   // variantes con stock en el origen elegido
let traslados = [];
let filtroEstado = "";
let pedidoExistencias = 0; // ignora respuestas viejas al cambiar de origen

// ===== ACCESO A DATOS =====
// Contrato propuesto (ver mensaje a Juan Pablo y Joseph):
//   GET   /api/inventario/existencias?ubicacionId=  → [{ varianteId, referencia, modelo, color, talla, calidad, cantidad }]
//   POST  /api/traslados { origenId, destinoId, varianteId, cantidad } → 201 traslado
//   GET   /api/traslados → [{ id, origenId, origen, destinoId, destino, varianteId, referencia, modelo, color, talla,
//                             calidad, cantidad, estado, solicitadoPor, solicitadoEn, despachadoEn, recibidoEn }]
//   PATCH /api/traslados/{id}/despachar | /recibir | /cancelar → traslado actualizado
// Errores: { mensaje } con 400 (regla de negocio), 403 (sin permiso), 404 y 409 (estado no permitido).

function obtenerUbicaciones() {
    return apiFetch("/ubicaciones");
}

function obtenerExistencias(ubicacionId) {
    if (!USAR_API_TRASLADOS) return mockTraslados.existencias(Number(ubicacionId));
    return apiFetch(`/inventario/existencias?ubicacionId=${ubicacionId}`);
}

function listarTraslados() {
    if (!USAR_API_TRASLADOS) return mockTraslados.listar();
    return apiFetch("/traslados");
}

function crearTraslado(datos, info) {
    if (!USAR_API_TRASLADOS) return mockTraslados.crear(datos, info);
    return apiFetch("/traslados", { method: "POST", body: JSON.stringify(datos) });
}

// accion: "despachar" | "recibir" | "cancelar"
function cambiarEstadoTraslado(id, accion) {
    if (!USAR_API_TRASLADOS) return mockTraslados.cambiarEstado(id, accion);
    return apiFetch(`/traslados/${id}/${accion}`, { method: "PATCH" });
}

// ===== UTILIDADES =====

function mostrarMensaje(texto, tipo) {
    mensaje.textContent = texto;
    mensaje.className = "mensaje " + (tipo || "");
}

function textoError(error) {
    return error instanceof TypeError ? "No se pudo conectar con el servidor." : error.message;
}

function escapar(texto) {
    const div = document.createElement("div");
    div.textContent = texto ?? "";
    return div.innerHTML.replace(/"/g, "&quot;");
}

function llenarSelect(select, opciones, textoInicial) {
    select.innerHTML =
        `<option value="">${textoInicial}</option>` +
        opciones.map(o => `<option value="${escapar(String(o.valor))}">${escapar(o.texto)}</option>`).join("");
    select.disabled = opciones.length === 0;
}

const descripcionVariante = v => `${v.referencia} · ${v.color} · ${v.talla} · ${v.calidad}`;
const nombreUbicacion = id => ubicaciones.find(u => String(u.id) === String(id))?.nombre ?? "";
const fechaCorta = iso => iso ? new Date(iso).toLocaleString("es-CO", { dateStyle: "short", timeStyle: "short" }) : "";

// ===== FORMULARIO: NUEVO TRASLADO =====

function llenarDestinos() {
    const origen = selOrigen.value;
    const actual = selDestino.value;
    const opciones = ubicaciones.filter(u => String(u.id) !== origen).map(u => ({ valor: u.id, texto: u.nombre }));
    llenarSelect(selDestino, opciones, origen ? "Elige el destino" : "Elige primero el origen");
    if (opciones.some(o => String(o.valor) === actual)) selDestino.value = actual;
}

async function cargarExistencias() {
    const origen = selOrigen.value;
    const pedido = ++pedidoExistencias;
    existencias = [];
    disponibleHint.hidden = true;

    if (!origen) {
        llenarSelect(selVariante, [], "Elige primero el origen");
        return;
    }
    llenarSelect(selVariante, [], "Cargando...");
    try {
        const lista = await obtenerExistencias(origen);
        if (pedido !== pedidoExistencias) return; // el usuario ya cambió de origen
        existencias = lista;
        llenarSelect(selVariante,
            lista.map(v => ({ valor: v.varianteId, texto: `${descripcionVariante(v)} (${v.cantidad} disponibles)` })),
            lista.length ? "Elige la variante" : "Sin existencias en este origen");
    } catch (error) {
        if (pedido !== pedidoExistencias) return;
        llenarSelect(selVariante, [], "No se pudo cargar");
        mostrarMensaje(textoError(error), "error");
    }
}

function varianteElegida() {
    return existencias.find(v => String(v.varianteId) === selVariante.value);
}

selOrigen.addEventListener("change", () => {
    llenarDestinos();
    cargarExistencias();
    mostrarMensaje("", "");
});

selVariante.addEventListener("change", () => {
    const v = varianteElegida();
    disponibleHint.hidden = !v;
    if (v) {
        disponibleHint.textContent = `Disponible en ${nombreUbicacion(selOrigen.value)}: ${v.cantidad} pares`;
        inputCantidad.max = v.cantidad;
    }
});

form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!puede("solicitar")) return mostrarMensaje("Tu rol no puede solicitar traslados.", "error");

    const variante = varianteElegida();
    const cantidad = Number(inputCantidad.value);

    if (!selOrigen.value) return mostrarMensaje("Elige el origen.", "error");
    if (!selDestino.value) return mostrarMensaje("Elige el destino.", "error");
    if (selOrigen.value === selDestino.value) return mostrarMensaje("El origen y el destino deben ser distintos.", "error");
    if (!variante) return mostrarMensaje("Elige la variante que vas a trasladar.", "error");
    if (!Number.isInteger(cantidad) || cantidad < 1 || cantidad > variante.cantidad) {
        inputCantidad.focus();
        return mostrarMensaje(`La cantidad debe ser un entero entre 1 y ${variante.cantidad} (lo que hay en el origen).`, "error");
    }

    const datos = {
        origenId: Number(selOrigen.value),
        destinoId: Number(selDestino.value),
        varianteId: variante.varianteId,
        cantidad,
    };
    const info = {
        origen: nombreUbicacion(selOrigen.value),
        destino: nombreUbicacion(selDestino.value),
        usuario: obtenerUsuarioSesion()?.nombre,
    };

    botonGuardar.disabled = true; // evita doble clic
    try {
        await crearTraslado(datos, info);
        mostrarMensaje(`Traslado solicitado: ${cantidad} pares de ${descripcionVariante(variante)} de ${info.origen} a ${info.destino}.`, "ok");
        selVariante.value = inputCantidad.value = "";
        disponibleHint.hidden = true;
        await cargarTraslados();
    } catch (error) {
        mostrarMensaje(textoError(error), "error");
    } finally {
        botonGuardar.disabled = false;
    }
});

// ===== LISTA DE TRASLADOS =====

function pasosHtml(estado) {
    const actual = ESTADOS[estado]?.paso ?? -1;
    if (actual < 0) return "";
    const iconos = ["fa-clipboard-list", "fa-truck-fast", "fa-circle-check"];
    const llego = estado === "LLEGO";
    return `<div class="pasos" aria-hidden="true">` + iconos.map((icono, i) => {
        const clase = llego || i < actual ? "hecho" : i === actual ? "actual" : "";
        const linea = i > 0 ? `<span class="paso-linea ${llego || i <= actual ? "hecha" : ""}"></span>` : "";
        return `${linea}<span class="paso ${clase}"><i class="fa-solid ${icono}"></i></span>`;
    }).join("") + `</div>`;
}

function boton(accion, texto, icono, id, extra = "") {
    return `<button type="button" class="btn-traslado ${extra}" data-accion="${accion}" data-id="${escapar(String(id))}">
        <i class="fa-solid ${icono}"></i> ${texto}</button>`;
}

function accionesHtml(t) {
    const botones = [];
    if (t.estado === "SOLICITADO") {
        if (puede("despachar")) botones.push(boton("despachar", "Despachar", "fa-truck-fast", t.id));
        if (puede("cancelar")) botones.push(boton("cancelar", "Cancelar", "fa-xmark", t.id, "peligro"));
    } else if (t.estado === "EN_MOVIMIENTO" && puede("recibir")) {
        botones.push(boton("recibir", "Confirmar llegada", "fa-circle-check", t.id));
    }
    return botones.length ? `<div class="acciones-traslado">${botones.join("")}</div>` : "<small>—</small>";
}

function dibujarTraslados() {
    const visibles = traslados.filter(t => !filtroEstado || t.estado === filtroEstado);

    tbody.innerHTML = visibles.map(t => {
        const e = ESTADOS[t.estado] ?? { texto: t.estado, icono: "fa-circle-question" };
        return `<tr>
            <td>#${escapar(String(t.id))}<br><small>${escapar(fechaCorta(t.recibidoEn ?? t.despachadoEn ?? t.solicitadoEn))}</small></td>
            <td>${escapar(descripcionVariante(t))}<br><small>${escapar(t.modelo)}</small></td>
            <td class="ruta">${escapar(t.origen)}<i class="fa-solid fa-arrow-right"></i>${escapar(t.destino)}</td>
            <td>${escapar(String(t.cantidad))}</td>
            <td><span class="estado-traslado est-${escapar(String(t.estado).toLowerCase())}">
                <i class="fa-solid ${e.icono}"></i> ${escapar(e.texto)}</span>${pasosHtml(t.estado)}</td>
            <td>${accionesHtml(t)}</td>
        </tr>`;
    }).join("");

    textoVacio.hidden = visibles.length > 0;
    contador.textContent = visibles.length;
}

async function cargarTraslados() {
    traslados = await listarTraslados();
    dibujarTraslados();
}

chips.forEach(chip => chip.addEventListener("click", () => {
    filtroEstado = chip.dataset.estado;
    chips.forEach(c => c.classList.toggle("activo", c === chip));
    dibujarTraslados();
}));

const AVISOS = {
    despachar: t => `¿Despachar ${t.cantidad} pares de ${descripcionVariante(t)} desde ${t.origen}? Saldrán de su inventario y quedarán En movimiento.`,
    recibir: t => `¿Confirmar que llegaron ${t.cantidad} pares de ${descripcionVariante(t)} a ${t.destino}? Se sumarán a su inventario.`,
    cancelar: t => `¿Cancelar el traslado #${t.id}?`,
};
const EXITOS = {
    despachar: "Traslado despachado: ahora está En movimiento.",
    recibir: "Llegada confirmada: la mercancía ya está en el destino.",
    cancelar: "Traslado cancelado.",
};

tbody.addEventListener("click", async event => {
    const boton = event.target.closest(".btn-traslado");
    if (!boton) return;

    const accion = boton.dataset.accion;
    const t = traslados.find(x => String(x.id) === boton.dataset.id);
    if (!t || !puede(accion) || !confirm(AVISOS[accion](t))) return;

    boton.disabled = true;
    try {
        await cambiarEstadoTraslado(t.id, accion);
        mostrarMensaje(EXITOS[accion], "ok");
        await Promise.all([cargarTraslados(), cargarExistencias()]); // el stock del origen pudo cambiar
    } catch (error) {
        mostrarMensaje(textoError(error), "error"); // 400, 403, 404 y 409 llegan como { mensaje }
        boton.disabled = false;
    }
});

// ===== CARGA INICIAL =====

document.addEventListener("DOMContentLoaded", async () => {
    llenarSelect(selOrigen, [], "Cargando...");
    llenarSelect(selDestino, [], "Elige primero el origen");
    llenarSelect(selVariante, [], "Elige primero el origen");
    if (!puede("solicitar")) form.closest(".panel").hidden = true;

    // Cada carga es independiente: si una falla, las demás siguen funcionando
    const [rUbicaciones, rTraslados] = await Promise.allSettled([obtenerUbicaciones(), listarTraslados()]);

    if (rUbicaciones.status === "fulfilled") {
        ubicaciones = rUbicaciones.value.filter(u => u.activa !== false); // una ubicación inactiva no participa
        llenarSelect(selOrigen, ubicaciones.map(u => ({ valor: u.id, texto: u.nombre })),
            ubicaciones.length ? "Elige el origen" : "No hay ubicaciones activas");
    }
    if (rTraslados.status === "fulfilled") {
        traslados = rTraslados.value;
        dibujarTraslados();
    }
    const fallo = [rUbicaciones, rTraslados].find(r => r.status === "rejected");
    if (fallo) mostrarMensaje(textoError(fallo.reason), "error");
});