const loginForm = document.getElementById("loginForm");
//busca el formulario que tiene el id loginForm en el html y lo guarda en la variable loginForm

loginForm.addEventListener("submit", function(event) {
    //cuando alguien intente enviar el formulario ejecute esta funcion el evento que se manda es submit que es enviar 

    event.preventDefault(); // se le dice que no recargue la pagina cuando se envie el formulario ya que se nesecitan hacer validaciones antes de enviarlo

    const username = document.getElementById("username").value; //se obtiene lo que escribio el usuario
    const password = document.getElementById("password").value;//se obtiene la contraseña que puso el usuario

    const loginMessage = document.getElementById("loginMessage"); //manda un mensaje

    if (username === "" || password === "") {
        loginMessage.textContent = "Por favor, completa todos los campos.";
        return; //se verifica que los campos no esten vacios si lo estan manda un mensaje y no hace nada mas
    }

    loginMessage.textContent = "Datos recibidos correctamente."; //si estan bien 

});