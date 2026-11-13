// ===== PERMISOS =====
// Solo dueño y administrador ingresan mercancía (HU-07). El vendedor recibe 403.
const tienePermiso = requerirRol("DUENO", "ADMIN");
if (!tienePermiso) document.querySelector(".dashboard").hidden = true;

// ===== CONFIGURACIÓN =====
const MAX_PARES = 10000; // decisión del Product Owner: máximo por ingreso (evita un cero de más)

// El listado de últimos ingresos depende de que Juan Pablo confirme su GET (ruta y campos).
// Mientras tanto la tabla se oculta; cuando exista, pasa a true.
const MOSTRAR_ULTIMOS_INGRESOS = false;

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
let pedidoOpciones = 0; // para ignorar respuestas viejas al cambiar de referencia

// ===== ACCESO A DATOS (API) =====

function obtenerBodegas() {
    return apiFetch("/ubicaciones");
}

// Trae costos: solo dueño y administrador pueden llamarlo (el vendedor recibe 403)
function obtenerModelos() {
    return apiFetch("/catalogo");
}

// { referenciaId, codigo, nombre, marca, colores: [{id, nombre}], tallas: [{id, nombre}], calidades: [{id, nombre}] }
function obtenerOpciones(referenciaId) {
    return apiFetch(`/catalogo/${referenciaId}/opciones`);
}

// datos = { ubicacionId, referenciaId, colorId, tallaId, calidadId, cantidad } → 201 { varianteId, stockEnUbicacion }
function registrarIngreso(datos) {
    return apiFetch("/inventario/ingresos", { method: "POST", body: JSON.stringify(datos) });
}

// Ruta y campos por confirmar con Juan Pablo
function obtenerIngresos() {
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

function textoElegido(select) {
    return select.selectedOptions[0]?.text ?? "";
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

// El precio se lee del catálogo (las opciones no traen precios ni costos)
function precioElegido() {
    const modelo = modeloElegido();
    return modelo ? modelo.precios.find(p => String(p.calidadId) === selCalidad.value) : null;
}

function limpiarDependientes(texto = "Elige primero la referencia") {
    llenarSelect(selColor, [], texto);
    llenarSelect(selTalla, [], texto);
    llenarSelect(selCalidad, [], texto);
    actualizarPrecio();
}

// Muestra el precio que heredará la variante
function actualizarPrecio() {
    const precio = precioElegido();
    precioHint.hidden = !precio;
    if (precio) {
        precioHint.textContent = precio.precioSugerido != null
            ? `Precio sugerido que heredará la variante: ${dinero(precio.precioSugerido)}`
            : "Esta calidad no tiene precios configurados: la variante se creará sin precios.";
    }
}

selReferencia.addEventListener("change", async () => {
    const modelo = modeloElegido();
    if (!modelo) {
        limpiarDependientes();
        return;
    }

    const pedido = ++pedidoOpciones;
    limpiarDependientes("Cargando...");
    mostrarMensaje("", "");

    try {
        const opciones = await obtenerOpciones(modelo.id);
        if (pedido !== pedidoOpciones) return; // el usuario ya eligió otra referencia

        llenarSelect(selColor, opciones.colores.map(c => ({ valor: c.id, texto: c.nombre })),
            opciones.colores.length ? "Elige un color" : "Sin colores");
        llenarSelect(selTalla, opciones.tallas.map(t => ({ valor: t.id, texto: t.nombre })),
            opciones.tallas.length ? "Elige una talla" : "Sin tallas");
        llenarSelect(selCalidad, opciones.calidades.map(c => ({ valor: c.id, texto: c.nombre })),
            opciones.calidades.length ? "Elige una calidad" : "Sin calidades");
    } catch (error) {
        if (pedido !== pedidoOpciones) return;
        limpiarDependientes();
        mostrarMensaje(textoError(error), "error");
    }
});

selCalidad.addEventListener("change", actualizarPrecio);

form.addEventListener("submit", async event => {
    event.preventDefault();

    const bodega = bodegas.find(b => String(b.id) === selBodega.value);
    const modelo = modeloElegido();
    const cantidad = Number(inputCantidad.value);

    if (!bodega) return mostrarMensaje("Elige la bodega donde entra la mercancía.", "error");
    if (!modelo) return mostrarMensaje("Elige la referencia.", "error");
    if (!selColor.value) return mostrarMensaje("Elige el color.", "error");
    if (!selTalla.value) return mostrarMensaje("Elige la talla.", "error");
    if (!selCalidad.value) return mostrarMensaje("Elige la calidad.", "error");
    if (!Number.isInteger(cantidad) || cantidad < 1 || cantidad > MAX_PARES) {
        inputCantidad.focus();
        return mostrarMensaje(`La cantidad debe ser un número entero entre 1 y ${MAX_PARES.toLocaleString("es-CO")} pares.`, "error");
    }

    const datos = {
        ubicacionId: bodega.id,
        referenciaId: modelo.id,
        colorId: Number(selColor.value),
        tallaId: Number(selTalla.value),
        calidadId: Number(selCalidad.value),
        cantidad,
    };
    const descripcion = `${modelo.referencia} · ${textoElegido(selColor)} · ${textoElegido(selTalla)} · ${textoElegido(selCalidad)}`;

    botonGuardar.disabled = true; // evita doble clic
    try {
        const respuesta = await registrarIngreso(datos);
        mostrarMensaje(
            `Se registraron ${cantidad} pares de ${descripcion} en ${bodega.nombre}. ` +
            `Ahora hay ${respuesta.stockEnUbicacion} en esa bodega.`, "ok");

        // Se conservan la bodega y la referencia: quien recibe cajas repite el ingreso
        selColor.value = selTalla.value = selCalidad.value = inputCantidad.value = "";
        actualizarPrecio();
        selColor.focus();
        if (MOSTRAR_ULTIMOS_INGRESOS) await cargarIngresos();
    } catch (error) {
        mostrarMensaje(textoError(error), "error"); // 400, 403 y 404 llegan como { mensaje }
    } finally {
        botonGuardar.disabled = false;
    }
});

// ===== ÚLTIMOS INGRESOS (solo si MOSTRAR_ULTIMOS_INGRESOS) =====

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

    if (!MOSTRAR_ULTIMOS_INGRESOS) {
        document.getElementById("tablaIngresos").closest(".panel").hidden = true;
    }

    // Cada carga es independiente: si una falla, las demás siguen funcionando
    const [rBodegas, rModelos, rIngresos] = await Promise.allSettled([
        obtenerBodegas(),
        obtenerModelos(),
        MOSTRAR_ULTIMOS_INGRESOS ? obtenerIngresos() : Promise.resolve([]),
    ]);

    if (rBodegas.status === "fulfilled") {
        bodegas = rBodegas.value.filter(b => b.activa !== false); // una bodega inactiva no recibe mercancía
        llenarSelect(selBodega, bodegas.map(b => ({ valor: b.id, texto: b.nombre })),
            bodegas.length ? "Elige la bodega" : "No hay bodegas activas");
    }
    if (rModelos.status === "fulfilled") {
        modelos = rModelos.value.filter(m => m.activa !== false); // una referencia inactiva no genera variantes
        llenarSelect(selReferencia, modelos.map(m => ({ valor: m.id, texto: `${m.referencia} · ${m.nombre}` })),
            modelos.length ? "Elige la referencia" : "No hay referencias creadas");
    }
    if (MOSTRAR_ULTIMOS_INGRESOS && rIngresos.status === "fulfilled") dibujarIngresos(rIngresos.value);

    const fallo = [rBodegas, rModelos, rIngresos].find(r => r.status === "rejected");
    if (fallo) mostrarMensaje(textoError(fallo.reason), "error");
});