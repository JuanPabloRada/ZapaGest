// ===== PERMISOS =====
// Solo el dueño configura las calidades (HU-03). Si el rol no puede entrar,
// requerirRol redirige al dashboard.
const tienePermiso = requerirRol("DUENO");
if (!tienePermiso) document.querySelector(".dashboard").hidden = true;

// ===== ELEMENTOS DEL HTML =====
const form = document.getElementById("calidadForm");
const tituloForm = document.getElementById("tituloForm");
const inputNombre = document.getElementById("nombre");
const botonGuardar = document.getElementById("guardarBtn");
const botonCancelar = document.getElementById("cancelarBtn");
const mensaje = document.getElementById("mensaje");
const contador = document.getElementById("contador");
const tbody = document.querySelector("#tablaCalidades tbody");
const textoVacio = document.getElementById("vacio");
const buscador = document.getElementById("searchInput");

// ===== ESTADO =====
let calidadesActuales = []; // última lista dibujada
let editandoId = null;      // null = creando; con valor = editando esa calidad

// ===== ACCESO A DATOS (API) =====
// apiFetch (api.js) ya agrega el token y convierte los errores en excepciones.
// El negocio define libremente los nombres: el sistema no limita las calidades
// a una lista fija (Original, 1.1, AAA, Nacional...).

function obtenerCalidades() {
    return apiFetch("/calidades");
}

function guardarCalidad(datos) {
    return apiFetch("/calidades", { method: "POST", body: JSON.stringify(datos) });
}


function actualizarCalidad(id, datos) {
    return apiFetch(`/calidades/${id}`, { method: "PUT", body: JSON.stringify(datos) });
}

// ===== PANTALLA =====


function mostrarMensaje(texto, tipo) {
    if (!mensaje) {
        console.error(
            'No se encontró el elemento HTML con id="mensaje".'
        );
        return;
    }

    mensaje.textContent = texto;
    mensaje.className = "mensaje " + (tipo || "");
}


// Evita que se inserte HTML escrito por el usuario en la tabla
function escapar(texto) {
    const div = document.createElement("div");
    div.textContent = texto ?? "";
    return div.innerHTML.replace(/"/g, "&quot;");
}

function entrarModoEdicion(calidad) {
    editandoId = calidad.id;
    inputNombre.value = calidad.nombre;
    tituloForm.textContent = "Editar calidad";
    botonGuardar.textContent = "Guardar cambios";
    botonCancelar.hidden = false;
    mostrarMensaje("", "");
    inputNombre.focus();
    form.scrollIntoView({ behavior: "smooth", block: "center" });
}

function salirModoEdicion() {
    editandoId = null;
    form.reset();
    tituloForm.textContent = "Nueva calidad";
    botonGuardar.textContent = "Guardar calidad";
    botonCancelar.hidden = true;
}

function dibujarTabla(lista) {
    calidadesActuales = lista;
    const filtro = (buscador?.value || "").trim().toLowerCase();
    const visibles = lista.filter(c => c.nombre.toLowerCase().includes(filtro));

    tbody.innerHTML = visibles
        .map(c => `<tr>
            <td>${escapar(c.nombre)}</td>
            <td><div class="acciones">
                <button type="button" class="btn-estado btn-editar"
                    data-id="${escapar(String(c.id))}">Editar</button>
            </div></td>
        </tr>`)
        .join("");

    textoVacio.hidden = lista.length > 0;
    contador.textContent = lista.length;
}

async function cargarCalidades() {
    try {
        dibujarTabla(await obtenerCalidades());
    } catch (e) {
        mostrarMensaje(e.message, "error");
    }
}

// ===== EVENTOS =====

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
        // Nombres repetidos no se permiten (al editar se ignora la propia calidad)
        const existe = (await obtenerCalidades()).some(c =>
            String(c.id) !== String(editandoId) &&
            c.nombre.toLowerCase() === nombre.toLowerCase()
        );
        if (existe) {
            mostrarMensaje("Ya existe una calidad con ese nombre.", "error");
            return;
        }

        if (editandoId !== null) {
            await actualizarCalidad(editandoId, { nombre });
            salirModoEdicion();
            mostrarMensaje("Calidad actualizada.", "ok");
        } else {
            await guardarCalidad({ nombre });
            form.reset();
            mostrarMensaje("Calidad guardada.", "ok");
        }
        await cargarCalidades();
    } catch (e) {
        mostrarMensaje(e.message, "error");
    } finally {
        botonGuardar.disabled = false;
    }
});

botonCancelar.addEventListener("click", function () {
    salirModoEdicion();
    mostrarMensaje("", "");
});

tbody.addEventListener("click", function (event) {
    const botonEditar = event.target.closest(".btn-editar");
    if (!botonEditar) return;
    const calidad = calidadesActuales.find(c => String(c.id) === botonEditar.dataset.id);
    if (calidad) entrarModoEdicion(calidad);
});

buscador?.addEventListener("input", dibujarLista);

document.addEventListener("DOMContentLoaded", cargarCalidades);