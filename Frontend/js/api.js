const API_BASE_URL = "http://localhost:8080/api";
const TOKEN_KEY = "jwt_token";
const USER_KEY = "usuario";

function guardarSesion(datos) {
    sessionStorage.setItem(TOKEN_KEY, datos.token);
    sessionStorage.setItem(USER_KEY, JSON.stringify(datos.usuario));
}

function cerrarSesion(redirigir = true) {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    if (redirigir) window.location.href = "login.html";
}

function obtenerUsuarioSesion() {
    const usuario = sessionStorage.getItem(USER_KEY);
    if (!usuario) return null;
    try {
        return JSON.parse(usuario);
    } catch (error) {
        if (!(error instanceof SyntaxError)) throw error;
        cerrarSesion();
        return null;
    }
}

function requerirSesion() {
    if (!sessionStorage.getItem(TOKEN_KEY) || !obtenerUsuarioSesion()) {
        cerrarSesion();
        return false;
    }
    return true;
}

function requerirRol(...roles) {
    const usuario = obtenerUsuarioSesion();
    if (!usuario || !roles.includes(usuario.rol)) {
        window.location.href = "dashboard.html";
        return false;
    }
    return true;
}

async function apiFetch(endpoint, options = {}) {
    const token = sessionStorage.getItem(TOKEN_KEY);
    const headers = new Headers(options.headers || {});
    if (options.body != null && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    if (token) headers.set("Authorization", `Bearer ${token}`);

    const response = await fetch(`${API_BASE_URL}${endpoint}`, { ...options, headers });
    if (response.status === 401 && endpoint !== "/auth/login") {
        cerrarSesion();
    }

    const data = response.status === 204 ? null : response.headers.get("Content-Type")?.includes("application/json")
        ? await response.json()
        : null;
    if (!response.ok) {
        throw new Error(data?.mensaje || (response.status === 401
            ? "Usuario o contraseña incorrectos"
            : "Ocurrió un error inesperado"));
    }
    return data;
}
