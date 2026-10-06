package com.zapgest.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Prueba de arranque completo: necesita PostgreSQL y las variables de entorno
// JWT_SECRET (y DUENO_PASSWORD la primera vez). Si JWT_SECRET no esta definida,
// se omite para que "mvnw test" siga funcionando sin base de datos.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
