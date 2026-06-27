# UrbanApp - Licenciamiento y distribución

## Objetivo

Permitir una sola APK para todos los clientes, con activación, bloqueo remoto, control de módulos y distribución controlada.

## Principio

La APK no se personaliza por cliente. El cliente, proyecto, módulos y permisos se resuelven desde Firebase.

```text
APK única
  -> installationId
  -> licencia
  -> proyecto
  -> configuración
  -> módulos habilitados
```

## Colecciones propuestas

### licenses/{licenseId}

```json
{
  "licenseId": "lic_demo_001",
  "customerId": "customer_demo",
  "status": "ACTIVE",
  "plan": "PRO",
  "expiresAt": 1798761600000,
  "maxDevices": 50,
  "maxProjects": 10,
  "gracePeriodDays": 7,
  "modules": {
    "asd": true,
    "delays": true,
    "flags": true,
    "live": true,
    "exports": true,
    "garmin": true,
    "analytics": true
  },
  "updatedAt": "serverTimestamp"
}
```

Estados:

```text
ACTIVE
SUSPENDED
EXPIRED
BLOCKED
```

### installations/{installationId}

```json
{
  "installationId": "android_id",
  "licenseId": "lic_demo_001",
  "customerId": "customer_demo",
  "projectId": "project_demo",
  "status": "ACTIVE",
  "deviceModel": "Xiaomi 22101320G",
  "manufacturer": "Xiaomi",
  "androidVersion": "14",
  "appVersion": "1.0.0",
  "registeredAt": "serverTimestamp",
  "lastSeen": "serverTimestamp",
  "lastLicenseCheck": 1760000000000
}
```

Estados:

```text
PENDING
ACTIVE
BLOCKED
REVOKED
```

### app_config/{projectId}

```json
{
  "projectId": "project_demo",
  "customerId": "customer_demo",
  "status": "ACTIVE",
  "studyName": "Estudio ASD Demo",
  "syncEnabled": true,
  "liveEnabled": true,
  "minAppVersion": "1.0.0",
  "forceUpgrade": false,
  "maintenanceMode": false,
  "message": ""
}
```

## Comportamiento Android

### Al iniciar

1. Resolver `installationId`.
2. Leer instalación.
3. Leer licencia.
4. Leer configuración.
5. Evaluar acceso.
6. Si todo OK, entra a la app.
7. Si no, mostrar pantalla bloqueada.

### Offline grace period

Si la app no tiene internet pero la última licencia válida está dentro del periodo de gracia:

```text
Permitir uso offline
```

Si excede el periodo:

```text
Bloquear captura nueva
Permitir exportar datos locales
Mostrar aviso de licencia
```

## Bloqueo remoto

Para cortar servicio:

```text
licenses/{licenseId}.status = BLOCKED
```

O por dispositivo:

```text
installations/{installationId}.status = BLOCKED
```

La app debe quedar vacía/inhabilitada para nuevas capturas, pero debe proteger datos locales hasta que se defina política de borrado.

## Importante sobre borrado de datos

No se recomienda borrar Room automáticamente al bloquear licencia. Mejor:

1. Bloquear nuevas capturas.
2. Bloquear sync.
3. Permitir exportación local si se necesita rescate.
4. Borrado local solo con acción explícita administrativa.

Esto evita pérdida accidental de datos de campo.

## Distribución APK

Opciones recomendadas:

### Fase inicial

- APK firmada manualmente.
- Distribución por enlace privado.
- Control de instalación por licencia.

### Fase profesional

- Firebase App Distribution.
- Grupos por cliente/proyecto.
- Release notes.
- Versionado.

### Fase enterprise

- Android Enterprise / MDM.
- Instalación remota.
- Políticas de actualización.
- Bloqueo por dispositivo.

## Versionado recomendado

```text
1.0.0 Captura estable
1.1.0 Live Operations
1.2.0 Licenciamiento
1.3.0 Admin Console
2.0.0 Urban Suite
```

## Próximos pasos técnicos

1. Crear módulo Android `license`.
2. Crear `LicenseManager`.
3. Crear pantalla bloqueada.
4. Guardar cache local de licencia.
5. Añadir campos de licencia a `live_devices`.
6. Crear panel web de instalaciones/licencias.
