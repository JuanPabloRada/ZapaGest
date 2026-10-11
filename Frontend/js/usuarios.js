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
const buscadorUsuarios = document.getElementById("searchInput");

let usuarios = [];

// Roles del backend → texto que ve el usuario
const ETIQUETAS_ROL = { DUENO: "Dueño", ADMIN: "Administrador", VENDEDOR: "Vendedor" };

function etiquetaRol(rol) {
    return ETIQUETAS_ROL[rol] || rol || "";
}

// ===== UTILIDADES =====

function mostrarMensajeUsuario(texto, tipo = "") {
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
    return elemento.innerHTML;
}

// ===== PANTALLA =====

function dibujarUsuarios() {
    const filtro = buscadorUsuarios.value.trim().toLowerCase();
    const visibles = usuarios.filter(usuario =>
        `${usuario.nombre} ${usuario.username} ${etiquetaRol(usuario.rol)}`.toLowerCase().includes(filtro)
    );

    cuerpoTablaUsuarios.innerHTML = visibles.map(usuario => `
        <tr>
            <td>${escaparUsuario(usuario.nombre)}</td>
            <td>${escaparUsuario(usuario.username)}</td>
            <td>${escaparUsuario(etiquetaRol(usuario.rol))}</td>
            <td>${usuario.activo !== false ? "Activo" : "Inactivo"}</td>
        </tr>
    `).join("");

    contadorUsuarios.textContent = String(usuarios.length);
    textoSinUsuarios.hidden = usuarios.length > 0;
}

async function cargarUsuarios() {
    usuarios = await apiFetch("/usuarios");
    dibujarUsuarios();
}

// ===== EVENTOS =====

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
        await apiFetch("/usuarios", { method: "POST", body: JSON.stringify(datos) });
        formUsuario.reset();
        mostrarMensajeUsuario("Usuario creado.", "ok");
        await cargarUsuarios();
    } catch (error) {
        mostrarMensajeUsuario(textoError(error), "error");
    } finally {
        botonGuardarUsuario.disabled = false;
    }
});

if (esDueno) {
    buscadorUsuarios.addEventListener("input", dibujarUsuarios);
    cargarUsuarios().catch(error => mostrarMensajeUsuario(textoError(error), "error"));
}