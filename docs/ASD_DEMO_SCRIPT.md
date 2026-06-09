# UrbanApp ASD — Guion rápido de demo

Rama: `feature/asd-demo-ready`

## Objetivo de la demo

Mostrar que UrbanApp ASD permite levantar un recorrido real de Ascensos y Descensos con:

- Encabezado del recorrido.
- Tracking GPS continuo.
- Registro de ascensos, descensos y demoras.
- Conteo operativo en tiempo real.
- Mapa del recorrido.
- Exportación CSV, KML, GPX y trackpoints.

## Flujo recomendado para presentar

### 1. Abrir módulo ASD

Desde Home:

1. Entrar a ASD.
2. Mostrar lista de viajes.
3. Explicar que cada viaje representa un recorrido operativo.

Frase sugerida:

> Aquí concentramos los recorridos ASD. Cada recorrido guarda encabezado, eventos, GPS, ruta y exportaciones para análisis posterior.

## 2. Crear recorrido

1. Presionar `+`.
2. Capturar datos mínimos:
   - ID Planeación.
   - Ruta / Derrotero.
   - Empresa.
   - Eco / placa si aplica.
   - Sentido IDA o REGRESO.
3. Presionar `Crear recorrido`.

Frase sugerida:

> Al iniciar el recorrido, la app intenta capturar ubicación GPS. Si el GPS todavía no está listo, no bloquea la operación; el tracking continúa registrando puntos cuando la señal mejora.

## 3. Mostrar pantalla detalle

En la pantalla detalle, mostrar:

- Estado del viaje.
- Resumen operativo.
- Estado de tracking.
- Distancia del recorrido.
- Acciones de demo.
- Exportaciones.
- Eventos registrados.

Frase sugerida:

> Esta pantalla funciona como centro operativo del recorrido. El supervisor puede ver en tiempo real cuántos eventos van, cuántos ascensos, descensos, pasajeros a bordo, demoras y puntos GPS registrados.

## 4. Registrar evento

Presionar `+ Registrar evento` y capturar:

### Evento de ascenso

- Tipo: ASCENSO.
- Hombres: 1 o más.
- Mujeres: 0 o más.
- Parada / referencia opcional.
- Guardar.

### Evento de descenso

- Tipo: DESCENSO.
- Hombres o mujeres.
- Guardar.

### Evento de demora

- Tipo: DEMORA.
- Seleccionar código de demora.
- Agregar observación si aplica.
- Guardar.

Frase sugerida:

> Cada evento guarda punto IN y punto OUT, lo que permite saber dónde se detuvo la unidad y dónde retomó el recorrido.

## 5. Ver mapa

Presionar `Ver mapa del recorrido`.

Mostrar:

- Ruta GPS.
- Marcadores de eventos.
- Inicio y fin.
- Resumen sobre el mapa.

Frase sugerida:

> El mapa permite validar visualmente si el recorrido fue consistente y dónde ocurrieron los eventos registrados.

## 6. Cerrar viaje

Presionar `Cerrar viaje`.

Frase sugerida:

> Al cerrar, el viaje queda finalizado y se detiene el tracking para evitar registrar puntos innecesarios.

## 7. Exportar resultados

Mostrar los botones:

- Exportar CSV final.
- Exportar KML.
- Exportar GPX.
- Exportar TRACK CSV.

Frase sugerida:

> La información queda lista para análisis externo. CSV para base de datos o Excel, KML para Google Earth, GPX para software GPS y trackpoints para auditoría detallada del recorrido.

## Checklist antes de presentar

- [ ] Instalar app en dispositivo físico.
- [ ] Dar permisos de ubicación.
- [ ] Probar crear un recorrido.
- [ ] Registrar mínimo un ascenso.
- [ ] Registrar mínimo un descenso.
- [ ] Registrar mínimo una demora.
- [ ] Abrir mapa y validar que no esté vacío.
- [ ] Exportar CSV final.
- [ ] Exportar KML.
- [ ] Exportar GPX.
- [ ] Cerrar viaje correctamente.

## Riesgos conocidos para mencionar solo si preguntan

- En interiores puede tardar el GPS en estabilizarse.
- Si no hay señal GPS, la app no bloquea la captura; registra el evento y marca el estado de ubicación.
- Para mejor demo, hacer la prueba en exterior o cerca de ventana.
