package com.zapgest.backend.security;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.zapgest.backend.config.SeguridadConfig;
import com.zapgest.backend.controllers.ExistenciasController;
import com.zapgest.backend.controllers.TrasladoController;
import com.zapgest.backend.dto.inventario.ExistenciaResponse;
import com.zapgest.backend.dto.traslado.TrasladoRequest;
import com.zapgest.backend.dto.traslado.TrasladoResponse;
import com.zapgest.backend.exceptions.CatalogoErrores;
import com.zapgest.backend.exceptions.EstadoNoPermitidoException;
import com.zapgest.backend.exceptions.ManejadorErrores;
import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.exceptions.TrasladoErrores;
import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.repositories.UsuarioRepository;
import com.zapgest.backend.services.InventarioService;
import com.zapgest.backend.services.TrasladoService;

import jakarta.servlet.Filter;

// Traslados: seguridad real (JWT y roles) y formato de las respuestas. Servicios simulados.
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@org.springframework.test.context.ContextConfiguration(classes = TrasladoApiTest.Contexto.class)
@TestPropertySource(properties = {
        "app.jwt.secret=" + PermisosApiTest.CLAVE_PRUEBA,
        "app.cors.allowed-origins=http://localhost:5500"
})
class TrasladoApiTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ SeguridadConfig.class, JwtService.class, JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, ManejadorErrores.class,
            CatalogoErrores.class, TrasladoErrores.class, TrasladoController.class, ExistenciasController.class })
    static class Contexto {
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
    private TrasladoService trasladoService;
    @MockitoBean
    private InventarioService inventarioService;

    private MockMvc mvc;

    private static final String CUERPO = "{\"origenId\":8,\"destinoId\":9,\"varianteId\":101,\"cantidad\":5}";

    private static TrasladoResponse respuesta(String estado) {
        return new TrasladoResponse(1L, 8L, "Bodega 8", 9L, "Local 9", 101L, "AF1-001", "Air Force", "Blanco",
                "38", "Original", 5, estado, "Juan", Instant.parse("2026-11-20T15:00:00Z"), null, null, null);
    }

    @BeforeEach
    void preparar() {
        reset(usuarioRepository, trasladoService, inventarioService);
        mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(cadenaSeguridad).build();

        when(trasladoService.listar()).thenReturn(List.of(respuesta("SOLICITADO")));
        when(trasladoService.solicitar(any(), anyString())).thenReturn(respuesta("SOLICITADO"));
        when(trasladoService.despachar(anyLong(), anyString())).thenReturn(respuesta("EN_MOVIMIENTO"));
        when(trasladoService.recibir(anyLong(), anyString())).thenReturn(respuesta("LLEGO"));
        when(trasladoService.cancelar(anyLong(), anyString())).thenReturn(respuesta("CANCELADO"));
        when(inventarioService.existenciasEn(8L)).thenReturn(
                List.of(new ExistenciaResponse(101L, "AF1-001", "Air Force", "Blanco", "38", "Original", 24)));
    }

    private String bearer(String username, Rol rol) {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNegocioId(1L);
        u.setNombre(username);
        u.setUsername(username);
        u.setRol(rol);
        u.setActivo(true);
        when(usuarioRepository.findByUsername(username)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.emitir(u);
    }

    @Test
    void sinTokenDa401() throws Exception {
        mvc.perform(get("/api/traslados")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inventario/existencias").param("ubicacionId", "8")).andExpect(status().isUnauthorized());
    }

    @Test
    void elVendedorNoPuedeVerNiHacerTraslados() throws Exception {
        String token = bearer("vend", Rol.VENDEDOR);
        mvc.perform(get("/api/traslados").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/traslados").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/traslados/1/despachar").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/inventario/existencias").param("ubicacionId", "8").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void duenoYAdminPuedenListar() throws Exception {
        for (Rol rol : List.of(Rol.DUENO, Rol.ADMIN)) {
            mvc.perform(get("/api/traslados").header("Authorization", bearer("u_" + rol, rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].origen").value("Bodega 8"))
                    .andExpect(jsonPath("$[0].estado").value("SOLICITADO"))
                    .andExpect(jsonPath("$[0].solicitadoEn").value("2026-11-20T15:00:00Z"));
        }
    }

    @Test
    void solicitarResponde201YPasaQuienLoHace() throws Exception {
        mvc.perform(post("/api/traslados").header("Authorization", bearer("admin1", Rol.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("SOLICITADO"));

        verify(trasladoService).solicitar(eq(new TrasladoRequest(8L, 9L, 101L, 5)), eq("admin1"));
    }

    @Test
    void lasTresAccionesCambianElEstado() throws Exception {
        String token = bearer("dueno", Rol.DUENO);
        mvc.perform(patch("/api/traslados/1/despachar").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_MOVIMIENTO"));
        mvc.perform(patch("/api/traslados/1/recibir").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("LLEGO"));
        mvc.perform(patch("/api/traslados/1/cancelar").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
    }

    @Test
    void unEstadoNoPermitidoDa409ConMensaje() throws Exception {
        when(trasladoService.despachar(eq(1L), anyString()))
                .thenThrow(new EstadoNoPermitidoException("Solo se puede despachar un traslado Solicitado."));

        mvc.perform(patch("/api/traslados/1/despachar").header("Authorization", bearer("dueno", Rol.DUENO)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Solo se puede despachar un traslado Solicitado."));
    }

    @Test
    void unaReglaDeNegocioDa400YUnTrasladoInexistente404() throws Exception {
        String token = bearer("dueno", Rol.DUENO);
        when(trasladoService.solicitar(any(), anyString()))
                .thenThrow(new IllegalArgumentException("El origen y el destino deben ser distintos."));
        when(trasladoService.cancelar(eq(77L), anyString()))
                .thenThrow(new RecursoNoEncontradoException("El traslado no existe."));

        mvc.perform(post("/api/traslados").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El origen y el destino deben ser distintos."));
        mvc.perform(patch("/api/traslados/77/cancelar").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El traslado no existe."));
    }

    @Test
    void lasExistenciasNoTraenCostosNiPrecios() throws Exception {
        mvc.perform(get("/api/inventario/existencias").param("ubicacionId", "8")
                .header("Authorization", bearer("admin1", Rol.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].varianteId").value(101))
                .andExpect(jsonPath("$[0].cantidad").value(24))
                .andExpect(jsonPath("$[0]", not(hasKey("costoAdquisicion"))))
                .andExpect(jsonPath("$[0]", not(hasKey("precioMinimo"))))
                .andExpect(jsonPath("$[0]", not(hasKey("precioSugerido"))));
    }
}
