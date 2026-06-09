# UrbanApp ASD — Checklist de validación en campo

Rama: `feature/asd-demo-ready`

## Estado actual

ASD ya cuenta con:

- Captura visible de ascensos, descensos y demoras.
- Registro sin bloqueo por GPS perfecto.
- Backfill automático de coordenadas pendientes.
- Tracking GPS cada 2 segundos.
- Mapa ASD con botón `Ubicarme`.
- Exportación CSV, GPX y KML.
- Regla automática de demora `AD` cuando hay ascenso o descenso.
- Columnas `Total suben`, `Total bajan` y `Total a bordo` en CSV.
- Columnas de auditoría GPS en CSV.

## Reglas operativas confirmadas

### 1. Registro ASD

Un punto puede contener al mismo tiempo:

- Personas que suben.
- Personas que bajan.
- Una o varias demoras.
- Observaciones.
- Referencia de parada.
- Maleta o bulto voluminoso.

La app debe interpretarlo como un solo punto operativo.

### 2. Demora AD automática

Siempre que el registro tenga al menos una subida o bajada:

```text
Total suben + Total bajan > 0
```

la app debe agregar automáticamente:

```text
AD
```

Si el usuario además marca otra demora, el resultado debe quedar combinado:

```text
AD/C
AD/S
AD/TM
AD/CND
AD/O
```

### 3. GPS no bloqueante

Al guardar un evento:

- Si existe último punto GPS válido, se usa ese punto.
- Si no existe punto válido, el evento se guarda con `GPS_PENDING`.
- Cuando el tracking consiga un punto válido, el servicio completa la coordenada con `GPS_BACKFILLED`.

### 4. Tracking

El tracking debe guardar puntos cada 2 segundos aproximadamente:

```text
maxSaveIntervalMs = 2_000L
```

Esto permite reconstruir mejor el recorrido y auditar la ruta.

## Prueba mínima antes de demo

### Flujo 1 — Ascenso simple

1. Crear viaje ASD.
2. Registrar `1 hombre sube`.
3. Guardar.
4. Confirmar en eventos:
   - Suben: 1.
   - Bajan: 0.
   - Demora: AD.

### Flujo 2 — Descenso simple

1. Registrar `1 hombre baja`.
2. Guardar.
3. Confirmar:
   - Suben: 0.
   - Bajan: 1.
   - Demora: AD.

### Flujo 3 — Evento combinado

1. Registrar:
   - 1 sube.
   - 1 baja.
   - Demora C.
2. Guardar.
3. Confirmar:
   - Demora: AD/C.
   - Total suben: 1.
   - Total bajan: 1.

### Flujo 4 — Solo demora

1. Registrar demora `TM` sin pasajeros.
2. Guardar.
3. Confirmar:
   - Demora: TM.
   - Total suben: 0.
   - Total bajan: 0.

### Flujo 5 — GPS pendiente

1. Guardar evento sin señal GPS fuerte.
2. Confirmar que se guarda sin bloquear.
3. Esperar nuevo punto de tracking.
4. Confirmar que el evento cambia a `GPS_BACKFILLED` en exportación.

## Exportación CSV esperada

El CSV final debe incluir:

- Pax. Hombres Suben.
- Pax. Mujeres Suben.
- Pax. Hombres bajan.
- Pax. Mujeres bajan.
- Total suben.
- Total bajan.
- Total a bordo.
- Tipo de demora.
- Observaciones.
- GPS Status.
- GPS Accuracy m.
- GPS Provider.
- GPS Fix Time.

## Recomendación de siguiente afinación

Siguiente cambio visual recomendado:

- Mostrar en el bloque de captura los totales antes de guardar:
  - Total suben.
  - Total bajan.
  - A bordo estimado.
- Mostrar aviso visual:

```text
AD se agregará automáticamente si hay ascenso o descenso.
```

Esto reduce errores de captura y ayuda a que el usuario entienda la regla operativa.
