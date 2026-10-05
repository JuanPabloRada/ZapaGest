// ===== CONFIGURACIÓN =====
const USAR_API = false; // true = usa el backend de Joseph; false = datos falsos
const API = "http://localhost:8080/api";

// ===== ELEMENTOS DEL HTML =====
const form = document.getElementById("calidadForm");
const inputNombre = document.getElementById("nombre");
const botonGuardar = document.getElementById("guardarBtn");
const mensaje = document.getElementById("mensaje");
const contador = document.getElementById("contador");
const tbody = document.querySelector("#tablaCalidades tbody");
const textoVacio = document.getElementById("vacio");
const buscador = document.getElementById("searchInput");

// ===== DATOS FALSOS (se usan mientras no exista la API) =====
// Son solo ejemplos: el negocio define libremente los nombres de sus calidades
// (Original, 1.1, AAA, Nacional...). El sistema no las limita a una lista fija.
let calidades = [
    { id: 1, nombre: "Original" },
    { id: 2, nombre: "1.1" },
    { id: 3, nombre: "AAA" },
];

// ===== ACCESO A DATOS =====
// Estas dos funciones son las únicas que cambian cuando llegue la API.

async function obtenerCalidades() {
    if (!USAR_API) return calidades;

    const res = await fetch(`${API}/calidades`);
    if (!res.ok) throw new Error("No se pudieron cargar las calidades.");
    return res.json();
}

async function guardarCalidad(datos) {
    if (!USAR_API) {
        const siguienteId = calidades.reduce((max, c) => Math.max(max, c.id), 0) + 1;
        const nueva = { id: siguienteId, ...datos };
        calidades.push(nueva);
        return nueva;
    }

    const res = await fetch(`${API}/calidades`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(datos),
    });
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.mensaje || "No se pudo crear la calidad.");
    }
    return res.json();
}

// ===== PANTALLA =====

function mostrarMensaje(texto, tipo) {
    mensaje.textContent = texto;
    mensaje.className = "mensaje " + (tipo || "");
}

// Evita que se inserte HTML escrito por el usuario en la tabla
function escapar(texto) {
    const div = document.createElement("div");
    div.textContent = texto ?? "";
    return div.innerHTML;
}

function dibujarTabla(lista) {
    const filtro = buscador.value.trim().toLowerCase();
    const visibles = lista.filter(c => c.nombre.toLowerCase().includes(filtro));

    tbody.innerHTML = visibles
        .map(c => `<tr><td>${escapar(c.nombre)}</td></tr>`)
        .join("");

    textoVacio.hidden = lista.length > 0;
    contador.textContent = lista.length;
}

async function cargarCalidades() {
    try {
        const lista = await obtenerCalidades();
        dibujarTabla(lista);
    } catch (e) {
        mostrarMensaje(e.message, "error");
    }
}

form.addEventListener("submit", async function (event) {
    event.preventDefault();

    const nombre = inputNombre.value.trim().replace(/\s+/g, " "); // sin espacios de más

    if (nombre === "") {
        mostrarMensaje("Escribe el nombre de la calidad.", "error");
        inputNombre.focus();
        return;
    }

    botonGuardar.disabled = true; // evita doble clic
    try {
        const existe = (await obtenerCalidades()).some(
            c => c.nombre.toLowerCase() === nombre.toLowerCase()
        );
        if (existe) {
            mostrarMensaje("Ya existe una calidad con ese nombre.", "error");
            return;
        }

        await guardarCalidad({ nombre });
        form.reset();
        mostrarMensaje("Calidad guardada.", "ok");
        await cargarCalidades();
    } catch (e) {
        mostrarMensaje(e.message, "error");
    } finally {
        botonGuardar.disabled = false;
    }
});

buscador.addEventListener("input", cargarCalidades);

document.addEventListener("DOMContentLoaded", cargarCalidades);