const USAR_API = true;
const formUsuario = document.getElementById("usuarioForm");
const mensajeUsuario = document.getElementById("mensaje");
const botonGuardarUsuario = document.getElementById("guardarBtn");
const cuerpoTablaUsuarios = document.querySelector("#tablaUsuarios tbody");
const contadorUsuarios = document.getElementById("contador");
const textoSinUsuarios = document.getElementById("vacio");
const buscadorUsuarios = document.getElementById("searchInput");
let usuarios = [];

const esDueno = requerirRol("DUENO");
if (!esDueno) document.querySelector(".dashboard").hidden = true;

function mostrarMensajeUsuario(texto, tipo = "") {
    mensajeUsuario.textContent = texto;
    mensajeUsuario.className = `mensaje ${tipo}`;
}

function escaparUsuario(texto) {
    const elemento = document.createElement("span");
    elemento.textContent = texto ?? "";
    return elemento.innerHTML;
}

function dibujarUsuarios() {
    const filtro = buscadorUsuarios.value.trim().toLowerCase();
    const visibles = usuarios.filter(usuario =>
        `${usuario.nombre} ${usuario.username} ${usuario.rol}`.toLowerCase().includes(filtro)
    );
    cuerpoTablaUsuarios.innerHTML = visibles.map(usuario => `
        <tr>
            <td>${escaparUsuario(usuario.nombre)}</td>
            <td>${escaparUsuario(usuario.username)}</td>
            <td>${escaparUsuario(usuario.rol)}</td>
            <td>${usuario.activo ? "Activo" : "Inactivo"}</td>
        </tr>
    `).join("");
    contadorUsuarios.textContent = String(usuarios.length);
    textoSinUsuarios.hidden = usuarios.length > 0;
}

async function cargarUsuarios() {
    if (!USAR_API) return;
    usuarios = await apiFetch("/usuarios");
    dibujarUsuarios();
}

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
    botonGuardarUsuario.disabled = true;
    try {
        await apiFetch("/usuarios", { method: "POST", body: JSON.stringify(datos) });
        formUsuario.reset();
        mostrarMensajeUsuario("Usuario creado.", "ok");
        await cargarUsuarios();
    } catch (error) {
        mostrarMensajeUsuario(error.message, "error");
    } finally {
        botonGuardarUsuario.disabled = false;
    }
});

if (esDueno) {
    buscadorUsuarios.addEventListener("input", dibujarUsuarios);
    cargarUsuarios().catch(error => mostrarMensajeUsuario(error.message, "error"));
} else {
    formUsuario.addEventListener("submit", event => event.preventDefault());
}
