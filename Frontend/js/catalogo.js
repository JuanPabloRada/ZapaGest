// ===== PERMISOS =====
const tienePermiso = requerirRol("DUENO", "ADMIN");
if (!tienePermiso) document.querySelector(".dashboard").hidden = true;

// ===== MODO: CREAR O EDITAR =====
// catalogo.html           -> crear un modelo nuevo
// catalogo.html?id=5      -> editar el modelo 5 (mismo formulario, con sus datos)
const idEditar = new URLSearchParams(window.location.search).get("id");
const modeloEditandoId = idEditar !== null && idEditar !== "" ? Number(idEditar) : null;
const editando = modeloEditandoId !== null && !Number.isNaN(modeloEditandoId);

// ===== ELEMENTOS DEL HTML =====
const form = document.getElementById("modeloForm");
const tituloForm = document.getElementById("tituloForm");
const enlaceVolver = document.getElementById("volverModelos");
const avisoStock = document.getElementById("avisoStock");
const inputReferencia = document.getElementById("referencia");
const inputNombre = document.getElementById("nombre");
const inputMarca = document.getElementById("marca");
const resumenVariantes = document.getElementById("resumenVariantes");
const selectCalidad = document.getElementById("selectCalidad");
const tablaPrecios = document.getElementById("tablaPrecios");
const tbodyPrecios = document.querySelector("#tablaPrecios tbody");
const sinCalidades = document.getElementById("sinCalidades");
const botonGuardar = document.getElementById("guardarBtn");
const mensaje = document.getElementById("mensaje");

// ===== ACCESO A DATOS (API) =====
// apiFetch (api.js) ya agrega el token.
function obtenerCalidades() {
    return apiFetch("/calidades");
}

function obtenerModelos() {
    return apiFetch("/catalogo");
}

function guardarModelo(datos) {
    return apiFetch("/catalogo", { method: "POST", body: JSON.stringify(datos) });
}

// OJO: este endpoint debe existir en el backend (pídeselo a Joseph).
function actualizarModelo(id, datos) {
    return apiFetch(`/catalogo/${id}`, { method: "PUT", body: JSON.stringify(datos) });
}

function obtenerTallas() {
    return apiFetch("/tallas"); // [{ id: 1, nombre: "34" }, { id: 2, nombre: "34.5" }, ...]
}

function obtenerColores() {
    return apiFetch("/colores"); // [{ id: 1, nombre: "Blanco" }, ...]
}

// ===== UTILIDADES =====

function mostrarMensaje(texto, tipo) {
    mensaje.textContent = texto;
    mensaje.className = "mensaje " + (tipo || "");
}

// Evita que se inserte HTML escrito por el usuario
function escapar(texto) {
    const div = document.createElement("div");
    div.textContent = texto ?? "";
    return div.innerHTML.replace(/"/g, "&quot;");
}

// ===== DESPLEGABLES DE COLORES Y TALLAS =====
// Colores: la última opción, "Añadir otro...", permite crear uno nuevo
//          (el backend lo crea al guardar el modelo).
// Tallas: solo se pueden elegir las que vienen del backend.

const OPCION_NUEVO = "__nuevo__";

function crearSelector(config) {
    const el = id => (id ? document.getElementById(id) : null);
    const ids = config.ids;
    return {
        ...config,
        lista: [], // se llena desde la API
        elementos: {
            select: el(ids.select),
            tags: el(ids.tags),
            wrap: el(ids.wrap),
            input: el(ids.input),
            btnAgregar: el(ids.btnAgregar),
            btnCancelar: el(ids.btnCancelar),
            error: el(ids.error),
        },
        elegidos: [],
    };
}

const selColor = crearSelector({
    singular: "color",
    placeholder: "Elige un color",
    permiteNuevo: true,
    ids: {
        select: "selectColor", tags: "tagsColores", wrap: "nuevoColorWrap",
        input: "nuevoColor", btnAgregar: "btnAgregarColor", btnCancelar: "btnCancelarColor",
        error: "errorNuevoColor",
    },
});

const selTalla = crearSelector({
    singular: "talla",
    placeholder: "Elige una talla",
    permiteNuevo: false, // las tallas las crea el backend al arrancar
    ordenar: (a, b) => Number(a) - Number(b),
    ids: {
        select: "selectTalla", tags: "tagsTallas",
    },
});

async function cargarTallasYColores() {
    const [tallasApi, coloresApi] = await Promise.all([
        obtenerTallas(),
        obtenerColores(),
    ]);
    selTalla.lista = tallasApi.map(t => String(t.nombre));
    selColor.lista = coloresApi.map(c => c.nombre);
    dibujarSelector(selTalla);
    dibujarSelector(selColor);
}

function dibujarSelector(s) {
    const { select, tags } = s.elementos;
    if (s.ordenar) s.lista.sort(s.ordenar); // las tallas quedan en orden numérico

    s.elegidos.sort((x, y) => s.lista.indexOf(x) - s.lista.indexOf(y));
    const disponibles = s.lista.filter(v => !s.elegidos.includes(v));

    select.innerHTML =
        `<option value="">${s.placeholder}</option>` +
        disponibles.map(v => `<option value="${escapar(v)}">${escapar(v)}</option>`).join("") +
        (s.permiteNuevo ? `<option value="${OPCION_NUEVO}">➕ Añadir otro...</option>` : "");
    select.value = "";

    tags.innerHTML = s.elegidos
        .map((v, i) => `<span class="tag">${escapar(v)}
            <button type="button" data-i="${i}" aria-label="Quitar ${escapar(v)}">×</button></span>`)
        .join("");

    actualizarResumen();
}

function mostrarErrorCampo(s, texto) {
    const { error, input } = s.elementos;
    if (!error || !input) return;
    error.textContent = texto;
    error.hidden = texto === "";
    input.classList.toggle("invalido", texto !== "");
}

function ocultarNuevo(s) {
    if (!s.permiteNuevo) return;
    s.elementos.wrap.hidden = true;
    s.elementos.input.value = "";
    mostrarErrorCampo(s, "");
}

function agregarNuevo(s) {
    if (!s.permiteNuevo) return;
    const { input } = s.elementos;
    const valor = input.value.trim();

    if (valor === "") {
        mostrarErrorCampo(s, `Escribe el nombre del ${s.singular} que quieres añadir.`);
        input.focus();
        return;
    }

    const existente = s.lista.find(v => v.toLowerCase() === valor.toLowerCase());
    if (existente) {
        if (!s.elegidos.includes(existente)) s.elegidos.push(existente);
    } else {
        s.lista.push(valor);
        s.elegidos.push(valor);
    }

    mostrarMensaje("", "");
    ocultarNuevo(s);
    dibujarSelector(s);
}

function conectarSelector(s) {
    const { select, tags, input, btnAgregar, btnCancelar, wrap } = s.elementos;

    select.addEventListener("change", () => {
        if (select.value === OPCION_NUEVO) {
            wrap.hidden = false;
            input.focus();
            select.value = "";
        } else if (select.value !== "") {
            s.elegidos.push(select.value);
            dibujarSelector(s);
        }
    });

    tags.addEventListener("click", e => {
        const boton = e.target.closest("button[data-i]");
        if (!boton) return;
        s.elegidos.splice(Number(boton.dataset.i), 1);
        dibujarSelector(s);
    });

    if (!s.permiteNuevo) return;

    btnAgregar.addEventListener("click", () => agregarNuevo(s));
    btnCancelar.addEventListener("click", () => ocultarNuevo(s));

    // Enter en el campo nuevo agrega la opción en vez de enviar el formulario
    input.addEventListener("keydown", e => {
        if (e.key === "Enter") {
            e.preventDefault();
            agregarNuevo(s);
        }
    });

    input.addEventListener("input", () => mostrarErrorCampo(s, ""));
}

function limpiarSelecciones() {
    for (const s of [selColor, selTalla]) {
        s.elegidos = [];
        ocultarNuevo(s);
        dibujarSelector(s);
    }
}

// variantes = colores × tallas × calidades.
function actualizarResumen() {
    const c = selColor.elegidos.length;
    const t = selTalla.elegidos.length;
    const q = filasPrecios.length;

    if (c === 0 || t === 0 || q === 0) {
        resumenVariantes.textContent =
            "Elige colores, tallas y al menos una calidad para ver cuántas combinaciones tendrá la referencia.";
        return;
    }
    resumenVariantes.textContent =
        `${c} color${c > 1 ? "es" : ""} × ${t} talla${t > 1 ? "s" : ""} × ` +
        `${q} calidad${q > 1 ? "es" : ""} = ${c * t * q} combinaciones posibles`;
}

// ===== CALIDADES Y PRECIOS =====

let calidadesCargadas = [];
let filasPrecios = []; // { calidadId, costo, precioMinimo, precioVenta } (texto de los campos)

function nombreCalidad(id) {
    const c = calidadesCargadas.find(x => x.id === id);
    return c ? c.nombre : "?";
}

function dibujarSelectCalidad() {
    const disponibles = calidadesCargadas.filter(
        c => !filasPrecios.some(f => f.calidadId === c.id)
    );

    const textoInicial = calidadesCargadas.length === 0
        ? "No hay calidades creadas"
        : disponibles.length === 0
            ? "Ya añadiste todas las calidades"
            : "Elige una calidad";

    selectCalidad.innerHTML =
        `<option value="">${textoInicial}</option>` +
        disponibles.map(c => `<option value="${escapar(String(c.id))}">${escapar(c.nombre)}</option>`).join("");
    selectCalidad.value = "";
    selectCalidad.disabled = disponibles.length === 0;
}

function dibujarFilasPrecios() {
    tablaPrecios.hidden = filasPrecios.length === 0;

    tbodyPrecios.innerHTML = filasPrecios
        .map((f, i) => {
            const nombre = escapar(nombreCalidad(f.calidadId));
            return `
            <tr data-i="${i}">
                <td>${nombre}</td>
                <td><input type="number" min="0" step="1" data-campo="costo"
                    value="${escapar(f.costo)}" placeholder="0" aria-label="Costo ${nombre}"></td>
                <td><input type="number" min="0" step="1" data-campo="precioMinimo"
                    value="${escapar(f.precioMinimo)}" placeholder="0" aria-label="Precio mínimo ${nombre}"></td>
                <td><input type="number" min="0" step="1" data-campo="precioVenta"
                    value="${escapar(f.precioVenta)}" placeholder="0" aria-label="Precio sugerido ${nombre}"></td>
                <td><button type="button" class="quitar-fila" aria-label="Quitar ${nombre}">×</button></td>
            </tr>`;
        })
        .join("");

    actualizarResumen();
}

async function cargarCalidades() {
    calidadesCargadas = await obtenerCalidades();
    sinCalidades.hidden = calidadesCargadas.length > 0;
    dibujarSelectCalidad();
}

// Devuelve { precios } o { error }
function leerPrecios() {
    if (filasPrecios.length === 0) {
        return { error: "Añade al menos una calidad con sus precios." };
    }

    const precios = [];

    for (const f of filasPrecios) {
        const nombre = nombreCalidad(f.calidadId);

        if (f.costo === "" || f.precioMinimo === "" || f.precioVenta === "") {
            return { error: `En "${nombre}" completa el costo, el precio mínimo y el precio sugerido.` };
        }

        const costo = Number(f.costo);
        const precioMinimo = Number(f.precioMinimo);
        const precioVenta = Number(f.precioVenta);

        if (costo < 0 || precioMinimo <= 0 || precioVenta <= 0) {
            return { error: `Revisa los valores de "${nombre}".` };
        }
        if (precioMinimo < costo) {
            return { error: `En "${nombre}" el precio mínimo no puede ser menor que el costo.` };
        }
        if (precioMinimo > precioVenta) {
            return { error: `En "${nombre}" el precio mínimo no puede ser mayor que el precio sugerido.` };
        }

        precios.push({ calidadId: f.calidadId, costo, precioMinimo, precioVenta });
    }

    return { precios };
}

selectCalidad.addEventListener("change", () => {
    if (selectCalidad.value === "") return;

    const calidad = calidadesCargadas.find(c => String(c.id) === selectCalidad.value);
    if (!calidad) return;

    filasPrecios.push({ calidadId: calidad.id, costo: "", precioMinimo: "", precioVenta: "" });
    dibujarSelectCalidad();
    dibujarFilasPrecios();
    mostrarMensaje("", "");

    const ultimo = tbodyPrecios.querySelector("tr:last-child input");
    if (ultimo) ultimo.focus();
});

tbodyPrecios.addEventListener("input", e => {
    const campo = e.target.dataset.campo;
    const fila = e.target.closest("tr");
    if (!campo || !fila) return;
    filasPrecios[Number(fila.dataset.i)][campo] = e.target.value.trim();
});

tbodyPrecios.addEventListener("click", e => {
    const boton = e.target.closest(".quitar-fila");
    if (!boton) return;
    filasPrecios.splice(Number(boton.closest("tr").dataset.i), 1);
    dibujarFilasPrecios();
    dibujarSelectCalidad();
});

// ===== MODO EDICIÓN: rellenar el formulario con los datos del modelo =====

async function cargarModeloParaEditar() {
    const lista = await obtenerModelos();
    const m = lista.find(x => Number(x.id) === modeloEditandoId);

    if (!m) {
        mostrarMensaje("No se encontró el modelo que quieres editar.", "error");
        botonGuardar.disabled = true;
        return;
    }

    inputReferencia.value = m.referencia ?? "";
    inputNombre.value = m.nombre ?? "";
    inputMarca.value = m.marca ?? "";

    // Si el modelo trae un color o talla que no está en la lista, se agrega para poder mostrarlo
    selColor.elegidos = [...m.colores];
    for (const v of selColor.elegidos) {
        if (!selColor.lista.includes(v)) selColor.lista.push(v);
    }
    selTalla.elegidos = m.tallas.map(String);
    for (const v of selTalla.elegidos) {
        if (!selTalla.lista.includes(v)) selTalla.lista.push(v);
    }

    filasPrecios = m.precios.map(p => ({
        calidadId: p.calidadId,
        costo: String(p.costo),
        precioMinimo: String(p.precioMinimo),
        precioVenta: String(p.precioSugerido),
    }));

    dibujarSelector(selColor);
    dibujarSelector(selTalla);
    dibujarSelectCalidad();
    dibujarFilasPrecios();
}

function prepararTextosModoEdicion() {
    if (!editando) return;
    tituloForm.textContent = "Editar modelo";
    botonGuardar.textContent = "Guardar cambios";
    enlaceVolver.hidden = false;
    avisoStock.hidden = true;
}

// Botón "Volver a modelos" (solo visible al editar)
enlaceVolver.addEventListener("click", () => {
    window.location.href = "modelos.html";
});

// ===== EVENTOS =====

conectarSelector(selColor);
conectarSelector(selTalla);

form.addEventListener("submit", async function (event) {
    event.preventDefault();

    const referencia = inputReferencia.value.trim();
    const nombre = inputNombre.value.trim();
    const marca = inputMarca.value.trim();
    const coloresElegidos = [...selColor.elegidos];
    const tallasElegidas = [...selTalla.elegidos];

    if (!referencia || !nombre || !marca) {
        mostrarMensaje("Completa referencia, nombre y marca.", "error");
        return;
    }
    if (coloresElegidos.length === 0) {
        mostrarMensaje("Elige al menos un color.", "error");
        return;
    }
    if (tallasElegidas.length === 0) {
        mostrarMensaje("Elige al menos una talla.", "error");
        return;
    }

    const lectura = leerPrecios();
    if (lectura.error) {
        mostrarMensaje(lectura.error, "error");
        return;
    }

    const datos = {
        referencia,
        nombre,
        marca,
        colores: coloresElegidos,
        tallas: tallasElegidas,
        precios: lectura.precios.map(precio => ({
            calidadId: precio.calidadId,
            costo: precio.costo,
            precioMinimo: precio.precioMinimo,
            precioSugerido: precio.precioVenta,
        })),
    };

    botonGuardar.disabled = true; // evita doble clic
    try {
        // Referencia repetida: la valida el backend y su mensaje se muestra en el catch.
        if (editando) {
            await actualizarModelo(modeloEditandoId, datos);
            window.location.href = "modelos.html";
            return;
        }

        await guardarModelo(datos);

        form.reset();
        limpiarSelecciones();
        filasPrecios = [];
        dibujarFilasPrecios();
        await cargarCalidades();
        await cargarTallasYColores(); // por si el backend creó un color nuevo
        mostrarMensaje("Modelo guardado con stock en 0.", "ok");
    } catch (e) {
        mostrarMensaje(e.message, "error");
    } finally {
        botonGuardar.disabled = false;
    }
});

document.addEventListener("DOMContentLoaded", async () => {
    if (!tienePermiso) return; // ya se está redirigiendo; no llamar a la API
    prepararTextosModoEdicion();
    dibujarSelector(selColor);
    dibujarSelector(selTalla);
    try {
        await cargarTallasYColores();
        await cargarCalidades();
        dibujarFilasPrecios();
        if (editando) await cargarModeloParaEditar();
    } catch (e) {
        mostrarMensaje(e.message, "error");
    }
});