// ===== CONFIGURACIÓN =====
const MAX_BODEGAS = 10;
const USAR_API = true; // true = usa el backend de Joseph; false = datos falsos
const API = "http://localhost:8080/api";

// ===== ELEMENTOS DEL HTML =====
const form = document.getElementById("bodegaForm");
const inputNombre = document.getElementById("nombre");
const botonGuardar = document.getElementById("guardarBtn");
const mensaje = document.getElementById("mensaje");
const contador = document.getElementById("contador");
const tbody = document.querySelector("#tablaBodegas tbody");
const textoVacio = document.getElementById("vacio");
const buscador = document.getElementById("searchInput");

// ===== DATOS FALSOS (se usan mientras no exista la API) =====
let bodegas = [
    { id: 1, nombre: "Local Principal" },
    { id: 2, nombre: "Bodega 1" },
];

// ===== ACCESO A DATOS =====
// Estas dos funciones son las únicas que cambian cuando llegue la API.

async function obtenerBodegas() {
    if (!USAR_API) return bodegas;

    const res = await fetch(`${API}/ubicaciones`);
    if (!res.ok) throw new Error("No se pudieron cargar las bodegas.");
    return res.json();
}

async function guardarBodega(datos) {
    if (!USAR_API) {
        const nueva = { id: Date.now(), ...datos };
        bodegas.push(nueva);
        return nueva;
    }

    const res = await fetch(`${API}/ubicaciones`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(datos),
    });
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.mensaje || "No se pudo crear la bodega.");
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
    const visibles = lista.filter(b => b.nombre.toLowerCase().includes(filtro));

    tbody.innerHTML = visibles
        .map(b => `<tr><td>${escapar(b.nombre)}</td></tr>`)
        .join("");

    textoVacio.hidden = lista.length > 0;

    const lleno = lista.length >= MAX_BODEGAS;
    contador.textContent = `${lista.length} de ${MAX_BODEGAS}`;
    contador.classList.toggle("lleno", lleno);
    botonGuardar.disabled = lleno;

    if (lleno) {
        mostrarMensaje("Llegaste al máximo de 10 bodegas.", "error");
    }
}

async function cargarBodegas() {
    try {
        const lista = await obtenerBodegas();
        dibujarTabla(lista);
    } catch (e) {
        mostrarMensaje(e.message, "error");
    }
}

form.addEventListener("submit", async function (event) {
    event.preventDefault();

    const nombre = inputNombre.value.trim();

    if (nombre === "") {
        mostrarMensaje("Escribe el nombre de la bodega.", "error");
        inputNombre.focus();
        return;
    }

    const existe = (await obtenerBodegas()).some(
        b => b.nombre.toLowerCase() === nombre.toLowerCase()
    );
    if (existe) {
        mostrarMensaje("Ya existe una bodega con ese nombre.", "error");
        return;
    }

    botonGuardar.disabled = true; // evita doble clic
    try {
        await guardarBodega({ nombre });
        form.reset();
        mostrarMensaje("Bodega guardada.", "ok");
        await cargarBodegas();
    } catch (e) {
        mostrarMensaje(e.message, "error");
        botonGuardar.disabled = false;
    }
});

buscador.addEventListener("input", cargarBodegas);

document.addEventListener("DOMContentLoaded", cargarBodegas);