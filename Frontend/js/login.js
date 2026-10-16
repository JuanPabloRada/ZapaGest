// ===== ELEMENTOS DEL HTML =====
const loginForm = document.getElementById("loginForm");
const loginMessage = document.getElementById("loginMessage");
const loginButton = loginForm.querySelector('button[type="submit"]');

// "Failed to fetch" = el backend no responde
function textoError(error) {
    return error instanceof TypeError ? "No se pudo conectar con el servidor." : error.message;
}

// ===== EVENTOS =====

loginForm.addEventListener("submit", async event => {
    event.preventDefault();

    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value;

    if (!username || !password) {
        loginMessage.textContent = "Por favor, completa todos los campos.";
        return;
    }

    loginButton.disabled = true; // evita doble clic
    loginMessage.textContent = "Ingresando...";
    cerrarSesion(false); // borra cualquier sesión anterior sin redirigir

    try {
        // El backend responde { token, usuario: { id, nombre, username, rol } }
        const sesion = await apiFetch("/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
        });
        guardarSesion(sesion);
        window.location.href = "dashboard.html";
    } catch (error) {
        cerrarSesion(false);
        loginMessage.textContent = textoError(error);
        loginButton.disabled = false;
    }
});