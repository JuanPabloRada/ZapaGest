const tienePermiso = requerirRol("DUENO", "ADMIN");
if (!tienePermiso) document.querySelector(".dashboard").hidden = true;

// ===== CONFIGURACIÓN =====
const MAX_BODEGAS = 10;
const MENSAJE_LIMITE = "Ha alcanzado el límite máximo permitido de 10 ubicaciones";
const USAR_API = true;

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
// "activo" indica si la bodega está en uso. El contador solo cuenta las activas.
let bodegas = [
    { id: 1, nombre: "Local Principal", activo: true },
    { id: 2, nombre: "Bodega 1", activo: true },
    { id: 3, nombre: "Bodega antigua", activo: false },
];

// ===== REGLAS DE NEGOCIO (HU-04) =====

// Si el backend no envía "activo", se asume que la bodega está activa
function esActiva(bodega) {
    return bodega.activa !== false && bodega.activa !== false;
}

function contarActivas(lista) {
    return lista.filter(esActiva).length;
}

function limiteAlcanzado(lista) {
    return contarActivas(lista) >= MAX_BODEGAS;
}

// ===== ACCESO A DATOS =====
// Estas funciones son las únicas que cambian cuando llegue la API.

async function obtenerBodegas() {
    if (!USAR_API) return bodegas;
    return apiFetch("/ubicaciones");
}

async function guardarBodega(datos) {
    if (!USAR_API) {
        const siguienteId = bodegas.reduce((max, b) => Math.max(max, b.id), 0) + 1;
        const nueva = { id: siguienteId, activo: true, ...datos };
        bodegas.push(nueva);
        return nueva;
    }

    return apiFetch("/ubicaciones", {
        method: "POST",
        body: JSON.stringify(datos),
    });
}

// Activa o desactiva una bodega.
// OJO: este endpoint (PATCH /api/ubicaciones/{id}) hay que acordarlo con Joseph.
async function cambiarEstadoBodega(id, activo) {
    if (!USAR_API) {
        const bodega = bodegas.find(b => String(b.id) === String(id));
        if (bodega) bodega.activo = activo;
        return bodega;
    }

    return apiFetch(`/ubicaciones/${id}`, {
        method: "PATCH",
        body: JSON.stringify({ activa: activo }),
    });
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
    return div.innerHTML.replace(/"/g, "&quot;");
}

function dibujarTabla(lista) {
    const filtro = buscador.value.trim().toLowerCase();
    const visibles = lista.filter(b => b.nombre.toLowerCase().includes(filtro));

    tbody.innerHTML = visibles
        .map(b => {
            const activa = esActiva(b);
            return `<tr>
                <td>${escapar(String(b.id))}</td>
                <td>${escapar(b.nombre)}</td>
                <td><span class="estado ${activa ? "activa" : "inactiva"}">${activa ? "Activa" : "Inactiva"}</span></td>
                <td><button type="button" class="btn-estado"
                    data-id="${escapar(String(b.id))}" data-activa="${activa}">
                    ${activa ? "Desactivar" : "Activar"}</button></td>
            </tr>`;
        })
        .join("");

    textoVacio.hidden = lista.length > 0;

    // El contador solo toma en cuenta las bodegas activas
    contador.textContent = `${contarActivas(lista)} de ${MAX_BODEGAS}`;
    contador.title = "Solo cuenta las bodegas activas";
    contador.classList.toggle("lleno", limiteAlcanzado(lista));
}

async function cargarBodegas() {
    try {
        const lista = await obtenerBodegas();
        dibujarTabla(lista);
    } catch (e) {
        mostrarMensaje(e.message, "error");
    }
}

// ===== EVENTOS =====

// Registrar una nueva bodega
form.addEventListener("submit", async function (event) {
    event.preventDefault();

    const nombre = inputNombre.value.trim();

    if (nombre === "") {
        mostrarMensaje("Escribe el nombre de la bodega.", "error");
        inputNombre.focus();
        return;
    }

    botonGuardar.disabled = true; // evita doble clic
    try {
        const lista = await obtenerBodegas();

        // Si ya hay 10 activas, se bloquea la creación de la número 11
        if (limiteAlcanzado(lista)) {
            mostrarMensaje(MENSAJE_LIMITE, "error");
            dibujarTabla(lista);
            return;
        }

        // Cada ubicación se identifica por su nombre: no se permiten repetidos
        const existe = lista.some(b => b.nombre.toLowerCase() === nombre.toLowerCase());
        if (existe) {
            mostrarMensaje("Ya existe una bodega con ese nombre.", "error");
            return;
        }

        await guardarBodega({ nombre });
        form.reset();
        mostrarMensaje("Bodega guardada.", "ok");
        await cargarBodegas();
    } catch (e) {
        mostrarMensaje(e.message, "error");
    } finally {
        botonGuardar.disabled = false;
    }
});

// Activar / desactivar una bodega desde la tabla
tbody.addEventListener("click", async function (event) {
    const boton = event.target.closest(".btn-estado");
    if (!boton) return;

    const id = boton.dataset.id;
    const activar = boton.dataset.activa !== "true";

    boton.disabled = true;
    try {
        const lista = await obtenerBodegas();

        // Reactivar una bodega no puede pasar el máximo de 10 activas
        if (activar && limiteAlcanzado(lista)) {
            mostrarMensaje(MENSAJE_LIMITE, "error");
            return;
        }

        await cambiarEstadoBodega(id, activar);
        mostrarMensaje(activar ? "Bodega activada." : "Bodega desactivada.", "ok");
        await cargarBodegas();
    } catch (e) {
        mostrarMensaje(e.message, "error");
    } finally {
        boton.disabled = false;
    }
});

buscador.addEventListener("input", cargarBodegas);

document.addEventListener("DOMContentLoaded", cargarBodegas);