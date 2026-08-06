# Auditoría completa del flujo ASD — estabilización de campo

**Rama auditada:** `stabilization/field-release`  
**Objetivo:** garantizar que la operación ASD funcione sin internet y que GPS/Firebase nunca bloqueen la captura local.  
**Estado:** diagnóstico técnico inicial confirmado mediante inspección del flujo y prueba en dispositivo real.

---

## 1. Resumen ejecutivo

El recorrido se inserta correctamente en Room, pero la navegación a la pantalla operativa queda bloqueada porque la misma corrutina que crea el recorrido también intenta procesar la cola de sincronización contra Firestore antes de regresar a la UI.

El defecto central está en `AsdRepository.enqueueSync()`:

1. Inserta correctamente el elemento en `sync_queue`.
2. Inmediatamente llama de forma suspendida a `UrbanCloudSyncScheduler.syncNow(context)`.
3. `syncNow()` ejecuta `CloudSyncEngine.processNextBatch()` y espera operaciones contra el destino cloud.
4. La creación del recorrido no retorna a `AsdNewTripScreen` hasta que ese intento termina.
5. `onCreated(tripId)` y la navegación se ejecutan demasiado tarde o no parecen ejecutarse mientras no hay conectividad.

Esto viola el principio Offline First aunque los datos sí se hayan guardado localmente.

### Causa raíz confirmada

**La persistencia local y la sincronización remota están acopladas dentro de la misma operación suspendida.**

La navegación no depende directamente de GPS ni de Wi-Fi, pero queda indirectamente bloqueada por una llamada cloud esperada dentro de `enqueueSync()`.

---

## 2. Flujo actual observado

### Creación de recorrido

```text
Botón CREAR RECORRIDO
  -> createTripFlow()
  -> AsdNewTripVM.createWithFix()
  -> AsdRepository.createTripWithStartFix()
  -> tripDao.insert()                         [Room: correcto]
  -> enqueueSync(TRIP)
       -> syncQueueDao.insert()               [Room: correcto]
       -> UrbanCloudSyncScheduler.syncNow()   [BLOQUEO REMOTO]
  -> addStopDetailed(AD/INICIO)
       -> stopDao.insert()                    [Room: correcto]
       -> enqueueSync(EVENT)
            -> syncQueueDao.insert()          [Room: correcto]
            -> UrbanCloudSyncScheduler.syncNow() [SEGUNDO BLOQUEO REMOTO]
  -> return tripId
  -> onCreated(tripId)
  -> navegación al detalle
```

La creación hace potencialmente dos intentos completos de sincronización antes de navegar: uno para el viaje y otro para `AD/INICIO`.

### Guardado de evento

```text
Botón GUARDAR EVENTO
  -> AddStopUseCase
  -> addStopDetailed()
  -> stopDao.insert()                         [Room: correcto]
  -> enqueueSync(EVENT)
       -> syncQueueDao.insert()               [Room: correcto]
       -> syncNow()                           [BLOQUEO REMOTO]
  -> UI recibe resultado
```

El evento queda en Room antes del bloqueo. Por eso puede existir aunque el usuario no reciba confirmación inmediata.

### Cierre de recorrido

```text
Botón FINALIZAR
  -> TripOperationManager.closeTrip()
  -> endTripWithFix()
  -> addStopDetailed(AD/FINAL)
       -> insert local
       -> enqueue + syncNow()                 [BLOQUEO]
  -> tripDao.update(endTime)
  -> enqueueSync(TRIP)
       -> syncNow()                           [OTRO BLOQUEO]
  -> enqueueTrackChunks()
  -> retorna éxito
  -> UI detiene servicio y navega atrás
```

Esto explica por qué finalizar funciona pero tarda.

---

## 3. Hallazgos priorizados

## P0-01 — Sincronización remota bloquea operaciones locales

**Severidad:** crítica  
**Impacto:** no se puede confiar en la UI durante pérdida de conectividad.  
**Archivos:**

- `asd/data/repository/repository.kt`
- `core/platform/sync/UrbanCloudSyncScheduler.kt`
- `asd/sync/cloud/CloudSyncEngine.kt`

### Evidencia

`enqueueSync()` es suspendida y llama directamente a `UrbanCloudSyncScheduler.syncNow(context)`. A su vez, `syncNow()` procesa lotes y espera al destino Firestore.

### Corrección recomendada

`enqueueSync()` debe limitarse a una transacción local:

```text
crear payload
-> insertar en sync_queue
-> regresar inmediatamente
```

La activación de sincronización debe ser no bloqueante y estar desacoplada:

- WorkManager con restricción `NetworkType.CONNECTED`, o
- scheduler que lance un trabajo independiente sin ser esperado por la operación de dominio.

**Regla:** ningún caso de uso de captura debe esperar una llamada Firebase.

---

## P0-02 — Confirmación de UI ocurre después de la sincronización

**Severidad:** crítica  
**Impacto:** el usuario puede repetir acciones, abandonar la pantalla o considerar perdido un registro que ya existe.

La UI espera el retorno completo del repositorio. Como el repositorio espera `syncNow()`, la confirmación local y la navegación se retrasan.

### Corrección recomendada

Los casos de uso deben retornar éxito cuando finaliza la transacción Room, no cuando termina la nube.

Resultado recomendado:

```kotlin
LocalOperationResult.Saved(localId, syncState = QUEUED)
```

La UI debe navegar o limpiar el formulario con ese resultado local.

---

## P0-03 — Inicio y cierre generan múltiples sincronizaciones consecutivas

**Severidad:** crítica para rendimiento/conectividad débil.  
**Impacto:** creación y cierre pueden esperar dos o más intentos cloud.

`createTripWithStartFix()` encola viaje y evento inicial. Cada inserción dispara `syncNow()` por separado. El cierre hace lo mismo con evento final, viaje y chunks.

### Corrección recomendada

Encolar todas las mutaciones localmente y programar **un solo trabajo** de sincronización al final, sin esperarlo.

---

## P1-01 — Ruta cloud incorrecta al recuperar GPS de eventos

**Severidad:** alta  
**Impacto:** un evento recuperado puede sincronizarse bajo un viaje inexistente o incorrecto.

`completePendingGpsEvents()` llama:

```kotlin
enqueueSync("EVENT", "UPDATE", event.eventId, updatedEvent)
```

sin proporcionar `cloudPath`.

El fallback de `enqueueSync()` para eventos construye una ruta con viaje `installationId_0`, porque solo recibe `eventId` y no `tripId`.

### Corrección recomendada

Construir siempre la ruta explícita usando:

- `event.tripId`
- `installationId`
- identificador cloud del evento

Nunca debe existir un fallback de evento que use viaje `0`.

---

## P1-02 — Estado de sincronización por recorrido consulta IDs incompatibles

**Severidad:** alta  
**Impacto:** la UI puede marcar un recorrido como pendiente aunque parte del contenido esté sincronizado, o viceversa.

`AsdSyncQueueDao.getTripSyncItemStatusesFlow(tripId)` filtra:

```sql
entityLocalId = :tripId
AND entityType IN ('TRIP', 'EVENT', 'TRACK_CHUNK')
```

Pero:

- para `TRIP`, `entityLocalId` sí es `tripId`;
- para `EVENT`, `entityLocalId` es `eventId`;
- para chunks puede no representar el `tripId` de forma consistente.

Por tanto, la consulta no puede calcular correctamente el estado completo del recorrido.

### Corrección recomendada

Agregar `parentTripId` a `sync_queue`, o extraerlo como columna normalizada. No depender de interpretar `entityLocalId` de entidades diferentes.

---

## P1-03 — Recorridos antiguos pueden no haber sido encolados

**Severidad:** alta  
**Impacto:** datos locales históricos permanecen pendientes indefinidamente.

Los recorridos creados antes de la cola actual pueden no tener elementos `TRIP`, `EVENT` y `TRACK_CHUNK` en `sync_queue`. El motor solo procesa elementos existentes; no reconstruye faltantes.

### Corrección recomendada

Implementar un reconciliador idempotente:

```text
leer viajes locales
-> detectar entidades sin representación cloud/cola
-> reconstruir UPSERT de viaje
-> reconstruir eventos
-> reconstruir chunks
-> no duplicar elementos equivalentes pendientes
```

Debe existir una acción visible: **Reparar sincronización local**.

---

## P1-04 — Elementos IN_PROGRESS pueden quedar huérfanos

**Severidad:** alta  
**Impacto:** si el proceso muere después de marcar `IN_PROGRESS`, el elemento deja de ser elegible porque `getPending()` solo consulta `PENDING` y `FAILED`.

### Corrección recomendada

Al iniciar el motor, recuperar elementos `IN_PROGRESS` antiguos y regresarlos a `FAILED` o `PENDING` después de un timeout.

---

## P1-05 — El bucle del scheduler usa cantidad de éxitos para decidir continuidad

**Severidad:** media-alta.

`processNextBatch()` retorna el número de éxitos, no el número de elementos procesados. Si un lote contiene elementos fallidos y cero éxitos, el scheduler termina inmediatamente. Esto es razonable para evitar un bucle instantáneo, pero mezcla dos conceptos y puede dejar elementos elegibles posteriores sin revisión.

### Corrección recomendada

Retornar un resultado estructurado:

```kotlin
BatchResult(processed, synced, retryableFailed, permanentFailed)
```

El scheduler decide continuar según `processed` y disponibilidad elegible, respetando backoff.

---

## P1-06 — Firestore directo adicional fuera de la cola

**Severidad:** media-alta  
**Archivo:** `AsdOnlineBackup.kt`

Cada evento ejecuta además un `.set()` directo hacia `urbanapp_asd_backups`. Aunque no se espera el resultado, representa una segunda ruta cloud, con esquema y permisos diferentes.

### Riesgos

- duplicación conceptual;
- reglas inconsistentes;
- errores difíciles de diagnosticar;
- consumo y tráfico innecesarios;
- la web puede mostrar fallback distinto al modelo oficial.

### Corrección recomendada

Durante estabilización, elegir una sola estrategia:

1. cola oficial y rutas de plataforma, o
2. backup de emergencia explícito.

No mantener ambas como fuentes equivalentes.

---

## P1-07 — La web y Android pueden consultar rutas/configuración distintas

**Severidad:** alta para Operations Center.

La web usa variables `VITE_URBAN_ORG_ID`, `VITE_URBAN_PROJECT_ID` y fallback `demo_workspace/demo_project`. Android construye rutas desde workspace/identidad runtime.

Si las variables web no coinciden exactamente con las usadas por Android, la web mostrará cero recorridos aunque Firestore contenga datos.

Adicionalmente, las reglas revisadas no cubren las rutas jerárquicas `asd_organizations/{org}/projects/{project}/trips`.

### Corrección recomendada

- mostrar en diagnóstico Android la ruta cloud efectiva;
- mostrar en consola web org/project efectivos;
- unificar configuración;
- desplegar reglas que autoricen explícitamente las colecciones oficiales;
- confirmar autenticación web.

---

## P2-01 — `FirebaseAuth.currentUser` determina el arranque de navegación

`AppNavHost` elige `home` o `login` según el usuario Firebase actual. Un token expirado o estado de autenticación inconsistente puede afectar el acceso al abrir la aplicación sin internet.

No fue la causa de la prueba actual porque la app ya estaba dentro de ASD, pero es un riesgo para jornadas futuras.

### Corrección recomendada

Permitir sesión operativa offline basada en credenciales/cache local válidas y resolver renovación Firebase en segundo plano.

---

## P2-02 — Backfill GPS limitado al mismo viaje activo

`TrackingService.persistSample()` completa eventos pendientes únicamente para `currentTripId`. Esto es correcto para campo activo, pero los pendientes históricos no se repararán automáticamente al reabrir o sincronizar.

### Corrección recomendada

Agregar una tarea de mantenimiento que pueda asociar puntos históricos del mismo viaje por proximidad temporal, sin inventar coordenadas.

---

## P2-03 — Precisión visual GPS puede confundirse con disponibilidad operativa

El indicador rojo comunica falta de fix, pero el usuario lo interpreta como impedimento para trabajar porque antes efectivamente bloqueaba el flujo.

### Corrección recomendada

Separar estados:

- **OPERACIÓN LOCAL: LISTA**
- **GPS: BUSCANDO / PRECISO / DEGRADADO**
- **NUBE: OFFLINE / PENDIENTE / SINCRONIZADO**

Un estado no debe dominar visualmente a los otros.

---

## 4. Arquitectura objetivo

```text
UI
 -> caso de uso local
     -> transacción Room
         -> entidad operativa
         -> evento inicial/final
         -> sync_queue
     -> retorna éxito local inmediatamente
 -> UI navega/confirma

TrackingService (independiente)
 -> captura puntos
 -> completa GPS pendiente
 -> encola actualización local

WorkManager (independiente)
 -> solo con red
 -> procesa sync_queue
 -> actualiza estados
 -> aplica reintentos/backoff
```

### Invariantes obligatorias

1. Room es la única condición de éxito para una acción de campo.
2. Firebase nunca se espera desde un botón operativo.
3. GPS enriquece; no autoriza ni bloquea.
4. Cada acción local queda en cola dentro de la misma transacción lógica.
5. Sin conectividad, la UI debe responder igual salvo el estado de nube.
6. Toda cola debe ser recuperable después de cierre forzado o reinicio.
7. Las rutas cloud deben construirse con IDs explícitos, nunca con fallbacks ambiguos.

---

## 5. Plan inmediato de solución

### Sprint 1.1 — Desacoplamiento crítico

1. Eliminar `syncNow()` de `AsdRepository.enqueueSync()`.
2. Convertir el scheduler en disparo no bloqueante mediante WorkManager.
3. Garantizar que crear, guardar y cerrar retornen después de Room.
4. Compilar y probar con Wi-Fi/datos desactivados.

### Sprint 1.2 — Integridad de rutas y estados

1. Corregir ruta cloud de backfill GPS.
2. Añadir `parentTripId` a la cola mediante migración Room.
3. Corregir estado agregado por recorrido.
4. Recuperar `IN_PROGRESS` huérfanos.

### Sprint 1.3 — Reconciliación histórica

1. Detectar viajes antiguos sin cola.
2. Reencolar viaje/eventos/tracks idempotentemente.
3. Añadir diagnóstico y acción manual de reparación.

### Sprint 1.4 — Operations Center

1. Alinear org/project/workspace.
2. Corregir reglas Firestore.
3. Confirmar autenticación web.
4. Validar viaje, eventos y track desde la web.

---

## 6. Matriz de aceptación del desacoplamiento

| Prueba | Resultado obligatorio |
|---|---|
| Crear con GPS apagado e internet apagado | Navega inmediatamente al detalle |
| Guardar evento sin GPS/internet | Aparece inmediatamente en historial |
| Finalizar sin internet | Regresa a lista y detiene tracking |
| Reiniciar app offline | Viaje y eventos siguen presentes |
| Encender internet después | Cola se procesa sin intervención operativa |
| Fallar Firestore | No afecta creación, captura ni cierre |
| Cerrar proceso durante sync | Cola se recupera posteriormente |

---

## 7. Conclusión

El problema principal no es Room, GPS ni Compose Navigation. La persistencia local funciona. El bloqueo se origina porque `enqueueSync()` ejecuta y espera una sincronización cloud desde la misma corrutina de la operación de campo.

La corrección prioritaria es separar **encolar** de **sincronizar**. Después deben corregirse la ruta cloud del backfill y el modelo de estado por recorrido, porque ambos pueden explicar datos pendientes o invisibles en el servidor.
