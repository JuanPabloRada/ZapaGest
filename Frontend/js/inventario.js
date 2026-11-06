// ===== PERMISOS =====
// Solo dueño y administrador ingresan mercancía (HU-07).
const tienePermiso = requerirRol("DUENO", "ADMIN");
if (!tienePermiso) document.querySelector(".dashboard").hidden = true;

// MOCK: pasa a true cuando existan POST y GET /api/inventario/ingresos.
// (Bodegas y catálogo ya usan la API real.)
const USAR_API_INVENTARIO = false;

// ===== ELEMENTOS DEL HTML =====
const form = document.getElementById("ingresoForm");
const selBodega = document.getElementById("bodega");
const selReferencia = document.getElementById("referencia");
const selColor = document.getElementById("color");
const selTalla = document.getElementById("talla");
const selCalidad = document.getElementById("calidad");
const inputCantidad = document.getElementById("cantidad");
const precioHint = document.getElementById("precioHint");
const botonGuardar = document.getElementById("guardarBtn");
const mensaje = document.getElementById("mensaje");
const contador = document.getElementById("contador");
const tbody = document.querySelector("#tablaIngresos tbody");
const textoVacio = document.getElementById("vacio");

let bodegas = [];
let modelos = [];

// ===== ACCESO A DATOS =====

function obtenerBodegas() {
    return apiFetch("/ubicaciones");
}

function obtenerModelos() {
    return apiFetch("/catalogo");
}

// datos = { ubicacionId, referenciaId, color, talla, calidadId, cantidad }
// La respuesta esperada incluye { stockEnUbicacion }. "info" solo la usa el mock.
function registrarIngreso(datos, info) {
    if (!USAR_API_INVENTARIO) return mockInventario.registrarIngreso(datos, info);
    return apiFetch("/inventario/ingresos", { method: "POST", body: JSON.stringify(datos) });
}

// Cada ingreso: { fecha, referencia, color, talla, calidad, ubicacion, cantidad, usuario }
function obtenerIngresos() {
    if (!USAR_API_INVENTARIO) return mockInventario.ingresos();
    return apiFetch("/inventario/ingresos");
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

function dinero(numero) {
    return "$" + Number(numero).toLocaleString("es-CO");
}

// opciones: [{ valor, texto }]
function llenarSelect(select, opciones, textoInicial) {
    select.innerHTML =
        `<option value="">${textoInicial}</option>` +
        opciones.map(o => `<option value="${escapar(String(o.valor))}">${escapar(o.texto)}</option>`).join("");
    select.disabled = opciones.length === 0;
}

// ===== FORMULARIO =====

function modeloElegido() {
    return modelos.find(m => String(m.id) === selReferencia.value);
}

function precioElegido() {
    const m = modeloElegido();
    return m ? m.precios.find(p => String(p.calidadId) === selCalidad.value) : null;
}

function limpiarDependientes() {
    llenarSelect(selColor, [], "Elige primero la referencia");
    llenarSelect(selTalla, [], "Elige primero la referencia");
    llenarSelect(selCalidad, [], "Elige primero la referencia");
    actualizarPrecio();
}

// Muestra el precio que heredará la variante (viene de la referencia)
function actualizarPrecio() {
    const precio = precioElegido();
    precioHint.hidden = !precio;
    if (precio) {
        precioHint.textContent = precio.precioSugerido != null
            ? `Precio sugerido que heredará la variante: ${dinero(precio.precioSugerido)}`
            : "Esta calidad no tiene precios configurados: la variante se creará sin precios.";
    }
}

selReferencia.addEventListener("change", () => {
    const m = modeloElegido();
    if (!m) {
        limpiarDependientes();
        return;
    }
    llenarSelect(selColor, m.colores.map(c => ({ valor: c, texto: c })), "Elige un color");
    llenarSelect(selTalla, m.tallas.map(t => ({ valor: t, texto: t })), "Elige una talla");
    llenarSelect(selCalidad,
        m.precios.map(p => ({ valor: p.calidadId, texto: p.calidad ?? `Calidad ${p.calidadId}` })),
        "Elige una calidad");
    actualizarPrecio();
});

selCalidad.addEventListener("change", actualizarPrecio);

form.addEventListener("submit", async event => {
    event.preventDefault();

    const bodega = bodegas.find(b => String(b.id) === selBodega.value);
    const modelo = modeloElegido();
    const precio = precioElegido();
    const cantidad = Number(inputCantidad.value);

    if (!bodega) return mostrarMensaje("Elige la bodega donde entra la mercancía.", "error");
    if (!modelo) return mostrarMensaje("Elige la referencia.", "error");
    if (!selColor.value) return mostrarMensaje("Elige el color.", "error");
    if (!selTalla.value) return mostrarMensaje("Elige la talla.", "error");
    if (!precio) return mostrarMensaje("Elige la calidad.", "error");
    if (!Number.isInteger(cantidad) || cantidad <= 0) {
        inputCantidad.focus();
        return mostrarMensaje("La cantidad debe ser un número entero mayor que 0.", "error");
    }

    const datos = {
        ubicacionId: bodega.id,
        referenciaId: modelo.id,
        color: selColor.value,
        talla: selTalla.value,
        calidadId: precio.calidadId,
        cantidad,
    };
    const info = {
        referencia: modelo.referencia,
        modelo: modelo.nombre,
        calidad: precio.calidad ?? `Calidad ${precio.calidadId}`,
        ubicacion: bodega.nombre,
        precioSugerido: precio.precioSugerido,
        usuario: obtenerUsuarioSesion()?.nombre,
    };

    botonGuardar.disabled = true; // evita doble clic
    try {
        const respuesta = await registrarIngreso(datos, info);
        const total = respuesta?.stockEnUbicacion;
        mostrarMensaje(
            `Se registraron ${cantidad} pares de ${info.referencia} · ${datos.color} · ${datos.talla} · ${info.calidad} en ${info.ubicacion}.` +
            (total != null ? ` Ahora hay ${total} en esa bodega.` : ""),
            "ok");

        // Se conservan la bodega y la referencia: quien recibe cajas repite el ingreso
        selColor.value = selTalla.value = selCalidad.value = inputCantidad.value = "";
        actualizarPrecio();
        selColor.focus();
        await cargarIngresos();
    } catch (error) {
        mostrarMensaje(textoError(error), "error");
    } finally {
        botonGuardar.disabled = false;
    }
});

// ===== ÚLTIMOS INGRESOS =====

function dibujarIngresos(lista) {
    tbody.innerHTML = lista.map(i => `<tr>
        <td>${escapar(new Date(i.fecha).toLocaleString("es-CO", { dateStyle: "short", timeStyle: "short" }))}</td>
        <td>${escapar(i.referencia)}</td>
        <td>${escapar(i.color)}</td>
        <td>${escapar(i.talla)}</td>
        <td>${escapar(i.calidad)}</td>
        <td>${escapar(i.ubicacion)}</td>
        <td>${escapar(String(i.cantidad))}</td>
        <td>${escapar(i.usuario)}</td>
    </tr>`).join("");
    textoVacio.hidden = lista.length > 0;
    contador.textContent = lista.length;
}

async function cargarIngresos() {
    dibujarIngresos(await obtenerIngresos());
}

// ===== CARGA INICIAL =====

document.addEventListener("DOMContentLoaded", async () => {
    limpiarDependientes();
    llenarSelect(selBodega, [], "Cargando...");
    llenarSelect(selReferencia, [], "Cargando...");

    // Cada carga es independiente: si una falla, las demás siguen funcionando
    const [rBodegas, rModelos, rIngresos] =
        await Promise.allSettled([obtenerBodegas(), obtenerModelos(), obtenerIngresos()]);

    if (rBodegas.status === "fulfilled") {
        bodegas = rBodegas.value.filter(b => b.activa !== false); // solo bodegas activas
        llenarSelect(selBodega, bodegas.map(b => ({ valor: b.id, texto: b.nombre })),
            bodegas.length ? "Elige la bodega" : "No hay bodegas activas");
    }
    if (rModelos.status === "fulfilled") {
        modelos = rModelos.value.filter(m => m.activa !== false);
        llenarSelect(selReferencia, modelos.map(m => ({ valor: m.id, texto: `${m.referencia} · ${m.nombre}` })),
            modelos.length ? "Elige la referencia" : "No hay referencias creadas");
    }
    if (rIngresos.status === "fulfilled") dibujarIngresos(rIngresos.value);

    const fallo = [rBodegas, rModelos, rIngresos].find(r => r.status === "rejected");
    if (fallo) mostrarMensaje(textoError(fallo.reason), "error");
});