# UrbanApp - Flujo paralelo con IA

## Objetivo

Acelerar el desarrollo usando dos roles claros:

- ChatGPT: arquitectura, cambios directos en repo, integración y decisiones de producto.
- Gemini: revisión paralela, auditoría, pruebas de criterio, alternativas y búsqueda de riesgos.

## Regla principal

Gemini no debe proponer reescrituras completas si el módulo ya funciona. Su rol es revisar, detectar riesgos y sugerir mejoras incrementales.

## Cómo usar Gemini

Pegar este contexto cuando se quiera una segunda opinión:

```text
Estoy desarrollando UrbanApp, una plataforma Android + Firebase + React para estudios de Ascenso-Descenso en transporte público.

Arquitectura:
- Android offline-first es fuente de verdad.
- Firestore solo sincroniza.
- Web solo visualiza y analiza.
- No se deben rehacer módulos existentes que ya funcionan.

Módulos existentes:
- ASD
- Demoras
- Banderas
- GPS continuo
- Room
- Firestore
- Exportadores
- Dashboard
- Playback
- Live Devices

Revisa el cambio o diseño que te comparta buscando:
1. Riesgos de arquitectura.
2. Riesgos de batería.
3. Riesgos de costo Firestore.
4. Riesgos de seguridad.
5. Riesgos de escalabilidad.
6. Mejoras incrementales sin reescribir todo.

Entrega respuesta en formato:
- Veredicto
- Riesgos
- Recomendaciones mínimas
- Recomendaciones futuras
- Qué NO tocar
```

## División de trabajo recomendada

### ChatGPT

- Hace cambios en `feature/live-operations-center`.
- Mantiene compatibilidad Android / Firebase / Web.
- Define contratos de datos.
- Decide implementación mínima viable.

### Gemini

- Revisa documentación.
- Revisa contratos de datos.
- Propone casos de prueba.
- Señala contradicciones.
- Ayuda a pensar licenciamiento, distribución y seguridad.

## Cadencia

1. ChatGPT implementa.
2. Usuario prueba.
3. Si algo es crítico, se consulta Gemini con el prompt anterior.
4. ChatGPT integra solo lo que sea útil y compatible.

## Lema

Trabajar una vez, dejar arquitectura estable y evitar deuda futura.
