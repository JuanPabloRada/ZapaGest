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
- El catálogo aún no tiene persistencia ni endpoints en el backend. Cuando Joseph los implemente, el precio enviado por el formulario como `precioVenta` se mapeará al campo persistido `precioSugerido`.
