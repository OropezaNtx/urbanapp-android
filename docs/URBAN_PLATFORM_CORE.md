# Urban Platform Core

## Objetivo

Centralizar la infraestructura común de UrbanApp para que ASD, CC, FOV y futuros módulos reutilicen la misma base.

## Principio

Ningún módulo de estudio debe implementar por su cuenta:

- identidad de dispositivo
- workspace / organización / proyecto
- configuración remota
- licenciamiento
- permisos
- heartbeat
- diagnóstico
- sync health
- feature flags

Todo eso pertenece a Urban Platform Core.

## Estado actual

Ya existe base en:

```text
app/src/main/java/com/oropeza/urbanapp/core/
```

Componentes detectados:

```text
runtime
identity
platform
config
license
auth
diagnostics
events
bootstrap
```

La nueva implementación debe extender esto, no duplicarlo.

## Contratos principales

### UrbanRuntime

Fachada pública para el resto de la app.

### UrbanConfiguration

Configuración efectiva local/remota.

### UrbanLicense

Licencia efectiva.

### UrbanWorkspace

Organización/proyecto/ambiente.

### UrbanDiagnosticsSnapshot

Foto de salud de dispositivo/app/sync/licencia.

## Firestore propuesto

```text
app_config/{projectId}
installations/{installationId}
licenses/{licenseId}
live_devices/{installationId}
```

## Orden de implementación

1. Remote App Config.
2. Device heartbeat enriquecido.
3. Unified diagnostics.
4. Enforcement controlado.
5. Admin web para licencias/config.
6. Distribución APK.

## No tocar por ahora

- Room ASD.
- Exportadores.
- Captura ASD.
- track_chunks.
- events.
- trips.

## Lema

Una sola APK, múltiples clientes, configuración por nube y mínimo mantenimiento futuro.
