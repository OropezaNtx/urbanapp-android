# Urban Operations Center MVP

Panel web mínimo para leer Firestore real de UrbanApp.

## Colecciones usadas

- `asd_trips`
- `asd_trips/{tripDocId}/events`
- `asd_trips/{tripDocId}/track_summary`
- `urbanapp_asd_backups` como fallback para eventos con documentos `trip_{tripId}_event_{eventId}`
- `asd_devices`

## Ejecutar

```cmd
cd C:\Users\phile\StudioProjects\urbanapp-android\web\urban-operations-center
copy .env.example .env
notepad .env
npm install
npm run dev
```

## CSV

El botón CSV eventos descarga eventos combinados con encabezado del viaje.

## Campos de viaje contemplados

tripId, routeName, routeNumber, planningRouteId, direction, company, aforador, supervisor, deviceNumber, vehicleEco, plateNumber, vehicleType, seatCapacity, baseStart, baseEnd, startTime, endTime, esFs, nextWaypointId, observerSex, notes.

## Campos de eventos contemplados

backupCreatedAt, backupSource, delayCodes, eventId, hasLuggage, locationStatus, notes, otherDelayDesc, paxMenDown, paxMenUp, paxWomenDown, paxWomenUp, startAccM, startLat, startLon, startProvider, startTime, stopAccM, stopLat, stopLon, stopName, stopProvider, stopTime, stopType, timestamp, tripId, uid, waypointStartId, waypointStopId.
