const logoutButton = document.getElementById("logoutButton");

logoutButton.addEventListener("click", function () { //este dice que cuando el usuario haga click se ejecute lo que esta andentro 
        window.location.href = "login.html"; //este es para devolver de pagina al login
});

 const btn = document.getElementById('usuarioBtn');
    const dropdown = document.getElementById('usuarioDropdown');

    // Abrir / cerrar al hacer clic en el usuario
    btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const abierto = dropdown.classList.toggle('abierto');
        btn.setAttribute('aria-expanded', abierto);
    });

    // Cerrar al hacer clic fuera
    document.addEventListener('click', (e) => {
        if (!dropdown.contains(e.target)) {
            dropdown.classList.remove('abierto');
            btn.setAttribute('aria-expanded', 'false');
        }
    });

    // Cerrar con la tecla Escape
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            dropdown.classList.remove('abierto');
            btn.setAttribute('aria-expanded', 'false');
        }
});