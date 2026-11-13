package com.zapgest.backend.security;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
import com.zapgest.backend.controllers.ConsultaInventarioController;
import com.zapgest.backend.dto.inventario.ResultadoBusquedaInventario;
import com.zapgest.backend.dto.inventario.UbicacionCantidadResponse;
import com.zapgest.backend.dto.inventario.VarianteDisponibleResponse;
import com.zapgest.backend.exceptions.ManejadorErrores;
import com.zapgest.backend.models.Rol;
import com.zapgest.backend.models.Usuario;
import com.zapgest.backend.repositories.UsuarioRepository;
import com.zapgest.backend.services.ConsultaInventarioService;

import jakarta.servlet.Filter;

// GET /api/inventario/buscar con la cadena de seguridad REAL: los tres roles pueden consultar,
// sin token da 401, el JSON no trae costos y el navegador puede leer X-Total-Count (CORS).
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@org.springframework.test.context.ContextConfiguration(classes = ConsultaInventarioApiTest.Contexto.class)
@TestPropertySource(properties = {
        "app.jwt.secret=" + PermisosApiTest.CLAVE_PRUEBA,
        "app.cors.allowed-origins=http://localhost:5500"
})
class ConsultaInventarioApiTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ SeguridadConfig.class, JwtService.class, JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, ManejadorErrores.class,
            ConsultaInventarioController.class })
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
    private ConsultaInventarioService service;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(usuarioRepository, service);
        mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(cadenaSeguridad).build();

        VarianteDisponibleResponse v = new VarianteDisponibleResponse(100L, "AF1-001", "Air Force", "Blanco", "40",
                "Original", new BigDecimal("180000"), 5,
                List.of(new UbicacionCantidadResponse(5L, "Bodega", 3), new UbicacionCantidadResponse(6L, "Local", 2)));
        when(service.buscar(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new ResultadoBusquedaInventario(List.of(v), 137));
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
        mvc.perform(get("/api/inventario/buscar")).andExpect(status().isUnauthorized());
    }

    @Test
    void losTresRolesPuedenConsultar() throws Exception {
        for (Rol rol : Rol.values()) {
            mvc.perform(get("/api/inventario/buscar").header("Authorization", bearer("u_" + rol, rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].referencia").value("AF1-001"))
                    .andExpect(jsonPath("$[0].total").value(5))
                    .andExpect(jsonPath("$[0].ubicaciones[1].ubicacion").value("Local"));
        }
    }

    @Test
    void elJsonNoTraeCostoNiPrecioMinimo() throws Exception {
        mvc.perform(get("/api/inventario/buscar").header("Authorization", bearer("vend", Rol.VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]", not(hasKey("costoAdquisicion"))))
                .andExpect(jsonPath("$[0]", not(hasKey("costo"))))
                .andExpect(jsonPath("$[0]", not(hasKey("precioMinimo"))))
                .andExpect(jsonPath("$[0]", hasKey("precioSugerido")));
    }

    @Test
    void elTotalDeResultadosVaEnElHeaderYSeLeeDesdeElNavegador() throws Exception {
        mvc.perform(get("/api/inventario/buscar")
                .header("Origin", "http://localhost:5500")
                .header("Authorization", bearer("vend", Rol.VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "137"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Total-Count"));
    }

    @Test
    void losParametrosLlegaranAlServicio() throws Exception {
        mvc.perform(get("/api/inventario/buscar")
                .param("q", "air").param("color", "10").param("talla", "20").param("calidad", "30")
                .param("incluirAgotados", "true").param("pagina", "1").param("tamano", "20")
                .header("Authorization", bearer("dueno", Rol.DUENO)))
                .andExpect(status().isOk());

        verify(service).buscar(eq("air"), eq(10L), eq(20L), eq(30L), eq(true), eq(1), eq(20));
    }

    @Test
    void sinParametrosUsaLosValoresPorDefecto() throws Exception {
        mvc.perform(get("/api/inventario/buscar").header("Authorization", bearer("dueno", Rol.DUENO)))
                .andExpect(status().isOk());

        verify(service).buscar(isNull(), isNull(), isNull(), isNull(), eq(false), eq(0), eq(50));
    }

    @Test
    void unParametroConFormatoInvalidoDa400ConMensaje() throws Exception {
        mvc.perform(get("/api/inventario/buscar").param("color", "abc")
                .header("Authorization", bearer("dueno", Rol.DUENO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void unaReglaDeNegocioQueFallaDa400ConMensaje() throws Exception {
        when(service.buscar(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("La página no puede ser negativa."));

        mvc.perform(get("/api/inventario/buscar").param("pagina", "-1")
                .header("Authorization", bearer("dueno", Rol.DUENO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La página no puede ser negativa."));
    }

    @Test
    void elPreflightDeCorsPermiteElGet() throws Exception {
        mvc.perform(options("/api/inventario/buscar")
                .header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
    }
}
