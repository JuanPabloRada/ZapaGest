// ===== PERMISOS =====
// Solo el dueño lista y crea usuarios (lo exige también el backend).
// Si el rol no puede entrar, requerirRol redirige al dashboard.
const esDueno = requerirRol("DUENO");
if (!esDueno) document.querySelector(".dashboard").hidden = true;

// ===== ELEMENTOS DEL HTML =====
const formUsuario = document.getElementById("usuarioForm");
const mensajeUsuario = document.getElementById("mensaje");
const botonGuardarUsuario = document.getElementById("guardarBtn");
const cuerpoTablaUsuarios = document.querySelector("#tablaUsuarios tbody");
const contadorUsuarios = document.getElementById("contador");
const textoSinUsuarios = document.getElementById("vacio");
const buscadorUsuarios = document.getElementById("searchInput"); // puede ser null

let usuarios = [];

// Roles del backend → texto que ve el usuario
const ETIQUETAS_ROL = { DUENO: "Dueño", ADMIN: "Administrador", VENDEDOR: "Vendedor" };

function etiquetaRol(rol) {
    return ETIQUETAS_ROL[rol] || rol || "";
}

// ===== ACCESO A DATOS (API) =====

function obtenerUsuarios() {
    return apiFetch("/usuarios");
}

function crearUsuario(datos) {
    return apiFetch("/usuarios", { method: "POST", body: JSON.stringify(datos) });
}

// Ruta supuesta: PATCH /api/usuarios/{id} con { activo: true|false }
// Si el backend usa otra ruta, solo hay que cambiar esta función.
function cambiarEstadoUsuario(id, activo) {
    return apiFetch(`/usuarios/${id}`, {
        method: "PATCH",
        body: JSON.stringify({ activo }),
    });
}

// ===== UTILIDADES =====

function mostrarMensajeUsuario(texto, tipo = "") {
    if (!mensajeUsuario) return;
    mensajeUsuario.textContent = texto;
    mensajeUsuario.className = `mensaje ${tipo}`;
}

// "Failed to fetch" = el backend no responde
function textoError(error) {
    return error instanceof TypeError ? "No se pudo conectar con el servidor." : error.message;
}

// Evita que se inserte HTML escrito por el usuario en la tabla
function escaparUsuario(texto) {
    const elemento = document.createElement("span");
    elemento.textContent = texto ?? "";
    return elemento.innerHTML.replace(/"/g, "&quot;");
}

// ===== PANTALLA =====

function dibujarUsuarios() {
    const filtro = (buscadorUsuarios?.value || "").trim().toLowerCase();
    const visibles = usuarios.filter(usuario =>
        `${usuario.nombre} ${usuario.username} ${etiquetaRol(usuario.rol)}`
            .toLowerCase()
            .includes(filtro)
    );

    cuerpoTablaUsuarios.innerHTML = visibles.map(usuario => {
        const activo = usuario.activo !== false;
        return `
            <tr>
                <td>${escaparUsuario(usuario.nombre)}</td>
                <td>${escaparUsuario(usuario.username)}</td>
                <td>${escaparUsuario(etiquetaRol(usuario.rol))}</td>
                <td><span class="estado ${activo ? "activa" : "inactiva"}">${activo ? "Activo" : "Inactivo"}</span></td>
                <td>
                    <button type="button" class="btn-estado btn-cambiar-estado"
                        data-id="${escaparUsuario(String(usuario.id))}"
                        data-activo="${activo}">
                        ${activo ? "Desactivar" : "Activar"}</button>
                </td>
            </tr>`;
    }).join("");

    contadorUsuarios.textContent = String(usuarios.length);
    textoSinUsuarios.hidden = usuarios.length > 0;
}

async function cargarUsuarios() {
    usuarios = await obtenerUsuarios();
    dibujarUsuarios();
}

// ===== EVENTOS =====

// Crear usuario
formUsuario.addEventListener("submit", async event => {
    event.preventDefault();
    if (!esDueno) return;
    if (!formUsuario.reportValidity()) return;

    const datos = {
        nombre: formUsuario.elements.nombre.value.trim(),
        username: formUsuario.elements.username.value.trim(),
        password: formUsuario.elements.password.value,
        rol: formUsuario.elements.rol.value,
    };

    botonGuardarUsuario.disabled = true; // evita doble clic
    try {
        await crearUsuario(datos);
        formUsuario.reset();
        mostrarMensajeUsuario("Usuario creado.", "ok");
        await cargarUsuarios();
    } catch (error) {
        mostrarMensajeUsuario(textoError(error), "error");
    } finally {
        botonGuardarUsuario.disabled = false;
    }
});

// Activar / Desactivar (un solo listener para todos los botones)
cuerpoTablaUsuarios.addEventListener("click", async event => {
    const boton = event.target.closest(".btn-cambiar-estado");
    if (!boton || !esDueno) return;

    const activar = boton.dataset.activo !== "true";
    boton.disabled = true;
    try {
        await cambiarEstadoUsuario(boton.dataset.id, activar);
        mostrarMensajeUsuario(activar ? "Usuario activado." : "Usuario desactivado.", "ok");
        await cargarUsuarios();
    } catch (error) {
        mostrarMensajeUsuario(textoError(error), "error");
        boton.disabled = false;
    }
});

// Buscador del topbar (si existe en esta pantalla)
buscadorUsuarios?.addEventListener("input", dibujarUsuarios);

// Carga inicial
if (esDueno) {
    cargarUsuarios().catch(error => mostrarMensajeUsuario(textoError(error), "error"));
}