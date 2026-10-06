<#
.SYNOPSIS
  Verifica contra el backend EN EJECUCION: login, 401/403, roles y regla de 10 ubicaciones (HU-04).

.DESCRIPTION
  - Lee la clave del dueño de la variable de entorno DUENO_PASSWORD (no se escribe en ningun archivo).
  - Crea un ADMIN y un VENDEDOR de prueba con nombre aleatorio y contraseña aleatoria (no se imprimen).
  - Crea ubicaciones "zz-prueba-..." y las deja INACTIVAS al terminar.
  - Usar solo contra una base de datos de desarrollo: la API no permite borrar usuarios,
    calidades ni ubicaciones, asi que los datos de prueba quedan registrados (inactivos o inofensivos).
  - Funciona en Windows PowerShell 5.1 y PowerShell 7.

.PARAMETER BaseUrl
  URL del backend. Por defecto http://localhost:8080

.PARAMETER Origen
  Origen del frontend para probar CORS. Por defecto http://localhost:5500

.PARAMETER IdUbicacionOtroNegocio
  (Opcional) id de una ubicacion insertada a mano con negocio_id = 2 (ver README).
  Si se indica, se comprueba que no aparece en el listado y que no se puede modificar.

.EXAMPLE
  $env:DUENO_PASSWORD = "..."   # la misma con la que arranco el backend
  .\scripts\verificar-api.ps1
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Origen = "http://localhost:5500",
    [long]$IdUbicacionOtroNegocio = 0
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$script:http = New-Object System.Net.Http.HttpClient
$script:http.Timeout = [TimeSpan]::FromSeconds(30)
$script:resultados = New-Object System.Collections.Generic.List[object]
$MSG_LIMITE = "Ha alcanzado el límite máximo permitido de 10 ubicaciones"
$MSG_403 = "No tiene permisos para realizar esta acción"

function Invoke-Api {
    param([string]$Metodo, [string]$Ruta, [string]$Token, $Cuerpo, [hashtable]$Cabeceras)
    $metodoHttp = New-Object System.Net.Http.HttpMethod $Metodo
    $req = New-Object System.Net.Http.HttpRequestMessage($metodoHttp, ($BaseUrl.TrimEnd('/') + $Ruta))
    if ($Token) {
        $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
    }
    if ($Cabeceras) {
        foreach ($k in $Cabeceras.Keys) { [void]$req.Headers.TryAddWithoutValidation($k, [string]$Cabeceras[$k]) }
    }
    if ($null -ne $Cuerpo) {
        $json = $Cuerpo | ConvertTo-Json -Compress
        $req.Content = New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, "application/json")
    }
    $resp = $script:http.SendAsync($req).GetAwaiter().GetResult()
    $bytes = $resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $texto = [Text.Encoding]::UTF8.GetString($bytes)
    $datos = $null
    try { if ($texto) { $datos = $texto | ConvertFrom-Json } } catch { $datos = $null }
    $tipo = ""
    if ($resp.Content.Headers.ContentType) { $tipo = $resp.Content.Headers.ContentType.ToString() }
    $cors = $null
    $vals = $null
    if ($resp.Headers.TryGetValues("Access-Control-Allow-Origin", [ref]$vals)) { $cors = ($vals -join ",") }
    return [pscustomobject]@{ Estado = [int]$resp.StatusCode; Datos = $datos; Texto = $texto; Tipo = $tipo; Cors = $cors }
}

function Comprobar {
    param([string]$Nombre, [bool]$Cumple, [string]$Detalle = "")
    $script:resultados.Add([pscustomobject]@{ Nombre = $Nombre; Ok = $Cumple; Detalle = $Detalle })
    if ($Cumple) { Write-Host ("  [OK]    " + $Nombre) -ForegroundColor Green }
    else { Write-Host ("  [FALLA] " + $Nombre + $(if ($Detalle) { "  -> " + $Detalle } else { "" })) -ForegroundColor Red }
}

function Tiene-Mensaje($r, $esperado) {
    return ($r.Datos -and $r.Datos.PSObject.Properties.Name -contains "mensaje" -and ($null -eq $esperado -or $r.Datos.mensaje -eq $esperado))
}

function Seccion($t) { Write-Host ""; Write-Host ("== " + $t) -ForegroundColor Cyan }

function Nueva-Clave {
    $bytes = New-Object byte[] 12
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes); $rng.Dispose()
    return ([Convert]::ToBase64String($bytes) -replace '[^A-Za-z0-9]', 'x')
}

# ---------------------------------------------------------------- 0. Requisitos
if (-not $env:DUENO_PASSWORD) {
    Write-Host "Define primero DUENO_PASSWORD (la misma con la que arrancaste el backend)." -ForegroundColor Red
    exit 2
}
$corrida = (Get-Date -Format "MMddHHmmss")

# ---------------------------------------------------------------- 1. Login
Seccion "1. Arranque y login"
try {
    $login = Invoke-Api -Metodo POST -Ruta "/api/auth/login" -Cuerpo @{ username = "dueno"; password = $env:DUENO_PASSWORD }
} catch {
    Write-Host ("No se pudo conectar con " + $BaseUrl + ". ¿Esta arrancado el backend? " + $_.Exception.Message) -ForegroundColor Red
    exit 2
}
Comprobar "Login del dueño devuelve 200" ($login.Estado -eq 200) ("estado " + $login.Estado)
if ($login.Estado -ne 200) {
    Write-Host "Sin login del dueño no se puede continuar." -ForegroundColor Red
    exit 1
}
$u = $login.Datos.usuario
Comprobar "La respuesta trae 'token'" ([bool]$login.Datos.token)
Comprobar "'usuario' trae id, nombre, username y rol" ($u -and $null -ne $u.id -and $u.nombre -and $u.username -and $u.rol)
Comprobar "El rol del usuario inicial es DUENO" ($u.rol -eq "DUENO")
Comprobar "La respuesta de login no incluye el hash de la contraseña" ($login.Texto -notmatch "password")
$tDueno = $login.Datos.token

$malo = Invoke-Api -Metodo POST -Ruta "/api/auth/login" -Cuerpo @{ username = "dueno"; password = (Nueva-Clave) }
Comprobar "Login con clave incorrecta -> 401 con 'mensaje'" ($malo.Estado -eq 401 -and (Tiene-Mensaje $malo $null)) ("estado " + $malo.Estado)

# ---------------------------------------------------------------- 2. Usuarios de prueba
Seccion "2. Usuarios de prueba (ADMIN y VENDEDOR)"
$claveAdmin = Nueva-Clave; $claveVend = Nueva-Clave
$userAdmin = "zz_admin_$corrida"; $userVend = "zz_vend_$corrida"
$r1 = Invoke-Api -Metodo POST -Ruta "/api/usuarios" -Token $tDueno -Cuerpo @{ nombre = "Admin Prueba"; username = $userAdmin; password = $claveAdmin; rol = "ADMIN" }
Comprobar "DUENO crea un usuario ADMIN" ($r1.Estado -eq 200) ("estado " + $r1.Estado + " " + $r1.Texto)
$r2 = Invoke-Api -Metodo POST -Ruta "/api/usuarios" -Token $tDueno -Cuerpo @{ nombre = "Vendedor Prueba"; username = $userVend; password = $claveVend; rol = "VENDEDOR" }
Comprobar "DUENO crea un usuario VENDEDOR" ($r2.Estado -eq 200) ("estado " + $r2.Estado + " " + $r2.Texto)
$rl = Invoke-Api -Metodo GET -Ruta "/api/usuarios" -Token $tDueno
Comprobar "DUENO lista usuarios (200) sin exponer contraseñas" ($rl.Estado -eq 200 -and $rl.Texto -notmatch "passwordHash")

$lA = Invoke-Api -Metodo POST -Ruta "/api/auth/login" -Cuerpo @{ username = $userAdmin; password = $claveAdmin }
$lV = Invoke-Api -Metodo POST -Ruta "/api/auth/login" -Cuerpo @{ username = $userVend; password = $claveVend }
if ($lA.Estado -ne 200 -or $lV.Estado -ne 200) {
    Write-Host "No se pudo iniciar sesion con los usuarios de prueba; se detiene." -ForegroundColor Red
    exit 1
}
$tAdmin = $lA.Datos.token; $tVend = $lV.Datos.token

# ---------------------------------------------------------------- 3. 401 y 403
Seccion "3. Permisos 401 / 403"
$rutas = @(
    @{ M = "GET";   R = "/api/usuarios" },
    @{ M = "POST";  R = "/api/usuarios";    B = @{ nombre = "x"; username = "x"; password = "123456"; rol = "VENDEDOR" } },
    @{ M = "GET";   R = "/api/calidades" },
    @{ M = "POST";  R = "/api/calidades";   B = @{ nombre = "x" } },
    @{ M = "GET";   R = "/api/ubicaciones" },
    @{ M = "POST";  R = "/api/ubicaciones"; B = @{ nombre = "x" } },
    @{ M = "PATCH"; R = "/api/ubicaciones/1"; B = @{ activa = $false } }
)
foreach ($x in $rutas) {
    $r = Invoke-Api -Metodo $x.M -Ruta $x.R -Cuerpo $x.B
    Comprobar ("Sin token: $($x.M) $($x.R) -> 401 con 'mensaje'") ($r.Estado -eq 401 -and (Tiene-Mensaje $r $null) -and $r.Tipo -like "*json*") ("estado " + $r.Estado)
}
$r = Invoke-Api -Metodo GET -Ruta "/api/ubicaciones" -Token "token.invalido.xyz"
Comprobar "Token inválido -> 401 con 'mensaje'" ($r.Estado -eq 401 -and (Tiene-Mensaje $r $null)) ("estado " + $r.Estado)

foreach ($x in @(
        @{ M = "GET";   R = "/api/usuarios" },
        @{ M = "POST";  R = "/api/usuarios"; B = @{ nombre = "x"; username = "x"; password = "123456"; rol = "VENDEDOR" } },
        @{ M = "POST";  R = "/api/calidades"; B = @{ nombre = "x" } })) {
    $r = Invoke-Api -Metodo $x.M -Ruta $x.R -Token $tAdmin -Cuerpo $x.B
    Comprobar ("ADMIN: $($x.M) $($x.R) -> 403 con mensaje acordado") ($r.Estado -eq 403 -and (Tiene-Mensaje $r $MSG_403)) ("estado " + $r.Estado)
}
foreach ($x in @(
        @{ M = "GET";   R = "/api/usuarios" },
        @{ M = "POST";  R = "/api/calidades"; B = @{ nombre = "x" } },
        @{ M = "POST";  R = "/api/ubicaciones"; B = @{ nombre = "x" } },
        @{ M = "PATCH"; R = "/api/ubicaciones/1"; B = @{ activa = $false } })) {
    $r = Invoke-Api -Metodo $x.M -Ruta $x.R -Token $tVend -Cuerpo $x.B
    Comprobar ("VENDEDOR: $($x.M) $($x.R) -> 403 con mensaje acordado") ($r.Estado -eq 403 -and (Tiene-Mensaje $r $MSG_403)) ("estado " + $r.Estado)
}
$r = Invoke-Api -Metodo GET -Ruta "/api/ubicaciones" -Token $tVend
Comprobar "VENDEDOR puede consultar ubicaciones (200)" ($r.Estado -eq 200)
$r = Invoke-Api -Metodo GET -Ruta "/api/calidades" -Token $tVend
Comprobar "VENDEDOR puede consultar calidades (200)" ($r.Estado -eq 200)

$r = Invoke-Api -Metodo POST -Ruta "/api/calidades" -Token $tDueno -Cuerpo @{ nombre = "zz-prueba-$corrida" }
Comprobar "DUENO crea una calidad (200)" ($r.Estado -eq 200) ("estado " + $r.Estado + " " + $r.Texto)

# ---------------------------------------------------------------- 4. CORS
Seccion "4. CORS (el frontend usa otro origen)"
$r = Invoke-Api -Metodo OPTIONS -Ruta "/api/ubicaciones/1" -Cabeceras @{ "Origin" = $Origen; "Access-Control-Request-Method" = "PATCH"; "Access-Control-Request-Headers" = "authorization,content-type" }
Comprobar "Preflight desde $Origen permitido" ($r.Estado -eq 200 -and $r.Cors -eq $Origen) ("estado " + $r.Estado + ", Allow-Origin=" + $r.Cors)

# ---------------------------------------------------------------- 5. HU-04
Seccion "5. HU-04: ubicaciones y limite de 10 activas"
function Listar-Ubicaciones($token) {
    $l = Invoke-Api -Metodo GET -Ruta "/api/ubicaciones" -Token $token
    if ($l.Estado -ne 200) { throw "GET /api/ubicaciones devolvio " + $l.Estado }
    return @($l.Datos)
}
function Contar-Activas($lista) { return @($lista | Where-Object { $_.activa -ne $false }).Count }

$propias = New-Object System.Collections.Generic.List[long]
try {
    $lista = Listar-Ubicaciones $tAdmin
    $activasInicio = Contar-Activas $lista
    Write-Host ("  Activas al empezar: " + $activasInicio) -ForegroundColor DarkGray

    # Llenar hasta 10 activas con ubicaciones propias
    $n = 0
    while ((Contar-Activas (Listar-Ubicaciones $tAdmin)) -lt 10) {
        $n++
        $c = Invoke-Api -Metodo POST -Ruta "/api/ubicaciones" -Token $tAdmin -Cuerpo @{ nombre = "zz-prueba-$corrida-$n" }
        if ($c.Estado -ne 200) { Comprobar "Crear ubicación mientras hay menos de 10 activas" $false ("estado " + $c.Estado + " " + $c.Texto); break }
        $propias.Add([long]$c.Datos.id)
        if ($n -gt 12) { break }
    }
    $activas = Contar-Activas (Listar-Ubicaciones $tAdmin)
    Comprobar "Se llega a 10 ubicaciones activas" ($activas -eq 10) ("activas = $activas")

    # 11a bloqueada
    $c11 = Invoke-Api -Metodo POST -Ruta "/api/ubicaciones" -Token $tAdmin -Cuerpo @{ nombre = "zz-prueba-$corrida-11" }
    Comprobar "Crear la número 11 se bloquea (400, mensaje acordado)" ($c11.Estado -eq 400 -and (Tiene-Mensaje $c11 $MSG_LIMITE)) ("estado " + $c11.Estado + " " + $c11.Texto)
    Comprobar "Sigue habiendo 10 activas tras el intento bloqueado" ((Contar-Activas (Listar-Ubicaciones $tAdmin)) -eq 10)

    if ($propias.Count -ge 1) {
        $victima = $propias[0]
        $d = Invoke-Api -Metodo PATCH -Ruta "/api/ubicaciones/$victima" -Token $tAdmin -Cuerpo @{ activa = $false }
        Comprobar "Desactivar una ubicación (200, activa=false)" ($d.Estado -eq 200 -and $d.Datos.activa -eq $false) ("estado " + $d.Estado)
        Comprobar "Al desactivar, el conteo de activas baja a 9 (solo cuentan las activas)" ((Contar-Activas (Listar-Ubicaciones $tAdmin)) -eq 9)

        $extra = Invoke-Api -Metodo POST -Ruta "/api/ubicaciones" -Token $tDueno -Cuerpo @{ nombre = "zz-prueba-$corrida-extra" }
        Comprobar "Con 9 activas se puede crear otra (DUENO) y vuelve a 10" ($extra.Estado -eq 200 -and (Contar-Activas (Listar-Ubicaciones $tAdmin)) -eq 10) ("estado " + $extra.Estado)
        if ($extra.Estado -eq 200) { $propias.Add([long]$extra.Datos.id) }

        $re = Invoke-Api -Metodo PATCH -Ruta "/api/ubicaciones/$victima" -Token $tAdmin -Cuerpo @{ activa = $true }
        Comprobar "Reactivar con 10 activas se bloquea (400, mensaje acordado)" ($re.Estado -eq 400 -and (Tiene-Mensaje $re $MSG_LIMITE)) ("estado " + $re.Estado + " " + $re.Texto)

        if ($extra.Estado -eq 200) {
            [void](Invoke-Api -Metodo PATCH -Ruta "/api/ubicaciones/$($extra.Datos.id)" -Token $tAdmin -Cuerpo @{ activa = $false })
            $re2 = Invoke-Api -Metodo PATCH -Ruta "/api/ubicaciones/$victima" -Token $tAdmin -Cuerpo @{ activa = $true }
            Comprobar "Con 9 activas, reactivar funciona (200, activa=true)" ($re2.Estado -eq 200 -and $re2.Datos.activa -eq $true) ("estado " + $re2.Estado)
        }
    } else {
        Write-Host "  (Ya había 10 o más activas que no son de prueba; se omiten desactivar/reactivar.)" -ForegroundColor Yellow
    }

    $nx = Invoke-Api -Metodo PATCH -Ruta "/api/ubicaciones/999999999" -Token $tAdmin -Cuerpo @{ activa = $false }
    Comprobar "PATCH a un id inexistente -> 400 'No se encontró la ubicación.'" ($nx.Estado -eq 400 -and (Tiene-Mensaje $nx "No se encontró la ubicación.")) ("estado " + $nx.Estado)
    $nv = Invoke-Api -Metodo PATCH -Ruta "/api/ubicaciones/$($propias[0])" -Token $tAdmin -Cuerpo @{ }
    Comprobar "PATCH sin 'activa' -> 400 con mensaje" ($nv.Estado -eq 400 -and (Tiene-Mensaje $nv $null)) ("estado " + $nv.Estado)
    $ab = Invoke-Api -Metodo PATCH -Ruta "/api/ubicaciones/abc" -Token $tAdmin -Cuerpo @{ activa = $true }
    Comprobar "PATCH con id no numérico -> 400 con 'mensaje'" ($ab.Estado -eq 400 -and (Tiene-Mensaje $ab $null)) ("estado " + $ab.Estado)

    if ($IdUbicacionOtroNegocio -gt 0) {
        $enLista = @(Listar-Ubicaciones $tDueno | Where-Object { $_.id -eq $IdUbicacionOtroNegocio }).Count
        Comprobar "Ubicación de otro negocio NO aparece en el listado" ($enLista -eq 0)
        $on = Invoke-Api -Metodo PATCH -Ruta "/api/ubicaciones/$IdUbicacionOtroNegocio" -Token $tDueno -Cuerpo @{ activa = $false }
        Comprobar "Ubicación de otro negocio NO se puede modificar (400/404)" ($on.Estado -in 400, 404) ("estado " + $on.Estado)
    } else {
        Write-Host "  (Prueba de otro negocio omitida: pasa -IdUbicacionOtroNegocio, ver README.)" -ForegroundColor Yellow
    }
}
finally {
    # Limpieza: dejar inactivas las ubicaciones de prueba
    foreach ($id in $propias) {
        try { [void](Invoke-Api -Metodo PATCH -Ruta "/api/ubicaciones/$id" -Token $tAdmin -Cuerpo @{ activa = $false }) } catch { }
    }
    Write-Host ("  Limpieza: " + $propias.Count + " ubicaciones de prueba desactivadas.") -ForegroundColor DarkGray
}

# ---------------------------------------------------------------- Resumen
Seccion "Resumen"
$fallas = @($script:resultados | Where-Object { -not $_.Ok })
Write-Host ("  Comprobaciones: " + $script:resultados.Count + "   Correctas: " + ($script:resultados.Count - $fallas.Count) + "   Fallidas: " + $fallas.Count)
if ($fallas.Count -gt 0) {
    Write-Host "  Fallidas:" -ForegroundColor Red
    foreach ($f in $fallas) { Write-Host ("   - " + $f.Nombre + " " + $f.Detalle) -ForegroundColor Red }
    exit 1
}
Write-Host "  Todo correcto." -ForegroundColor Green
exit 0
