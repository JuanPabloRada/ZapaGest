// ===== PERMISOS =====
const tienePermiso = requerirRol("DUENO", "ADMIN");
if (!tienePermiso) document.querySelector(".dashboard").hidden = true;

// ===== ELEMENTOS DEL HTML =====
const tbodyModelos = document.querySelector("#tablaModelos tbody");
const textoVacio = document.getElementById("vacio");
const contador = document.getElementById("contador");
const mensaje = document.getElementById("mensaje");

// Barra de filtros
const filtroTexto = document.getElementById("filtroTexto");
const filtroMarca = document.getElementById("filtroMarca");
const btnBuscar = document.getElementById("btnBuscar");
const btnLimpiar = document.getElementById("btnLimpiar");

let modelos = [];
let calidades = [];

// ===== UTILIDADES =====
function mostrarMensaje(texto, tipo) {
    mensaje.textContent = texto;
    mensaje.className = "mensaje " + (tipo || "");
}

function escapar(texto) {
    const div = document.createElement("div");
    div.textContent = texto ?? "";
    return div.innerHTML.replace(/"/g, "&quot;");
}

function dinero(numero) {
    return "$" + Number(numero).toLocaleString("es-CO");
}

// ===== FILTRO DE MARCAS =====
function llenarMarcas() {
    if (!filtroMarca) return;
    const seleccionada = filtroMarca.value;
    const marcas = [...new Set(modelos.map(m => m.marca).filter(Boolean))].sort();

    filtroMarca.innerHTML =
        '<option value="">Todas las marcas</option>' +
        marcas.map(m => `<option value="${escapar(m)}">${escapar(m)}</option>`).join("");

    filtroMarca.value = marcas.includes(seleccionada) ? seleccionada : "";
}

// ===== LISTA =====
function dibujarModelos() {
    const texto = (filtroTexto?.value || "").trim().toLowerCase();
    const marca = filtroMarca?.value || "";

    const visibles = modelos.filter(m => {
        const coincideTexto = !texto ||
            [m.referencia, m.nombre, m.marca].some(t => (t ?? "").toLowerCase().includes(texto));
        const coincideMarca = !marca || m.marca === marca;
        return coincideTexto && coincideMarca;
    });

    tbodyModelos.innerHTML = visibles
        .map(m => {
            const precios = m.precios
                .map(p => {
                    const cal = calidades.find(c => c.id === p.calidadId);
                    return `<span class="precio-linea">${escapar(p.calidad ?? (cal ? cal.nombre : "?"))}: ` +
                        `costo ${dinero(p.costo)} · mín. ${dinero(p.precioMinimo)} · sugerido ${dinero(p.precioSugerido)}</span>`;
                })
                .join("");

            return `<tr>
                <td>${escapar(m.referencia)}</td>
                <td>${escapar(m.nombre)}</td>
                <td>${escapar(m.marca)}</td>
                <td>${m.colores.length} × ${m.tallas.length} × ${m.precios.length} = ${m.colores.length * m.tallas.length * m.precios.length}</td>
                <td>${precios}</td>
                <td><a class="btn-editar" href="catalogo.html?id=${encodeURIComponent(m.id)}">
                    <i class="fa-solid fa-pen"></i> Editar</a></td>
            </tr>`;
        })
        .join("");

    // Vacío solo si no hay modelos en total; contador muestra los visibles
    textoVacio.hidden = modelos.length > 0;
    contador.textContent = visibles.length;
}

async function cargarModelos() {
    [modelos, calidades] = await Promise.all([
        apiFetch("/catalogo"),
        apiFetch("/calidades"),
    ]);
    llenarMarcas();
    dibujarModelos();
}

// ===== EVENTOS =====
filtroTexto?.addEventListener("input", dibujarModelos);
filtroMarca?.addEventListener("change", dibujarModelos);
btnBuscar?.addEventListener("click", dibujarModelos);
btnLimpiar?.addEventListener("click", () => {
    if (filtroTexto) filtroTexto.value = "";
    if (filtroMarca) filtroMarca.value = "";
    dibujarModelos();
});

document.addEventListener("DOMContentLoaded", async () => {
    if (!tienePermiso) return;
    try {
        await cargarModelos();
    } catch (e) {
        mostrarMensaje(e.message, "error");
    }
});