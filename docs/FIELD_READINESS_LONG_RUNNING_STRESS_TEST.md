# 3.3.5B — Long-running Tracking Stress Test

## Objetivo

Certificar que una jornada prolongada de tracking ASD puede ejecutarse sin pérdida de datos y sin intervención técnica del operador.

La prueba NO modifica el comportamiento de producción. Usa la instrumentación existente de TrackingService, TrackingRecoveryWorker, TripTelemetry, CloudSyncIntegrity y DataIntegrity.

## Principios

- Android/Room sigue siendo la fuente de verdad.
- Internet no es requisito para capturar.
- La prueba debe validar captura local, resiliencia, sincronización eventual y completitud Cloud/Web.
- Un problema de red no equivale a pérdida de datos.
- Toda incidencia debe poder explicarse con evidencia observable.

## Duración objetivo

### Smoke endurance
- 45–60 minutos.
- Sirve para validar el protocolo y los filtros de evidencia.

### Field endurance
- 4 horas continuas como mínimo.
- Objetivo recomendado antes de liberar a campo: 6–8 horas acumuladas en una misma versión.

## Escenario de prueba

Durante un solo recorrido:

1. Iniciar con condiciones normales.
2. Mantener tracking con la app en foreground durante al menos 10 minutos.
3. Bloquear la pantalla durante al menos 20 minutos.
4. Desbloquear y comprobar que el recorrido continúa.
5. Mandar la app a background y usar otras aplicaciones durante al menos 15 minutos.
6. Realizar desplazamiento real durante parte de la prueba.
7. Desactivar Wi-Fi/datos móviles durante al menos un ciclo de watchdog (> 2 minutos).
8. Restaurar conectividad y permitir recuperación/sync.
9. Mantener la prueba hasta completar la duración objetivo.
10. Finalizar el recorrido desde la app.
11. Esperar a que la cola de sincronización alcance outstanding=0.
12. Ejecutar/observar Data Audit y validar Cloud/Web Completeness.

## Evidencia obligatoria

### Tracking
- TRACKING / heartbeat continúa activo durante la jornada.
- WATCHDOG_HEALTHY aparece periódicamente.
- No existe WATCHDOG_DISARMED inesperado durante un trip abierto.
- Si existe recovery, queda explícitamente registrado y posteriormente confirmado.

### Telemetry
- TELEMETRY_SAMPLE continúa durante la prueba.
- Battery samples > 0.
- Heartbeat samples > 0.
- Network samples > 0.
- El estado final contiene finished=true.
- TELEMETRY_FINALIZED se emite al cerrar.

### Red
- Si se fuerza pérdida de Internet: network=NONE connected=false.
- Al recuperar red: connected=true y reconnectionCount aumenta.
- offlineDurationMs conserva la evidencia del periodo sin red.

### Sync
- La captura continúa aunque no exista Internet.
- Los elementos pendientes se sincronizan al recuperar conectividad.
- Debe existir SYNC_FINISHED result=SUCCESS.
- outstanding=0 al finalizar la ventana de recuperación.

### Room / Cloud
- Trip local cerrado correctamente.
- Conteo de eventos local == conteo Cloud.
- Conteo esperado de chunks == chunks Cloud.
- Puntos Room == puntos Cloud raw.
- pointDelta=0.
- mismatches=0.

## Criterios de aceptación

La prueba es PASS cuando:

- El trip no se pierde.
- El tracking no se detiene silenciosamente.
- Todos los eventos capturados localmente siguen disponibles.
- No faltan chunks.
- No existe delta de puntos inexplicado.
- La pérdida temporal de conectividad no detiene la captura.
- La sincronización converge eventualmente a outstanding=0.
- Cloud Completeness termina COMPLETE.
- Web Completeness termina WEB_COMPLETE.
- Data Audit termina mismatches=0.

Una recuperación confirmada NO falla automáticamente la prueba si:

- fue detectada,
- el tracking se restauró,
- no existe pérdida de datos,
- y Cloud/Web terminan completos.

## Criterios de fallo

FAIL si ocurre cualquiera de los siguientes:

- Trip desaparecido o imposible de cerrar.
- Tracking detenido sin diagnóstico/recovery.
- Evento local perdido.
- Chunk faltante.
- pointDelta distinto de cero sin explicación.
- outstanding no converge después de restaurar conectividad.
- Data Audit reporta mismatches > 0.
- Cloud/Web Completeness no alcanza estado completo tras finalizar y sincronizar.

## Evidencia final a conservar

- tripId.
- hora inicio/fin.
- duración aproximada.
- batería inicio/fin/mínima.
- cobertura y tiempo offline.
- reconexiones.
- watchdog healthy count.
- recoveries / assisted recoveries / FGS blocked.
- sync retries/failures/confirmed items.
- puntos Room.
- eventos Room.
- chunks Cloud.
- puntos Cloud.
- resultado Data Audit.
- resultado Cloud Completeness.
- resultado Web Completeness.
- Operational Health final.

## Resultado de certificación

Formato recomendado:

```text
FIELD_STRESS_RESULT
trip=<id>
duration=<hh:mm:ss>
tracking=PASS
background=PASS
screen_locked=PASS
network_recovery=PASS
room_integrity=PASS
cloud_sync=PASS
data_audit=PASS
web_complete=PASS
result=PASS
```
