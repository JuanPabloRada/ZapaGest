# ZapaGest
Sistema web para la administración de tiendas de calzado. Control de stock y ventas en un entorno local diseñado para los emprendedores informalez de calzado.

## Configuración local del backend

El backend requiere PostgreSQL y una clave JWT aleatoria en Base64 de al menos 32 bytes. En PowerShell, desde `backend`, genera una para la sesión actual y arranca Spring:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$env:DUENO_PASSWORD = "una-contraseña-local-segura"
.\mvnw.cmd spring-boot:run
```

La clave no debe guardarse en el repositorio. `FRONTEND_ORIGIN` puede establecerse como una lista separada por comas; de forma predeterminada se permiten `http://127.0.0.1:5500`, `http://localhost:5500` y `http://localhost:3000`. Si se sirve el frontend desde otro origen, configura esa variable antes de iniciar el backend.

El login devuelve un JWT con una expiración de ocho horas. El cliente web lo mantiene en `sessionStorage` y lo envía mediante `Authorization: Bearer <token>`. Los endpoints requieren token salvo `POST /api/auth/login`; la gestión de usuarios y calidades está restringida al rol `DUENO`, mientras que `DUENO` y `ADMIN` pueden gestionar ubicaciones.

### Contratos de integración

- `POST /api/auth/login` recibe `{ "username": "...", "password": "..." }` y responde `{ "token": "...", "usuario": { "id": 1, "nombre": "...", "username": "...", "rol": "ADMIN" } }`.
- Las respuestas de error de API usan `{ "mensaje": "..." }`. Un token ausente o inválido devuelve 401; un rol sin permiso devuelve 403.
- `GET` y `POST /api/usuarios` requieren rol `DUENO`. El alta usa `nombre`, `username`, `password` y `rol`.
- `GET /api/calidades` requiere autenticación; `POST /api/calidades` requiere `DUENO`.
- `GET /api/ubicaciones` requiere autenticación. `POST /api/ubicaciones` y `PATCH /api/ubicaciones/{id}` requieren `DUENO` o `ADMIN`; el PATCH recibe `{ "activa": true }` o `{ "activa": false }`.
- `GET /api/catalogo/**` requiere autenticación y `POST /api/catalogo/**` requiere `DUENO` o `ADMIN` (la regla ya está en `SeguridadConfig`; los endpoints los implementa Joseph).
- `GET` y `POST /api/catalogo` ya existen en el backend. El formulario envía `referencia`, `nombre`, `marca`, `categoria`, `colores`, `tallas` y `precios` (`calidadId`, `costo`, `precioMinimo`, `precioSugerido`); el precio de venta de la pantalla se envía como `precioSugerido`. Las tallas válidas son las europeas de 34 a 46 (de 0.5 en 0.5).
- `POST /api/calidades` solo toma el `nombre` (se recortan espacios, máximo 50 caracteres, no se permiten repetidos sin distinguir mayúsculas). Cualquier `id`, `negocioId` o `activa` enviado se ignora.
- Al arrancar, el backend crea las tallas europeas (34 a 46) en la tabla `talla` si faltan; no hace falta cargarlas a mano.


## Base de datos y primer arranque

1. Crea la base de datos una sola vez (el usuario por defecto es `postgres`):

```powershell
psql -U postgres -c "CREATE DATABASE zapagest_db;"
```

2. Si tu contraseña de PostgreSQL no es `postgres`, defínela con `DB_PASSWORD` (variable de entorno, nunca en el repositorio).
3. Define `JWT_SECRET` y `DUENO_PASSWORD` como se indica arriba y arranca con `.\mvnw.cmd spring-boot:run`.
4. En el primer arranque Hibernate crea las tablas (`ddl-auto=update`) y, si no existe ningún DUENO, se crea el usuario `dueno` con la contraseña de `DUENO_PASSWORD` (mínimo 6 caracteres). En los arranques siguientes `DUENO_PASSWORD` ya no es necesaria, pero `JWT_SECRET` sí.
5. Si falta `JWT_SECRET` el backend no arranca (es intencional).

## Pruebas

Pruebas automáticas (no necesitan PostgreSQL ni variables de entorno):

```powershell
.\mvnw.cmd test
```

- `UbicacionServiceTest`: regla de las 10 ubicaciones activas (crear, desactivar, reactivar) y aislamiento por negocio.
- `PermisosApiTest`: autenticación y permisos reales (`SeguridadConfig` + filtro JWT) con los controladores: 401 sin token/token inválido/expirado/usuario inactivo, 403 por rol, forma del login y CORS.
- `CalidadServiceTest`, `CatalogoServiceTest` y `SembradoTallasTest`: reglas de calidades, de registro de referencias (HU-05) y siembra de tallas.
- `JwtServiceTest`: emisión y lectura del token.
- `BackendApplicationTests`: arranque completo; solo se ejecuta si `JWT_SECRET` está definida y hay PostgreSQL.

Verificación contra el backend en ejecución (con la base de datos real). Usa la misma `DUENO_PASSWORD` con la que arrancaste:

```powershell
$env:DUENO_PASSWORD = "la-misma-clave-del-dueno"
.\scripts\verificar-api.ps1
```

El script comprueba login, 401/403 por rol, permisos de DUENO/ADMIN, CORS y la regla de 10 ubicaciones. Crea usuarios `zz_admin_*`/`zz_vend_*`, una calidad `zz-prueba-*` y ubicaciones `zz-prueba-*` (estas quedan inactivas al terminar). La API no permite borrarlos, así que úsalo solo en una base de datos de desarrollo.

Para probar el aislamiento entre negocios, inserta una ubicación de otro negocio y pasa su id al script:

```powershell
psql -U postgres -d zapagest_db -c "INSERT INTO ubicacion (negocio_id, nombre, activa, creado_en) VALUES (2, 'zz-otro-negocio', true, now()) RETURNING id;"
.\scripts\verificar-api.ps1 -IdUbicacionOtroNegocio <id-devuelto>
psql -U postgres -d zapagest_db -c "DELETE FROM ubicacion WHERE negocio_id = 2;"
```
