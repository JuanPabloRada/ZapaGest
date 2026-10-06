const USAR_API_LOGIN = true;
const loginForm = document.getElementById("loginForm");
const loginMessage = document.getElementById("loginMessage");
const loginButton = loginForm.querySelector('button[type="submit"]');

loginForm.addEventListener("submit", async event => {
    event.preventDefault();
    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value;

    if (!username || !password) {
        loginMessage.textContent = "Por favor, completa todos los campos.";
        return;
    }

    if (!USAR_API_LOGIN) {
        loginMessage.textContent = "El inicio de sesión está deshabilitado.";
        return;
    }

    loginButton.disabled = true;
    loginMessage.textContent = "Ingresando...";
    cerrarSesion(false);
    try {
        const sesion = await apiFetch("/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
        });
        guardarSesion(sesion);
        window.location.href = "dashboard.html";
    } catch (error) {
        cerrarSesion(false);
        loginMessage.textContent = error.message;
    } finally {
        loginButton.disabled = false;
    }
});