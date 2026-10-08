// ===== CONFIGURACIÓN =====
// Las calidades ya existen en el backend; el catálogo todavía no.
const USAR_API_CALIDADES = true;
const USAR_API_MODELOS = true; // Se activa cuando Joseph implemente /api/catalogo.

// ===== ELEMENTOS DEL HTML =====
const form = document.getElementById("modeloForm");
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
const contador = document.getElementById("contador");
const tbodyModelos = document.querySelector("#tablaModelos tbody");
const textoVacio = document.getElementById("vacio");
const buscador = document.getElementById("searchInput");

// ===== DATOS FALSOS (se usan mientras no exista la API) =====
let calidades = [
    { id: 1, nombre: "Original" },
    { id: 2, nombre: "1.1" },
    { id: 3, nombre: "AAA" },
];
let colores = ["Negro", "Blanco", "Rojo", "Azul", "Gris"];
let tallas = Array.from({ length: 25 }, (_, i) => String(34 + i * 0.5));
let modelos = [
    {
        id: 1,
        referencia: "AF1-001",
        nombre: "Nike Air Force",
        marca: "Nike",
        colores: ["Blanco", "Negro"],
        tallas: ["38", "39", "40", "41"],
        precios: [
            { calidadId: 1, costo: 250000, precioMinimo: 330000, precioVenta: 380000 },
            { calidadId: 3, costo: 120000, precioMinimo: 160000, precioVenta: 190000 },
        ],
    },
];

// ===== ACCESO A DATOS (API) =====
function obtenerCalidades() {
    return apiFetch("/calidades");
}

function obtenerModelos() {
    return apiFetch("/catalogo");
}

function guardarModelo(datos) {
    return apiFetch("/catalogo", { method: "POST", body: JSON.stringify(datos) });
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

function dinero(numero) {
    return "$" + Number(numero).toLocaleString("es-CO");
}

// ===== DESPLEGABLES DE COLORES Y TALLAS =====
// Cada desplegable agrega el valor elegido como etiqueta (se pueden elegir varios).
// La última opción, "Añadir otro...", muestra un campo para crear uno nuevo.

const OPCION_NUEVO = "__nuevo__";

function crearSelector(config) {
    return {
        ...config,
        elementos: {
            select: document.getElementById(config.ids.select),
            tags: document.getElementById(config.ids.tags),
            wrap: document.getElementById(config.ids.wrap),
            input: document.getElementById(config.ids.input),
            btnAgregar: document.getElementById(config.ids.btnAgregar),
            btnCancelar: document.getElementById(config.ids.btnCancelar),
        },
        elegidos: [],
    };
}

// Tallas europeas que acepta el backend: de 34 a 46, de media en media (38 o 38.5)
function normalizarTalla(texto) {
    const valor = texto.trim().replace(",", ".");
    if (!/^\d{2}(\.5)?$/.test(valor)) return null;
    const numero = Number(valor);
    return numero >= 34 && numero <= 46 ? String(numero) : null;
}

const selColor = crearSelector({
    lista: colores,
    singular: "color",
    placeholder: "Elige un color",
    ids: {
        select: "selectColor", tags: "tagsColores", wrap: "nuevoColorWrap",
        input: "nuevoColor", btnAgregar: "btnAgregarColor", btnCancelar: "btnCancelarColor",
    },
});

const selTalla = crearSelector({
    lista: tallas,
    singular: "talla",
    placeholder: "Elige una talla",
    ordenar: (a, b) => Number(a) - Number(b),
    validar: normalizarTalla,
    mensajeInvalido: "Usa una talla europea entre 34 y 46, de media en media (por ejemplo 38 o 38.5).",
    ids: {
        select: "selectTalla", tags: "tagsTallas", wrap: "nuevaTallaWrap",
        input: "nuevaTalla", btnAgregar: "btnAgregarTalla", btnCancelar: "btnCancelarTalla",
    },
});

function dibujarSelector(s) {
    const { select, tags } = s.elementos;
    if (s.ordenar) s.lista.sort(s.ordenar); // las tallas quedan en orden numérico

    s.elegidos.sort((x, y) => s.lista.indexOf(x) - s.lista.indexOf(y));
    const disponibles = s.lista.filter(v => !s.elegidos.includes(v));

    select.innerHTML =
        `<option value="">${s.placeholder}</option>` +
        disponibles.map(v => `<option value="${escapar(v)}">${escapar(v)}</option>`).join("") +
        `<option value="${OPCION_NUEVO}">➕ Añadir otro...</option>`;
    select.value = "";

    tags.innerHTML = s.elegidos
        .map((v, i) => `<span class="tag">${escapar(v)}
            <button type="button" data-i="${i}" aria-label="Quitar ${escapar(v)}">×</button></span>`)
        .join("");

    actualizarResumen();
}

function ocultarNuevo(s) {
    s.elementos.wrap.hidden = true;
    s.elementos.input.value = "";
}

function agregarNuevo(s) {
    const { input } = s.elementos;
    let valor = input.value.trim();

    if (valor === "") {
        mostrarMensaje(`Escribe el nombre del ${s.singular} que quieres añadir.`, "error");
        input.focus();
        return;
    }

    if (s.validar) {
        const valido = s.validar(valor);
        if (valido === null) {
            mostrarMensaje(s.mensajeInvalido, "error");
            input.focus();
            return;
        }
        valor = valido; // por ejemplo "38,5" pasa a "38.5"
    }

    const existente = s.lista.find(v => v.toLowerCase() === valor.toLowerCase());
    if (existente) {
        // Si ya existía, simplemente se selecciona
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

    btnAgregar.addEventListener("click", () => agregarNuevo(s));
    btnCancelar.addEventListener("click", () => ocultarNuevo(s));

    // Enter en el campo nuevo agrega la opción en vez de enviar el formulario
    input.addEventListener("keydown", e => {
        if (e.key === "Enter") {
            e.preventDefault();
            agregarNuevo(s);
        }
    });
}

function limpiarSelecciones() {
    for (const s of [selColor, selTalla]) {
        s.elegidos = [];
        ocultarNuevo(s);
        dibujarSelector(s);
    }
}

// La calidad es un atributo de la variante (HU-03):
// variantes = colores × tallas × calidades.
// Misma referencia, color y talla con otra calidad = otra variante.
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
        `${q} calidad${q > 1 ? "es" : ""} = ${c * t * q} combinaciones posibles (aún no se crean ni tienen stock)`;
}

// ===== CALIDADES Y PRECIOS =====
// Se elige una calidad ya creada en el desplegable y se le asignan sus precios.
// Cada calidad se puede añadir una sola vez por modelo.

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

    // Lleva el cursor al campo "Costo" de la fila nueva
    const ultimo = tbodyPrecios.querySelector("tr:last-child input");
    if (ultimo) ultimo.focus();
});

// Guarda lo que se escribe en cada fila sin volver a dibujar la tabla
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

// ===== LISTA DE MODELOS =====

async function dibujarModelos() {
    const [listaModelos, listaCalidades] = await Promise.all([
        obtenerModelos(),
        obtenerCalidades(),
    ]);

    const filtro = buscador.value.trim().toLowerCase();
    const visibles = listaModelos.filter(m =>
        [m.referencia, m.nombre, m.marca].some(t => t.toLowerCase().includes(filtro))
    );

    tbodyModelos.innerHTML = visibles
        .map(m => {
            const precios = m.precios
                .map(p => {
                    const cal = listaCalidades.find(c => c.id === p.calidadId);
                    return `<span class="precio-linea">${escapar(p.calidad ?? (cal ? cal.nombre : "?"))}: ` +
                        `costo ${dinero(p.costo)} · mín. ${dinero(p.precioMinimo)} · sugerido ${dinero(p.precioSugerido)}</span>`;
                })
                .join("");

            return `<tr>
                <td>${escapar(m.referencia)}</td>
                <td>${escapar(m.nombre)}</td>
                <td>${escapar(m.marca)}</td>
                <td>${m.colores.length} × ${m.tallas.length} × ${m.precios.length} = ${m.colores.length * m.tallas.length * m.precios.length}</td>
                <td>${precios}</td>
            </tr>`;
        })
        .join("");

    textoVacio.hidden = listaModelos.length > 0;
    contador.textContent = listaModelos.length;
}

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

    if (!referencia || !nombre || !marca ) {
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

    botonGuardar.disabled = true; // evita doble clic
    try {
        const existe = (await obtenerModelos()).some(
            m => m.referencia.toLowerCase() === referencia.toLowerCase()
        );
        if (existe) {
            mostrarMensaje("Ya existe un modelo con esa referencia.", "error");
            return;
        }

        await guardarModelo({
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
        });

        form.reset();
        limpiarSelecciones();
        filasPrecios = [];
        dibujarFilasPrecios();
        await cargarCalidades();
        mostrarMensaje("Modelo guardado con stock en 0.", "ok");
        await dibujarModelos();
    } catch (e) {
        mostrarMensaje(e.message, "error");
    } finally {
        botonGuardar.disabled = false;
    }
});

buscador.addEventListener("input", () =>
    dibujarModelos().catch(e => mostrarMensaje(e.message, "error")));

document.addEventListener("DOMContentLoaded", async () => {
    dibujarSelector(selColor);
    dibujarSelector(selTalla);
    try {
        await cargarCalidades();
        dibujarFilasPrecios();
        await dibujarModelos();
    } catch (e) {
        mostrarMensaje(e.message, "error");
    }
});