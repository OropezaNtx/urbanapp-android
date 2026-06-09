# UrbanApp ASD — Demo Ready Plan

Rama de trabajo: `feature/asd-demo-ready`

## Objetivo

Dejar el módulo ASD listo para una demo funcional y profesional sin tocar `main`.

## Estado confirmado

- La rama `feature/asd-demo-ready` parte de la misma base que `main`.
- ASD ya permite crear recorridos, registrar eventos, capturar GPS, visualizar mapa y exportar información.
- El enfoque de los cambios será únicamente preparar ASD para demo.

## Prioridades de implementación

1. Mejorar la pantalla de detalle ASD para que sea más demostrable.
2. Agregar resumen operativo visible: ascensos, descensos, pasajeros a bordo, eventos y puntos GPS.
3. Conectar acceso directo al mapa desde el detalle del viaje.
4. Conectar exportación KML visible desde la UI.
5. Pulir etiquetas y textos visibles para cliente.
6. Validar flujo completo: crear viaje, registrar ascenso, registrar descenso, registrar demora, cerrar, mapa y exportaciones.

## Regla de trabajo

Todos los commits de preparación de demo deben hacerse sobre:

```bash
git checkout feature/asd-demo-ready
```

No se deben hacer commits directos en `main`.
