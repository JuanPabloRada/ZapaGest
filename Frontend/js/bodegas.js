// ===== PERMISOS =====
// Dueño y administrador gestionan las ubicaciones (HU-04). Si el rol no puede entrar,
// requerirRol redirige al dashboard.
const tienePermiso = requerirRol("DUENO", "ADMIN");
if (!tienePermiso) document.querySelector(".dashboard").hidden = true;

// ===== CONFIGURACIÓN =====
const MAX_BODEGAS = 10;
const MENSAJE_LIMITE = "Ha alcanzado el límite máximo permitido de 10 ubicaciones";

// ===== ELEMENTOS DEL HTML =====
const form = document.getElementById("bodegaForm");
const tituloForm = document.getElementById("tituloForm");
const inputNombre = document.getElementById("nombre");
const botonGuardar = document.getElementById("guardarBtn");
const botonCancelar = document.getElementById("cancelarBtn");
const mensaje = document.getElementById("mensaje");
const contador = document.getElementById("contador");
const tbody = document.querySelector("#tablaBodegas tbody");
const textoVacio = document.getElementById("vacio");
const buscador = document.getElementById("searchInput"); 

// ===== ESTADO =====
let bodegasActuales = []; // última lista dibujada
let editandoId = null;    // null = creando; con valor = editando esa bodega

// ===== REGLAS DE NEGOCIO (HU-04) =====
// El backend envía "activa" (true/false). El contador solo cuenta las activas.

function esActiva(bodega) {
    return bodega.activa !== false;
}

function contarActivas(lista) {
    return lista.filter(esActiva).length;
}

function limiteAlcanzado(lista) {
    return contarActivas(lista) >= MAX_BODEGAS;
}

// ===== ACCESO A DATOS (API) =====
// apiFetch (api.js) ya agrega el token y convierte los errores en excepciones.

function obtenerBodegas() {
    return apiFetch("/ubicaciones");
}

function guardarBodega(datos) {
    return apiFetch("/ubicaciones", { method: "POST", body: JSON.stringify(datos) });
}


function actualizarBodega(id, datos) {
    return apiFetch(`/ubicaciones/${id}`, { method: "PUT", body: JSON.stringify(datos) });
}

// Activa o desactiva una bodega: PATCH /api/ubicaciones/{id} con { "activa": true|false }
function cambiarEstadoBodega(id, activa) {
    return apiFetch(`/ubicaciones/${id}`, {
        method: "PATCH",
        body: JSON.stringify({ activa }),
    });
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

function entrarModoEdicion(bodega) {
    editandoId = bodega.id;
    inputNombre.value = bodega.nombre;
    tituloForm.textContent = "Editar bodega";
    botonGuardar.textContent = "Guardar cambios";
    botonCancelar.hidden = false;
    mostrarMensaje("", "");
    inputNombre.focus();
    form.scrollIntoView({ behavior: "smooth", block: "center" });
}

function salirModoEdicion() {
    editandoId = null;
    form.reset();
    tituloForm.textContent = "Nueva bodega";
    botonGuardar.textContent = "Guardar bodega";
    botonCancelar.hidden = true;
}

function dibujarTabla(lista) {
    bodegasActuales = lista;
    const filtro = (buscador?.value || "").trim().toLowerCase();
    const visibles = lista.filter(b => b.nombre.toLowerCase().includes(filtro));

    tbody.innerHTML = visibles
        .map(b => {
            const activa = esActiva(b);
            return `<tr>
                <td>${escapar(String(b.id))}</td>
                <td>${escapar(b.nombre)}</td>
                <td><span class="estado ${activa ? "activa" : "inactiva"}">${activa ? "Activa" : "Inactiva"}</span></td>
                <td><div class="acciones">
                    <button type="button" class="btn-estado btn-editar"
                        data-id="${escapar(String(b.id))}">Editar</button>
                    <button type="button" class="btn-estado btn-cambiar-estado"
                        data-id="${escapar(String(b.id))}" data-activa="${activa}">
                        ${activa ? "Desactivar" : "Activar"}</button>
                </div></td>
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
        dibujarTabla(await obtenerBodegas());
    } catch (e) {
        mostrarMensaje(e.message, "error");
    }
}

// ===== EVENTOS =====

// Registrar una bodega nueva o guardar los cambios de una existente
form.addEventListener("submit", async function (event) {
    event.preventDefault();

    const nombre = inputNombre.value.trim().replace(/\s+/g, " ");

    if (nombre === "") {
        mostrarMensaje("Escribe el nombre de la bodega.", "error");
        inputNombre.focus();
        return;
    }

    botonGuardar.disabled = true; // evita doble clic
    try {
        const lista = await obtenerBodegas();

        // Si ya hay 10 activas, se bloquea la creación de la número 11 (editar no cambia el conteo)
        if (editandoId === null && limiteAlcanzado(lista)) {
            mostrarMensaje(MENSAJE_LIMITE, "error");
            dibujarTabla(lista);
            return;
        }

        // Cada ubicación se identifica por su nombre: no se permiten repetidos
        // (al editar se ignora la propia bodega)
        const existe = lista.some(b =>
            String(b.id) !== String(editandoId) &&
            b.nombre.toLowerCase() === nombre.toLowerCase()
        );
        if (existe) {
            mostrarMensaje("Ya existe una bodega con ese nombre.", "error");
            return;
        }

        if (editandoId !== null) {
            await actualizarBodega(editandoId, { nombre });
            salirModoEdicion();
            mostrarMensaje("Bodega actualizada.", "ok");
        } else {
            await guardarBodega({ nombre });
            form.reset();
            mostrarMensaje("Bodega guardada.", "ok");
        }
        await cargarBodegas();
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

// Botones de la tabla: Editar y Activar / Desactivar
tbody.addEventListener("click", async function (event) {
    const botonEditar = event.target.closest(".btn-editar");
    if (botonEditar) {
        const bodega = bodegasActuales.find(b => String(b.id) === botonEditar.dataset.id);
        if (bodega) entrarModoEdicion(bodega);
        return;
    }

    const boton = event.target.closest(".btn-cambiar-estado");
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

buscador?.addEventListener("input", () => dibujarTabla(bodegasActuales));

document.addEventListener("DOMContentLoaded", cargarBodegas);