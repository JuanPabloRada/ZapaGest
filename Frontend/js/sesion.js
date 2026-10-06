// =====================================================================
// SESIÓN, ROLES Y PETICIONES AL BACKEND (se carga en TODAS las pantallas)
// Debe incluirse ANTES de los demás scripts de cada página:
//   <script src="js/sesion.js"></script>
// =====================================================================

// ===== CONFIGURACIÓN (lo que se acuerda con Juan Pablo y Joseph) =====
const API_BASE = "http://localhost:8080/api";

// Cómo viaja la autenticación:
//   "bearer" → token JWT en el encabezado Authorization
//   "cookie" → sesión del servidor con cookie (el backend debe permitir CORS con credenciales)
const MODO_AUTENTICACION = "bearer";

const CLAVE_SESION = "zapagest_sesion";

// Roles del backend → texto que ve el usuario
const ROLES = { DUENO: "Dueño", ADMIN: "Administrador", VENDEDOR: "Vendedor" };

// Quién puede entrar a cada página. Si una página no aparece, la ve cualquier usuario con sesión.
// OJO: esto solo oculta cosas en la interfaz. La seguridad real la pone el backend.
const PERMISOS_PAGINA = {
    "dashboard.html": ["DUENO", "ADMIN", "VENDEDOR"],
    "bodegas.html": ["DUENO", "ADMIN"],
    "calidades.html": ["DUENO"],          // HU-03: "el dueño puede registrar una calidad"
    "catalogo.html": ["DUENO", "ADMIN"],
    "usuarios.html": ["DUENO", "ADMIN"],
};

// ===== SESIÓN =====
// sessionStorage: la sesión se borra al cerrar la pestaña.

function etiquetaRol(rol) {
    return ROLES[rol] || rol || "";
}

function guardarSesion(sesion) {
    sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
}

function obtenerSesion() {
    try {
        return JSON.parse(sessionStorage.getItem(CLAVE_SESION));
    } catch {
        return null;
    }
}

function cerrarSesion() {
    sessionStorage.removeItem(CLAVE_SESION);
}

function tienePermiso(pagina, rol) {
    const permitidos = PERMISOS_PAGINA[pagina];
    return !permitidos || permitidos.includes(rol);
}

function paginaEnCurso() {
    return location.pathname.split("/").pop() || "dashboard.html";
}

// ===== PETICIONES =====
// Se usa igual que fetch(), pero agrega la autenticación y maneja la sesión vencida.

async function apiFetch(url, opciones = {}) {
    const sesion = obtenerSesion();
    const config = { ...opciones, headers: { ...(opciones.headers || {}) } };

    if (MODO_AUTENTICACION === "bearer" && sesion && sesion.token) {
        config.headers.Authorization = `Bearer ${sesion.token}`;
    }
    if (MODO_AUTENTICACION === "cookie") {
        config.credentials = "include";
    }

    const res = await fetch(url, config);

    // 401 = sesión vencida o sin autenticar (el login mismo se excluye)
    if (res.status === 401 && !url.includes("/auth/")) {
        cerrarSesion();
        location.replace("login.html");
    }
    return res;
}

// ===== PROTECCIÓN DE PÁGINA Y DATOS DEL USUARIO =====

function iniciarPagina() {
    const sesion = obtenerSesion();

    if (!sesion) {
        location.replace("login.html");
        return;
    }

    if (!tienePermiso(paginaEnCurso(), sesion.rol)) {
        alert("No tienes permiso para ver esta página.");
        location.replace("dashboard.html");
        return;
    }

    // Oculta del menú lo que el rol no puede usar
    document.querySelectorAll(".menu-item").forEach(enlace => {
        if (!tienePermiso(enlace.getAttribute("href"), sesion.rol)) {
            enlace.style.display = "none";
        }
    });

    // Muestra quién tiene la sesión abierta
    document.querySelectorAll(".usuario-info strong, .dropdown-perfil strong")
        .forEach(el => { el.textContent = sesion.nombre || sesion.username; });
    document.querySelectorAll(".rol")
        .forEach(el => { el.textContent = etiquetaRol(sesion.rol); });
    document.querySelectorAll(".dropdown-perfil span")
        .forEach(el => { el.textContent = sesion.username || ""; });
}

// En todas las páginas protegidas se ejecuta solo (no en el login ni en la página de inicio)
if (!["login.html", "index.html"].includes(paginaEnCurso())) {
    document.addEventListener("DOMContentLoaded", iniciarPagina);
}