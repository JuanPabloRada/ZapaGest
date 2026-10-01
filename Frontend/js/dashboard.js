const logoutButton = document.getElementById("logoutButton");

logoutButton.addEventListener("click", function () { //este dice que cuando el usuario haga click se ejecute lo que esta andentro 
    const confirmar = confirm("¿Quieres cerrar sesión?");

    if (confirmar) {
        window.location.href = "login.html"; //este es para devolver de pagina al login
    }

});