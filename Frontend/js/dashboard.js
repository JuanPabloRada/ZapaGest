// =====================================================================
// COMPORTAMIENTO COMÚN DE LAS PÁGINAS CON MENÚ
// Requiere js/api.js antes. La sesión, los permisos y los datos del usuario
// (nombre, rol) los maneja js/menu-rol.js.
// =====================================================================

// ===== CIERRE DE SESIÓN =====
const logoutButton = document.getElementById("logoutButton");

if (logoutButton) {
    logoutButton.addEventListener("click", function () {
         cerrarSesion(); // borra la sesión y redirige a login.html
    });
}

// ===== MENÚ DESPLEGABLE DEL USUARIO =====
(function () {
    const btn = document.getElementById("usuarioBtn");
    const dropdown = document.getElementById("usuarioDropdown");
    if (!btn || !dropdown) return;

    function cerrarMenu() {
        dropdown.classList.remove("abierto");
        btn.setAttribute("aria-expanded", "false");
    }

    // Abrir / cerrar al hacer clic en el usuario
    btn.addEventListener("click", function (e) {
        e.stopPropagation();
        const abierto = dropdown.classList.toggle("abierto");
        btn.setAttribute("aria-expanded", String(abierto));
    });

    // Cerrar al hacer clic fuera
    document.addEventListener("click", function (e) {
        if (!dropdown.contains(e.target)) cerrarMenu();
    });

    // Cerrar con la tecla Escape
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") cerrarMenu();
    });
})();

(function () {
    const app = document.querySelector('.app');
    const btn = document.getElementById('menuToggle');
    if (!app || !btn) return;   // en las páginas sin botón no hace nada

    // Recordar el estado entre páginas
    try {
        if (localStorage.getItem('menuOculto') === '1') {
            app.classList.add('menu-oculto');
            btn.setAttribute('aria-expanded', 'false');
        }
    } catch (e) {}

    btn.addEventListener('click', () => {
        const oculto = app.classList.toggle('menu-oculto');
        btn.setAttribute('aria-expanded', String(!oculto));
        try { localStorage.setItem('menuOculto', oculto ? '1' : '0'); } catch (e) {}
    });
})();