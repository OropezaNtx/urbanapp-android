# UrbanApp Technical Audit v1

Rama auditada: `fix/maps-debug`

## Resumen ejecutivo

UrbanApp ya tiene una base funcional importante: autenticación, navegación, Room, GPS avanzado, tracking en foreground, mapa reutilizable, FOV operativo, ASD parcialmente avanzado y CC funcional. La app ya dejó de estar en etapa de prototipo simple y está entrando a una etapa de estabilización, arquitectura y preparación para demo comercial.

El objetivo inmediato no debe ser agregar muchas pantallas nuevas, sino estabilizar los núcleos compartidos y convertirlos en componentes reutilizables para ASD, FOV, CC y futuros estudios.

---

## Estado general por capa

### 1. Configuración Android

**Estado:** Funcional.

- Compose configurado.
- Firebase configurado.
- Room configurado.
- Google Maps configurado mediante `MAPS_API_KEY` en `local.properties`.
- Build exitoso en Windows después de corregir el formato de `local.properties`.

**Riesgo detectado:** Medio.

- El proyecto usa versiones muy recientes de Gradle/AGP/Kotlin. Si otro equipo abre el proyecto con Android Studio desactualizado puede aparecer conflicto de sync.
- La API Key depende de `local.properties`, por lo que cada ambiente debe configurarla correctamente.

**Acción recomendada:**

Crear `local.properties.example` o documentación clara:

```properties
sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=TU_API_KEY
```

---

### 2. Base de datos Room

**Estado:** Funcional en modo desarrollo.

La base integra entidades de ASD, CC y FOV en una sola `AppDatabase`.

**Fortalezas:**

- Modelo local unificado.
- Índices básicos para consultas importantes.
- Relación con cascada en varias entidades.
- DAO nuevo de Dashboard registrado.

**Riesgo alto:** Migraciones.

`DbProvider` usa `DEV_MODE = true` con `fallbackToDestructiveMigration()`. Esto es cómodo para desarrollo, pero peligroso para demo real o producción porque puede borrar datos locales al cambiar versión de Room.

Además, las migraciones existentes llegan a `MIGRATION_9_10`, pero la base está en versión 11. Falta formalizar `MIGRATION_10_11` si se desactiva `DEV_MODE`.

**Acción recomendada prioritaria:**

- Mantener `DEV_MODE = true` mientras seguimos desarrollando.
- Antes de demo real, crear migraciones completas y cambiar `DEV_MODE = false`.
- Agregar una pantalla/export de respaldo de base o exportaciones por módulo antes de migraciones destructivas.

---

### 3. Navegación

**Estado:** Funcional.

El `AppNavHost` ya integra:

- Login
- Signup
- Home
- ASD
- CC
- FOV
- FOV Map

**Pendiente:**

- Conectar Dashboard Operativo a Home y NavHost.
- Mejorar flujo de sesión: si Firebase ya tiene usuario logueado, saltar login.
- Agregar logout.

**Acción recomendada:**

Siguiente commit seguro:

```text
feat(home): add operational dashboard entry
```

---

### 4. Google Maps / mapa reutilizable

**Estado:** Funcional.

`UrbanMapScreen` ya soporta:

- puntos válidos
- agrupación por coordenada
- línea de ruta
- marcador de inicio
- marcador de fin
- diagnóstico si la API Key viene vacía

**Conclusión:**

El fallo del mapa no era del código, era el formato incorrecto de `local.properties` en Windows.

**Acción recomendada:**

No seguir tocando mapas FOV por ahora. El componente debe reutilizarse para ASD y CC.

---

### 5. FOV

**Estado:** Avanzado / cercano a demo.

Funcionalidades existentes:

- Creación de sesiones.
- Catálogo por POI.
- Importación desde Excel.
- Observaciones con GPS.
- Exportación CSV.
- Mapa FOV.
- Resumen de sesión con registros, rutas, GPS válido, distancia aproximada, precisión y calidad.

**Riesgo medio:** UX.

El resumen actualmente puede encimarse sobre la captura rápida. Funciona, pero requiere reorganización visual.

**Pendientes importantes:**

- Editar observaciones.
- Eliminar observaciones con confirmación.
- Exportar XLSX.
- Mejorar vista de detalle con secciones o tabs.
- Bloquear captura cuando sesión está cerrada en todos los puntos de entrada.

**Acción recomendada:**

No agregar más lógica FOV hasta corregir layout/UX de detalle.

---

### 6. ASD

**Estado:** Motor potente, visualización pendiente.

ASD ya tiene:

- Viajes.
- Eventos.
- Tracking en foreground.
- TrackPoint.
- Export CSV.
- Export GPX.
- Cálculo de distancia.
- Estado de tracking visible.
- GPS avanzado con filtros.

**Fortaleza principal:**

ASD es el módulo con mayor potencial comercial porque puede demostrar recorrido, eventos, demoras, ascensos, descensos y calidad GPS.

**Riesgo medio:** Pantalla muy cargada.

`AsdTripDetailScreen` concentra ViewModel, UI, exportaciones, cálculo de ruta, permisos, tracking y lógica de eventos en un solo archivo grande. Funciona, pero será difícil de mantener.

**Acción recomendada prioritaria:**

Crear `AsdMapScreen` reutilizando `UrbanMapScreen`.

Después dividir gradualmente:

- `AsdTripDetailScreen`
- `AsdTripSummaryCard`
- `AsdTrackingStatusCard`
- `AsdExportActions`
- `AsdEventList`

---

### 7. CC - Cierre de Circuito

**Estado:** Funcional base.

CC ya tiene:

- Sesiones.
- Eventos.
- GPS por evento.
- Export CSV.
- Export Excel.
- Cierre de sesión.

**Riesgo medio:** Menor analítica.

Falta convertir los eventos en indicadores útiles:

- intervalos entre llegadas
- intervalos entre salidas
- tiempo de vuelta
- unidades por hora
- frecuencia por sentido

**Acción recomendada:**

Después de ASD Map, crear resumen CC.

---

### 8. GPS / Tracking

**Estado:** Muy avanzado.

El proyecto ya tiene:

- `LocationProvider`
- fix rápido
- fix fresco
- fix estricto
- filtro Kalman
- rechazo de outliers
- tracking foreground
- modos ACQUIRE / TRACK / STILL

**Riesgo medio:** Duplicación conceptual.

Hay cálculos de distancia y calidad GPS distribuidos en varias zonas. Conviene centralizarlos en un módulo común.

**Acción recomendada:**

Crear:

```text
core/analytics
```

con:

- `distanceMeters()`
- `durationText()`
- `gpsQualityLabel()`
- `validGpsPercent()`
- `averageAccuracy()`

---

## Riesgos principales

### Riesgo alto

1. Migraciones destructivas en `DEV_MODE`.
2. Falta de `MIGRATION_10_11` al pasar a modo producción.
3. Posible pérdida de datos locales si se sube versión de DB sin migración.

### Riesgo medio

1. `AsdTripDetailScreen` demasiado grande.
2. Paquetes compartidos todavía viven bajo `asd`.
3. Dashboard creado pero aún no conectado a navegación.
4. Métricas de GPS duplicadas entre FOV, ASD y tracking.
5. Login sin sesión persistente/roles.

### Riesgo bajo

1. UX de FOV detalle encimada.
2. Textos de depuración visibles en mapa.
3. Falta de pulido visual para demo.

---

## Roadmap recomendado

### Bloque 1 - Estabilización inmediata

1. Conectar Dashboard a Home y NavHost.
2. Validar que Dashboard compile y muestre datos reales.
3. Documentar `local.properties.example`.
4. Corregir UX del detalle FOV para que el resumen no tape captura.

### Bloque 2 - ASD comercial

1. Crear `AsdMapScreen`.
2. Mostrar track completo.
3. Mostrar inicio y fin.
4. Mostrar eventos en mapa.
5. Calcular distancia/duración/calidad GPS.
6. Preparar resumen de viaje ASD.

### Bloque 3 - Core compartido

1. Crear `core/analytics`.
2. Mover cálculos comunes.
3. Preparar `core/location` gradualmente.
4. Preparar `core/export` gradualmente.

### Bloque 4 - Demo comercial

1. Dashboard operativo.
2. FOV estable.
3. ASD Map.
4. Exportaciones verificadas.
5. Guía de instalación.
6. Flujo de demo con datos de prueba.

---

## Próximo commit recomendado

```text
feat(home): add operational dashboard entry
```

Este commit debe:

- Agregar botón Dashboard en Home.
- Agregar ruta `dashboard` en `AppNavHost`.
- Abrir `OperationalDashboardScreen`.

Después de eso debe probarse compilación antes de continuar.
