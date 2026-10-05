// ===== CONFIGURACIÓN =====
const USAR_API = false; // true cuando Juan Pablo tenga GET/POST /api/usuarios
const RUTA_USUARIOS = `${API_BASE}/usuarios`;
const LARGO_MIN_PASSWORD = 6; // ajústalo a la regla de validación del backend

// ===== ELEMENTOS DEL HTML =====
const form = document.getElementById("usuarioForm");
const inputNombre = document.getElementById("nombre");
const inputUsername = document.getElementById("username");
const inputPassword = document.getElementById("password");
const selectRol = document.getElementById("rol");
const botonGuardar = document.getElementById("guardarBtn");
const mensaje = document.getElementById("mensaje");
const contador = document.getElementById("contador");
const tbody = document.querySelector("#tablaUsuarios tbody");
const textoVacio = document.getElementById("vacio");
const buscador = document.getElementById("searchInput");

// ===== DATOS FALSOS (se usan mientras no exista la API) =====
let usuarios = [
    { id: 1, nombre: "Angie", username: "angie", rol: "DUENO" },
    { id: 2, nombre: "Carlos Gómez", username: "cgomez", rol: "ADMIN" },
    { id: 3, nombre: "Laura Ruiz", username: "lruiz", rol: "VENDEDOR" },
];

// ===== PERMISOS =====
// El dueño puede crear cualquier rol. El administrador solo crea vendedores,
// para que no pueda darse a sí mismo (o a otro) más permisos de los que tiene.
// Confirmar esta regla con el Product Owner.
function rolesQuePuedeCrear() {
    const sesion = obtenerSesion();
    if (!sesion) return [];
    if (sesion.rol === "DUENO") return ["DUENO", "ADMIN", "VENDEDOR"];
    if (sesion.rol === "ADMIN") return ["VENDEDOR"];
    return [];
}

// ===== ACCESO A DATOS =====
// Estas dos funciones son las únicas que cambian cuando llegue la API.

async function obtenerUsuarios() {
    if (!USAR_API) return usuarios;

    const res = await apiFetch(RUTA_USUARIOS);
    if (res.status === 403) throw new Error("No tienes permiso para ver los usuarios.");
    if (!res.ok) throw new Error("No se pudieron cargar los usuarios.");
    return res.json();
}

async function guardarUsuario(datos) {
    if (!USAR_API) {
        const siguienteId = usuarios.reduce((max, u) => Math.max(max, u.id), 0) + 1;
        const nuevo = { id: siguienteId, nombre: datos.nombre, username: datos.username, rol: datos.rol };
        usuarios.push(nuevo); // la contraseña nunca se guarda ni se muestra en pantalla
        return nuevo;
    }

    const res = await apiFetch(RUTA_USUARIOS, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(datos), // { nombre, username, password, rol }
    });
    if (res.status === 403) throw new Error("No tienes permiso para crear usuarios.");
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.mensaje || "No se pudo crear el usuario.");
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
    return div.innerHTML.replace(/"/g, "&quot;");
}

function dibujarRoles() {
    const permitidos = rolesQuePuedeCrear();

    selectRol.innerHTML =
        `<option value="">Elige un rol</option>` +
        permitidos.map(r => `<option value="${r}">${escapar(etiquetaRol(r))}</option>`).join("");

    if (permitidos.length === 0) {
        form.querySelectorAll("input, select, button").forEach(el => { el.disabled = true; });
        mostrarMensaje("Tu rol no puede crear usuarios.", "error");
    }
}

function dibujarTabla(lista) {
    const filtro = buscador.value.trim().toLowerCase();
    const visibles = lista.filter(u =>
        u.nombre.toLowerCase().includes(filtro) || u.username.toLowerCase().includes(filtro)
    );

    tbody.innerHTML = visibles
        .map(u => `<tr>
            <td>${escapar(String(u.id))}</td>
            <td>${escapar(u.nombre)}</td>
            <td>${escapar(u.username)}</td>
            <td><span class="rol-chip ${escapar(u.rol)}">${escapar(etiquetaRol(u.rol))}</span></td>
        </tr>`)
        .join("");

    textoVacio.hidden = lista.length > 0;
    contador.textContent = lista.length;
}

async function cargarUsuarios() {
    try {
        dibujarTabla(await obtenerUsuarios());
    } catch (e) {
        mostrarMensaje(e.message, "error");
    }
}

// ===== EVENTOS =====

form.addEventListener("submit", async function (event) {
    event.preventDefault();

    const nombre = inputNombre.value.trim().replace(/\s+/g, " ");
    const username = inputUsername.value.trim();
    const password = inputPassword.value;
    const rol = selectRol.value;

    if (!nombre || !username || !password || !rol) {
        mostrarMensaje("Completa nombre, usuario, contraseña y rol.", "error");
        return;
    }
    if (/\s/.test(username) || username.length < 3) {
        mostrarMensaje("El usuario debe tener mínimo 3 caracteres y no puede tener espacios.", "error");
        inputUsername.focus();
        return;
    }
    if (password.length < LARGO_MIN_PASSWORD) {
        mostrarMensaje(`La contraseña debe tener mínimo ${LARGO_MIN_PASSWORD} caracteres.`, "error");
        inputPassword.focus();
        return;
    }
    if (!rolesQuePuedeCrear().includes(rol)) {
        mostrarMensaje("No tienes permiso para asignar ese rol.", "error");
        return;
    }

    botonGuardar.disabled = true; // evita doble clic
    try {
        const lista = await obtenerUsuarios();
        const existe = lista.some(u => u.username.toLowerCase() === username.toLowerCase());
        if (existe) {
            mostrarMensaje("Ya existe un usuario con ese nombre de usuario.", "error");
            return;
        }

        await guardarUsuario({ nombre, username, password, rol });
        form.reset();
        mostrarMensaje("Usuario creado.", "ok");
        await cargarUsuarios();
    } catch (e) {
        mostrarMensaje(e instanceof TypeError ? "No se pudo conectar con el servidor." : e.message, "error");
    } finally {
        botonGuardar.disabled = false;
    }
});

buscador.addEventListener("input", cargarUsuarios);

document.addEventListener("DOMContentLoaded", () => {
    dibujarRoles();
    cargarUsuarios();
});