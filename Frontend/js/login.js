// Aviso visible si falta cargar sesion.js (debe ir ANTES de login.js en login.html)
if (typeof obtenerSesion === "undefined") {
    const aviso = document.getElementById("loginMessage");
    if (aviso) aviso.textContent = "Error: falta cargar js/sesion.js antes de login.js en login.html.";
    throw new Error("sesion.js no está cargado");
}

// ===== CONFIGURACIÓN =====
const USAR_API_LOGIN = false; // true cuando exista el login real en el backend
const RUTA_LOGIN = `${API_BASE}/auth/login`; // confirmar la ruta con Juan Pablo

// ===== ELEMENTOS DEL HTML =====
const loginForm = document.getElementById("loginForm");
const loginMessage = document.getElementById("loginMessage");
const botonLogin = loginForm.querySelector('button[type="submit"]');

// Si ya hay una sesión abierta, no hace falta volver a iniciar sesión
if (obtenerSesion()) {
    window.location.replace("dashboard.html");
}

// ===== ACCESO A DATOS =====
// Estas dos funciones devuelven lo mismo: { token, nombre, username, rol }

// Mientras no exista el backend: el rol sale del nombre de usuario, para probar los permisos.
//   "vendedor" → Vendedor | "admin" → Administrador | cualquier otro → Dueño
async function iniciarSesionSimulado(username) {
    const usuario = username.toLowerCase();
    const rol = usuario === "vendedor" ? "VENDEDOR" : usuario === "admin" ? "ADMIN" : "DUENO";
    return { token: "token-de-prueba", nombre: username, username, rol };
}

async function iniciarSesionApi(username, password) {
    const res = await apiFetch(RUTA_LOGIN, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
    });

    const datos = await res.json().catch(() => ({}));

    if (!res.ok) {
        throw new Error(
            datos.mensaje ||
            (res.status === 401 ? "Usuario o contraseña incorrectos." : "No se pudo iniciar sesión.")
        );
    }

    // Si el backend usa otros nombres de campo, se ajustan SOLO aquí
    const rol = datos.rol ?? datos.usuario?.rol;
    if (!rol) throw new Error("El servidor no devolvió el rol del usuario.");

    return {
        token: datos.token ?? null,
        nombre: datos.nombre ?? datos.usuario?.nombre ?? username,
        username: datos.username ?? datos.usuario?.username ?? username,
        rol,
    };
}

// ===== EVENTOS =====
loginForm.addEventListener("submit", async function (event) {
    event.preventDefault();

    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value;

    if (username === "" || password === "") {
        loginMessage.textContent = "Por favor, completa todos los campos.";
        return;
    }

    botonLogin.disabled = true; // evita doble clic
    botonLogin.textContent = "Ingresando...";
    loginMessage.textContent = "";

    try {
        const sesion = USAR_API_LOGIN
            ? await iniciarSesionApi(username, password)
            : await iniciarSesionSimulado(username);

        guardarSesion(sesion);
        window.location.href = "dashboard.html";
    } catch (e) {
        // "Failed to fetch" = el backend no está disponible
        loginMessage.textContent = e instanceof TypeError
            ? "No se pudo conectar con el servidor."
            : e.message;
        botonLogin.disabled = false;
        botonLogin.textContent = "Iniciar sesión";
    }
});