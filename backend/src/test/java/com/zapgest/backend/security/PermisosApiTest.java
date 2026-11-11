package com.zapgest.backend.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import jakarta.servlet.Filter;

import com.zapgest.backend.config.SeguridadConfig;
import com.zapgest.backend.controllers.AuthController;
import com.zapgest.backend.controllers.CalidadController;
import com.zapgest.backend.controllers.UbicacionController;
import com.zapgest.backend.controllers.UsuarioController;
import com.zapgest.backend.exceptions.CredencialesInvalidasException;
import com.zapgest.backend.exceptions.ManejadorErrores;
import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.controllers.IngresoMercanciaController;
import com.zapgest.backend.dto.UsuarioResponse;
import com.zapgest.backend.dto.inventario.IngresoResponse;
import com.zapgest.backend.services.IngresoMercanciaService;
import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.repositories.UsuarioRepository;
import com.zapgest.backend.services.CalidadService;
import com.zapgest.backend.services.UbicacionService;
import com.zapgest.backend.services.UsuarioService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

// Prueba la cadena de seguridad REAL (SeguridadConfig + JwtAuthenticationFilter + handlers 401/403)
// con los controladores reales. No necesita PostgreSQL: repositorio y servicios son simulados.
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@org.springframework.test.context.ContextConfiguration(classes = PermisosApiTest.Contexto.class)
@TestPropertySource(properties = {
        "app.jwt.secret=" + PermisosApiTest.CLAVE_PRUEBA,
        "app.cors.allowed-origins=http://localhost:5500"
})
class PermisosApiTest {

    // Clave SOLO para pruebas (32 bytes en Base64). No es la clave real de ningun entorno.
    static final String CLAVE_PRUEBA = "dGVzdC1rZXktc29sby1wYXJhLXBydWViYXMtMzItYnl0ZXM=";

    private static final String MSG_403 = "No tiene permisos para realizar esta acción";
    private static final String MSG_401 = "Sesión no válida o expirada";
    private static final String MSG_LIMITE = "Ha alcanzado el límite máximo permitido de 10 ubicaciones";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ SeguridadConfig.class, JwtService.class, JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, ManejadorErrores.class,
            AuthController.class, UsuarioController.class, IngresoMercanciaController.class, CalidadController.class, UbicacionController.class,
            PermisosApiTest.CatalogoFalso.class })
    static class Contexto {
    }

    // Controlador de mentira en /api/catalogo: el catalogo real lo hace Joseph.
    // Solo sirve para comprobar la regla de seguridad (GET con login, POST solo DUENO y ADMIN).
    @RestController
    @RequestMapping("/api/catalogo")
    static class CatalogoFalso {
        @GetMapping
        public List<String> listar() {
            return List.of();
        }

        @PostMapping
        public String crear() {
            return "ok";
        }

        @PutMapping("/{id}")
        public String editar() {
            return "ok";
        }

        @GetMapping("/{id}/opciones")
        public String opciones() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private JwtService jwtService;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("springSecurityFilterChain")
    private Filter cadenaSeguridad;

    @MockitoBean
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private CalidadService calidadService;
    @MockitoBean
    private UbicacionService ubicacionService;
    @MockitoBean
    private IngresoMercanciaService ingresoMercanciaService;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(usuarioRepository, usuarioService, calidadService, ubicacionService, ingresoMercanciaService);
        mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(cadenaSeguridad).build();

        when(usuarioService.listar()).thenReturn(List.of());
        when(calidadService.listar()).thenReturn(List.of());
        when(calidadService.crear(any())).thenReturn(new Calidad());
        when(ubicacionService.listar()).thenReturn(List.of());
        when(ubicacionService.crear(any())).thenReturn(new Ubicacion());
        when(ubicacionService.cambiarEstado(anyLong(), any())).thenReturn(new Ubicacion());
        when(calidadService.editar(anyLong(), any())).thenReturn(new Calidad());
        when(ubicacionService.editar(anyLong(), any())).thenReturn(new Ubicacion());
        when(usuarioService.cambiarEstado(anyLong(), any(), any()))
                .thenReturn(new UsuarioResponse(5L, "Sandra", "sandra", Rol.VENDEDOR, false));
        when(ingresoMercanciaService.ingresar(any(), any())).thenReturn(new IngresoResponse(50L, 12));
    }

    // ---------- helpers ----------

    private Usuario usuario(String username, Rol rol, boolean activo) {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNegocioId(1L);
        u.setNombre("Nombre " + username);
        u.setUsername(username);
        u.setRol(rol);
        u.setActivo(activo);
        when(usuarioRepository.findByUsername(username)).thenReturn(Optional.of(u));
        return u;
    }

    private String bearer(String username, Rol rol) {
        return "Bearer " + jwtService.emitir(usuario(username, rol, true));
    }

    private static final String JSON_USUARIO =
            "{\"nombre\":\"Ana\",\"username\":\"ana\",\"password\":\"secreto1\",\"rol\":\"VENDEDOR\"}";

    // ---------- 401: sin token / token malo ----------

    @Test
    void sinTokenUnaRutaProtegidaDevuelve401ConMensaje() throws Exception {
        mvc.perform(get("/api/ubicaciones"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasKey("mensaje")))
                .andExpect(jsonPath("$.mensaje").value(MSG_401));
    }

    @Test
    void sinTokenTodasLasRutasProtegidasDan401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_USUARIO))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/calidades")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/calidades").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"A\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ubicaciones").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"A\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/ubicaciones/1").contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutaInexistenteSinTokenTambienDa401() throws Exception {
        mvc.perform(get("/api/no-existe")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenBasuraDevuelve401ConMensaje() throws Exception {
        mvc.perform(get("/api/ubicaciones").header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(MSG_401));
    }

    @Test
    void tokenFirmadoConOtraClaveDevuelve401() throws Exception {
        usuario("dueno", Rol.DUENO, true);
        SecretKey otraClave = Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                Base64.getEncoder().encodeToString("otra-clave-distinta-de-32-bytes!!".getBytes())));
        String falso = Jwts.builder().subject("dueno").signWith(otraClave).compact();

        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + falso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoDevuelve401() throws Exception {
        usuario("dueno", Rol.DUENO, true);
        SecretKey clave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(CLAVE_PRUEBA));
        Instant hace2Horas = Instant.now().minus(2, ChronoUnit.HOURS);
        String vencido = Jwts.builder()
                .subject("dueno")
                .issuedAt(Date.from(hace2Horas.minus(1, ChronoUnit.HOURS)))
                .expiration(Date.from(hace2Horas))
                .signWith(clave)
                .compact();

        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + vencido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(MSG_401));
    }

    @Test
    void tokenDeUsuarioDesactivadoDevuelve401() throws Exception {
        Usuario inactivo = usuario("exempleado", Rol.ADMIN, false);
        String token = jwtService.emitir(inactivo);

        mvc.perform(get("/api/ubicaciones").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeUsuarioQueYaNoExisteDevuelve401() throws Exception {
        Usuario u = usuario("fantasma", Rol.ADMIN, true);
        String token = jwtService.emitir(u);
        when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        mvc.perform(get("/api/ubicaciones").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 403: rol no autorizado ----------

    @Test
    void vendedorNoPuedeGestionarUsuariosNiCalidadesNiUbicaciones() throws Exception {
        String t = bearer("vendedor1", Rol.VENDEDOR);

        mvc.perform(get("/api/usuarios").header("Authorization", t))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mensaje").value(MSG_403));
        mvc.perform(post("/api/usuarios").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_USUARIO))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(MSG_403));
        mvc.perform(post("/api/calidades").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"A\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(MSG_403));
        mvc.perform(post("/api/ubicaciones").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"A\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(MSG_403));
        mvc.perform(patch("/api/ubicaciones/1").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(MSG_403));
    }

    @Test
    void vendedorSiPuedeConsultarCalidadesYUbicaciones() throws Exception {
        String t = bearer("vendedor1", Rol.VENDEDOR);
        mvc.perform(get("/api/calidades").header("Authorization", t)).andExpect(status().isOk());
        mvc.perform(get("/api/ubicaciones").header("Authorization", t)).andExpect(status().isOk());
    }

    @Test
    void adminNoPuedeGestionarUsuariosNiCrearCalidades() throws Exception {
        String t = bearer("admin1", Rol.ADMIN);

        mvc.perform(get("/api/usuarios").header("Authorization", t))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(MSG_403));
        mvc.perform(post("/api/usuarios").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_USUARIO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/calidades").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"A\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(MSG_403));
    }

    // ---------- 200: roles autorizados ----------

    @Test
    void duenoPuedeGestionarUsuariosYCrearCalidades() throws Exception {
        String t = bearer("dueno", Rol.DUENO);

        mvc.perform(get("/api/usuarios").header("Authorization", t)).andExpect(status().isOk());
        mvc.perform(post("/api/usuarios").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_USUARIO))
                .andExpect(status().isOk());
        mvc.perform(post("/api/calidades").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Primera\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void duenoYAdminPuedenGestionarUbicaciones() throws Exception {
        for (String[] par : new String[][] { { "dueno", "DUENO" }, { "admin1", "ADMIN" } }) {
            String t = bearer(par[0], Rol.valueOf(par[1]));
            mvc.perform(get("/api/ubicaciones").header("Authorization", t)).andExpect(status().isOk());
            mvc.perform(post("/api/ubicaciones").header("Authorization", t)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Bodega\"}"))
                    .andExpect(status().isOk());
            mvc.perform(patch("/api/ubicaciones/1").header("Authorization", t)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}"))
                    .andExpect(status().isOk());
        }
    }

    // ---------- login ----------

    @Test
    void loginDevuelveTokenYUsuarioConLosCamposAcordadosSinPedirToken() throws Exception {
        Usuario u = usuario("dueno", Rol.DUENO, true);
        when(usuarioService.autenticar(any())).thenReturn(u);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dueno\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").value(1))
                .andExpect(jsonPath("$.usuario.nombre").value("Nombre dueno"))
                .andExpect(jsonPath("$.usuario.username").value("dueno"))
                .andExpect(jsonPath("$.usuario.rol").value("DUENO"))
                // nunca debe filtrarse el hash de la contrasena
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("password"))));
    }

    @Test
    void elTokenDelLoginFuncionaComoBearerEnUnaRutaProtegida() throws Exception {
        Usuario u = usuario("admin1", Rol.ADMIN, true);
        when(usuarioService.autenticar(any())).thenReturn(u);

        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin1\",\"password\":\"x\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = cuerpo.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/ubicaciones").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void loginConCredencialesMalasDevuelve401ConMensaje() throws Exception {
        when(usuarioService.autenticar(any())).thenThrow(new CredencialesInvalidasException());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dueno\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"));
    }

    // ---------- errores con forma {mensaje} ----------

    @Test
    void limiteDeDiezUbicacionesLlegaComo400ConElMensajeAcordado() throws Exception {
        String t = bearer("admin1", Rol.ADMIN);
        when(ubicacionService.crear(any())).thenThrow(new IllegalStateException(MSG_LIMITE));
        when(ubicacionService.cambiarEstado(eq(4L), eq(true))).thenThrow(new IllegalStateException(MSG_LIMITE));

        mvc.perform(post("/api/ubicaciones").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Bodega 11\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MSG_LIMITE));
        mvc.perform(patch("/api/ubicaciones/4").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MSG_LIMITE));
    }

    @Test
    void idNoNumericoEnPatchDevuelve400ConMensaje() throws Exception {
        String t = bearer("admin1", Rol.ADMIN);
        mvc.perform(patch("/api/ubicaciones/abc").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", hasKey("mensaje")));
    }

    // ---------- CORS (el frontend corre en otro origen) ----------

    @Test
    void preflightDesdeElFrontendPermiteAuthorizationYPatch() throws Exception {
        mvc.perform(options("/api/ubicaciones/1")
                .header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
    }

    @Test
    void preflightDesdeUnOrigenNoPermitidoSeRechaza() throws Exception {
        mvc.perform(options("/api/ubicaciones/1")
                .header("Origin", "http://sitio-malo.example")
                .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
    }

    // ---------- catalogo (regla de seguridad; el controlador real es de Joseph) ----------

    @Test
    void catalogoSinTokenDa401() throws Exception {
        mvc.perform(get("/api/catalogo")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/catalogo")).andExpect(status().isUnauthorized());
    }

    @Test
    void catalogoGetLoPuedeVerCualquierUsuarioConLogin() throws Exception {
        mvc.perform(get("/api/catalogo").header("Authorization", bearer("vendedor1", Rol.VENDEDOR)))
                .andExpect(status().isOk());
    }

    @Test
    void catalogoPostVendedorDa403ConMensaje() throws Exception {
        mvc.perform(post("/api/catalogo").header("Authorization", bearer("vendedor1", Rol.VENDEDOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(MSG_403));
    }

    @Test
    void catalogoPostDuenoYAdminPasan() throws Exception {
        mvc.perform(post("/api/catalogo").header("Authorization", bearer("dueno", Rol.DUENO)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/catalogo").header("Authorization", bearer("admin1", Rol.ADMIN)))
                .andExpect(status().isOk());
    }

    // ---------- editar (PUT) ----------

    private static final String CUERPO_NOMBRE = "{\"nombre\":\"Nuevo nombre\"}";

    private int putEstado(String ruta, String token) throws Exception {
        return mvc.perform(put(ruta).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_NOMBRE))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void putSinTokenDa401() throws Exception {
        for (String ruta : List.of("/api/calidades/1", "/api/ubicaciones/1", "/api/catalogo/1")) {
            mvc.perform(put(ruta).contentType(MediaType.APPLICATION_JSON).content(CUERPO_NOMBRE))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void editarCalidadesEsSoloDelDueno() throws Exception {
        org.assertj.core.api.Assertions.assertThat(putEstado("/api/calidades/1", bearer("vendedor1", Rol.VENDEDOR))).isEqualTo(403);
        org.assertj.core.api.Assertions.assertThat(putEstado("/api/calidades/1", bearer("admin1", Rol.ADMIN))).isEqualTo(403);
        org.assertj.core.api.Assertions.assertThat(putEstado("/api/calidades/1", bearer("dueno", Rol.DUENO))).isEqualTo(200);
    }

    @Test
    void editarUbicacionesYCatalogoEsDeDuenoYAdmin() throws Exception {
        for (String ruta : List.of("/api/ubicaciones/1", "/api/catalogo/1")) {
            org.assertj.core.api.Assertions.assertThat(putEstado(ruta, bearer("vendedor1", Rol.VENDEDOR))).isEqualTo(403);
            org.assertj.core.api.Assertions.assertThat(putEstado(ruta, bearer("admin1", Rol.ADMIN))).isEqualTo(200);
            org.assertj.core.api.Assertions.assertThat(putEstado(ruta, bearer("dueno", Rol.DUENO))).isEqualTo(200);
        }
    }

    @Test
    void preflightDesdeElFrontendPermitePut() throws Exception {
        mvc.perform(options("/api/catalogo/1")
                .header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
    }

    @Test
    void laRutaErrorNoPideToken() throws Exception {
        // Si /error pidiera token, un error real llegaria como 401 y el frontend cerraria la sesion
        int estado = mvc.perform(get("/error")).andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(estado).isNotEqualTo(401);
    }

    // ---------- UTF-8 en los mensajes 401 y 403 ----------

    @Test
    void elMensaje401SaleEnUtf8ConSusTildes() throws Exception {
        byte[] cuerpo = mvc.perform(get("/api/ubicaciones"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("UTF-8")))
                .andReturn().getResponse().getContentAsByteArray();

        org.junit.jupiter.api.Assertions.assertEquals(
                "{\"mensaje\":\"" + MSG_401 + "\"}",
                new String(cuerpo, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void elMensaje403SaleEnUtf8ConSusTildes() throws Exception {
        byte[] cuerpo = mvc.perform(get("/api/usuarios").header("Authorization", bearer("vendedor1", Rol.VENDEDOR)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", containsString("UTF-8")))
                .andReturn().getResponse().getContentAsByteArray();

        org.junit.jupiter.api.Assertions.assertEquals(
                "{\"mensaje\":\"" + MSG_403 + "\"}",
                new String(cuerpo, java.nio.charset.StandardCharsets.UTF_8));
    }

    // ---------- Sprint 2: activar/desactivar usuarios y opciones por referencia ----------

    private int patchUsuario(String token) throws Exception {
        return mvc.perform(patch("/api/usuarios/5").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void activarODesactivarUsuariosEsSoloDelDueno() throws Exception {
        org.assertj.core.api.Assertions.assertThat(mvc.perform(patch("/api/usuarios/5")
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        org.assertj.core.api.Assertions.assertThat(patchUsuario(bearer("vendedor1", Rol.VENDEDOR))).isEqualTo(403);
        org.assertj.core.api.Assertions.assertThat(patchUsuario(bearer("admin1", Rol.ADMIN))).isEqualTo(403);
        org.assertj.core.api.Assertions.assertThat(patchUsuario(bearer("dueno", Rol.DUENO))).isEqualTo(200);
    }

    @Test
    void opcionesDeReferenciaSonDeDuenoYAdmin() throws Exception {
        String ruta = "/api/catalogo/1/opciones";
        org.assertj.core.api.Assertions.assertThat(mvc.perform(get(ruta)).andReturn().getResponse().getStatus()).isEqualTo(401);
        org.assertj.core.api.Assertions.assertThat(mvc.perform(get(ruta).header("Authorization", bearer("vendedor1", Rol.VENDEDOR)))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        org.assertj.core.api.Assertions.assertThat(mvc.perform(get(ruta).header("Authorization", bearer("admin1", Rol.ADMIN)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(mvc.perform(get(ruta).header("Authorization", bearer("dueno", Rol.DUENO)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        // El listado general del catalogo lo sigue viendo cualquiera con sesion
        org.assertj.core.api.Assertions.assertThat(mvc.perform(get("/api/catalogo").header("Authorization", bearer("vendedor1", Rol.VENDEDOR)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    // ---------- HU-07: ingreso de mercancia ----------

    private static final String CUERPO_INGRESO =
            "{\"ubicacionId\":2,\"referenciaId\":1,\"colorId\":10,\"tallaId\":20,\"calidadId\":30,\"cantidad\":12}";

    private int postIngreso(String token) throws Exception {
        var peticion = post("/api/inventario/ingresos").contentType(MediaType.APPLICATION_JSON).content(CUERPO_INGRESO);
        if (token != null) {
            peticion = peticion.header("Authorization", token);
        }
        return mvc.perform(peticion).andReturn().getResponse().getStatus();
    }

    @Test
    void ingresarMercanciaEsDeDuenoYAdmin() throws Exception {
        org.assertj.core.api.Assertions.assertThat(postIngreso(null)).isEqualTo(401);
        org.assertj.core.api.Assertions.assertThat(postIngreso(bearer("vendedor1", Rol.VENDEDOR))).isEqualTo(403);
        org.assertj.core.api.Assertions.assertThat(postIngreso(bearer("admin1", Rol.ADMIN))).isEqualTo(201);
        org.assertj.core.api.Assertions.assertThat(postIngreso(bearer("dueno", Rol.DUENO))).isEqualTo(201);
    }

    @Test
    void elIngresoRespondeVarianteYStockConQuienLoHizo() throws Exception {
        mvc.perform(post("/api/inventario/ingresos").header("Authorization", bearer("admin1", Rol.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_INGRESO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.varianteId").value(50))
                .andExpect(jsonPath("$.stockEnUbicacion").value(12));
        org.mockito.Mockito.verify(ingresoMercanciaService).ingresar(any(), org.mockito.ArgumentMatchers.eq("admin1"));
    }
}
