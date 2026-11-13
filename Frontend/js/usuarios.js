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
const sesionActual = obtenerUsuarioSesion();

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
    const filtro = (buscadorUsuarios?.value ?? "").trim().toLowerCase(); // el buscador puede no existir en la página
    const visibles = usuarios.filter(usuario =>
        `${usuario.nombre} ${usuario.username} ${etiquetaRol(usuario.rol)}`.toLowerCase().includes(filtro)
    );

    cuerpoTablaUsuarios.innerHTML = visibles.map(usuario => {
        const activo = usuario.activo !== false;
        const esYo = sesionActual && String(sesionActual.id) === String(usuario.id);
        return `
        <tr>
            <td>${escaparUsuario(usuario.nombre)}</td>
            <td>${escaparUsuario(usuario.username)}</td>
            <td>${escaparUsuario(etiquetaRol(usuario.rol))}</td>
            <td><span class="estado ${activo ? "activa" : "inactiva"}">${activo ? "Activo" : "Inactivo"}</span></td>
            <td><button type="button" class="btn-estado btn-cambiar-estado"
                data-id="${escaparUsuario(String(usuario.id))}" data-activo="${activo}"
                ${esYo ? 'disabled title="No puedes desactivarte a ti mismo"' : ""}>
                ${activo ? "Desactivar" : "Activar"}</button></td>
        </tr>`;
    }).join("");

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
    buscadorUsuarios?.addEventListener("input", dibujarUsuarios);

    // Activar / desactivar: PATCH /api/usuarios/{id} con { activo } (solo el dueño; no puede desactivarse a sí mismo)
    cuerpoTablaUsuarios.addEventListener("click", async event => {
        const boton = event.target.closest(".btn-cambiar-estado");
        if (!boton) return;

        const usuario = usuarios.find(u => String(u.id) === boton.dataset.id);
        const activar = boton.dataset.activo !== "true";
        if (!activar && !confirm(`¿Desactivar a ${usuario?.nombre ?? "este usuario"}? Perderá el acceso de inmediato.`)) return;

        boton.disabled = true;
        try {
            await apiFetch(`/usuarios/${boton.dataset.id}`, {
                method: "PATCH",
                body: JSON.stringify({ activo: activar }),
            });
            mostrarMensajeUsuario(activar ? "Usuario activado." : "Usuario desactivado.", "ok");
            await cargarUsuarios();
        } catch (error) {
            mostrarMensajeUsuario(textoError(error), "error");
            boton.disabled = false;
        }
    });
    cargarUsuarios().catch(error => mostrarMensajeUsuario(textoError(error), "error"));
}