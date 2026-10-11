// =====================================================================
// MENÚ POR ROL Y DATOS DEL USUARIO EN LA BARRA SUPERIOR
// Requiere js/api.js cargado ANTES (usa obtenerUsuarioSesion).
// Se puede incluir como <script src="js/menu-rol.js"></script> en cada página con menú,
// o pegar su contenido al final de dashboard.js.
// =====================================================================
(function () {
    const ETIQUETAS_ROL = { DUENO: "Dueño", ADMIN: "Administrador", VENDEDOR: "Vendedor" };

    // Qué roles ven cada opción del menú.
    // OJO: solo oculta cosas en la interfaz. La seguridad real la pone el backend (401/403).
    const MENU_POR_ROL = {
        "dashboard.html": ["DUENO", "ADMIN", "VENDEDOR"],
        "bodegas.html": ["DUENO", "ADMIN"],
        "calidades.html": ["DUENO"],           // HU-03: el dueño configura las calidades
        "catalogo.html": ["DUENO", "ADMIN"],
        "usuarios.html": ["DUENO"],          // el backend solo deja listar/crear usuarios al dueño
        "modelos.html": ["DUENO", "ADMIN", "VENDEDOR"],
    };

    document.addEventListener("DOMContentLoaded", function () {
        if (typeof obtenerUsuarioSesion !== "function") return; // api.js no está cargado

        const usuario = obtenerUsuarioSesion();
        if (!usuario) return;

        // Oculta del menú lo que el rol no puede usar
        document.querySelectorAll(".menu-item").forEach(function (enlace) {
            const permitidos = MENU_POR_ROL[enlace.getAttribute("href")];
            if (permitidos && !permitidos.includes(usuario.rol)) {
                enlace.style.display = "none";
            }
        });

        // Muestra quién tiene la sesión abierta
        const etiqueta = ETIQUETAS_ROL[usuario.rol] || usuario.rol || "";
        document.querySelectorAll(".usuario-info strong, .dropdown-perfil strong")
            .forEach(function (el) { el.textContent = usuario.nombre || usuario.username; });
        document.querySelectorAll(".rol")
            .forEach(function (el) { el.textContent = etiqueta; });
        document.querySelectorAll(".dropdown-perfil span")
            .forEach(function (el) { el.textContent = usuario.username || ""; });
    });
})();