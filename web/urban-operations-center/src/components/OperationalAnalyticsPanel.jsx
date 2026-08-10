import React from 'react';
import { Activity, Clock3, Gauge, MapPinned, Radio, Smartphone, TimerReset, WifiOff } from 'lucide-react';
import './OperationalAnalyticsPanel.css';

function finite(value) {
  return Number.isFinite(Number(value));
}

function fmtNumber(value, digits = 1, suffix = '') {
  return finite(value) ? `${Number(value).toFixed(digits)}${suffix}` : 'No disponible todavía';
}

function fmtDuration(ms) {
  if (!finite(ms)) return 'No disponible todavía';
  const totalSec = Math.max(0, Math.round(Number(ms) / 1000));
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h) return `${h}h ${m}m ${s}s`;
  if (m) return `${m}m ${s}s`;
  return `${s}s`;
}

function fmtBytes(bytes) {
  if (!finite(bytes)) return 'No disponible todavía';
  const value = Number(bytes);
  if (value < 1024) return `${value} B`;
  if (value < 1024 ** 2) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 ** 2).toFixed(2)} MB`;
}

function availabilityMetric(metric) {
  if (!metric || metric.availability === 'NOT_AVAILABLE') return 'No disponible todavía';
  return metric.value ?? 'No disponible todavía';
}

function Metric({ label, value, hint }) {
  return <div className="ops-metric">
    <span>{label}</span>
    <b>{value}</b>
    {hint && <small>{hint}</small>}
  </div>;
}

function Section({ icon, title, children }) {
  return <div className="ops-section">
    <div className="ops-section-title">{icon}<b>{title}</b></div>
    <div className="ops-metrics-grid">{children}</div>
  </div>;
}

export default function OperationalAnalyticsPanel({ analytics }) {
  if (!analytics) return null;

  const gps = analytics.gps || {};
  const trip = analytics.trip || {};
  const events = analytics.events || {};
  const operator = analytics.operator || {};
  const device = analytics.device || {};
  const sync = analytics.sync || {};

  return <section className="card ops-analytics-card">
    <div className="ops-head">
      <div>
        <h3><Activity size={18} /> Operational Analytics</h3>
        <p>Métricas calculadas sobre el recorrido sincronizado. Se conservan los datos raw y se separan las correcciones analíticas para mantener trazabilidad.</p>
      </div>
      <span className="ops-version">Engine {analytics.version}</span>
    </div>

    <div className="ops-layout">
      <Section icon={<Radio size={17} />} title="GPS">
        <Metric label="Pérdidas de señal inferidas" value={gps.lossEvents ?? 0} hint={`Gap > ${fmtDuration(gps.gapThresholdMs)}`} />
        <Metric label="Precisión promedio" value={fmtNumber(gps.averageAccuracyM, 1, ' m')} />
        <Metric label="Mejor precisión" value={fmtNumber(gps.minAccuracyM, 1, ' m')} />
        <Metric label="Peor precisión" value={fmtNumber(gps.maxAccuracyM, 1, ' m')} />
        <Metric label="Tiempo sin GPS inferido" value={fmtDuration(gps.noGpsMs)} />
        <Metric label="Cobertura GPS" value={fmtNumber(gps.coveragePct, 2, '%')} />
        <Metric label="Intervalo mediano" value={fmtDuration(gps.medianIntervalMs)} hint="Cadencia observada" />
        <Metric label="Puntos utilizables" value={gps.points ?? 0} />
        <Metric label="Segmentos totales" value={gps.totalSegments ?? 0} />
        <Metric label="Segmentos aceptados" value={gps.acceptedSegments ?? 0} />
        <Metric label="Segmentos atípicos" value={gps.rejectedSegments ?? 0} hint="Conservados en datos; excluidos de cinemática" />
        <Metric label="Tasa de outliers" value={fmtNumber(gps.outlierRatePct, 2, '%')} hint="Atípicos / segmentos totales" />
        <Metric label="Atípicos por NO_FIX" value={gps.rejectedNoFixOnlySegments ?? 0} />
        <Metric label="Atípicos por velocidad extrema" value={gps.rejectedImpossibleSpeedOnlySegments ?? 0} hint="> 160 km/h" />
        <Metric label="Atípicos por ambas causas" value={gps.rejectedBothSegments ?? 0} />
        <Metric label="Segmentos sospechosos Android" value={gps.engineSuspectSegments ?? 0} hint="Diagnóstico; no se eliminan automáticamente" />
      </Section>

      <Section icon={<MapPinned size={17} />} title="Recorrido">
        <Metric label="Duración" value={fmtDuration(trip.durationMs)} />
        <Metric label="Distancia analítica" value={fmtNumber(trip.distanceKm, 3, ' km')} />
        <Metric label="Distancia raw" value={fmtNumber(trip.rawDistanceKm, 3, ' km')} />
        <Metric label="Ajuste por outliers" value={fmtNumber(trip.distanceAdjustmentKm, 3, ' km')} />
        <Metric label="Corrección geométrica" value={fmtNumber(trip.distanceCorrectionPct, 2, '%')} hint="Raw - segmentos físicamente imposibles" />
        <Metric label="Velocidad promedio" value={fmtNumber(trip.averageSpeedKmh, 1, ' km/h')} />
        <Metric label="Máxima observada" value={fmtNumber(trip.maxObservedSpeedKmh ?? trip.maxSpeedKmh, 1, ' km/h')} hint="Mayor segmento analítico válido" />
        <Metric label="Máxima sostenida" value={fmtNumber(trip.maxSustainedSpeedKmh, 1, ' km/h')} hint={`Ventana continua ≥ ${fmtDuration(trip.sustainedSpeedWindowMs)}`} />
        <Metric label="Velocidad P95" value={fmtNumber(trip.p95SpeedKmh, 1, ' km/h')} hint="95% de los segmentos válidos están por debajo" />
        <Metric label="Velocidad mínima" value={fmtNumber(trip.minSpeedKmh, 1, ' km/h')} hint="Solo segmentos analíticos válidos" />
      </Section>

      <Section icon={<Gauge size={17} />} title="Eventos">
        <Metric label="Eventos" value={events.total ?? 0} />
        <Metric label="Demoras" value={events.delays ?? 0} hint="DEMORA/DELAY o códigos no operativos" />
        <Metric label="Demoras explícitas" value={events.explicitDelays ?? 0} />
        <Metric label="Banderas" value={events.flags ?? 0} />
        <Metric label="Con código de demora real" value={events.codedDelayEvents ?? 0} hint="Excluye AD, INICIO y FINAL" />
        <Metric label="Incidencias" value={events.incidents ?? 0} hint="Unión de banderas y demoras" />
        <Metric label="Con observaciones" value={events.observations ?? 0} />
        <Metric label="Eventos por km" value={fmtNumber(events.eventsPerKm, 2)} />
        <Metric label="Eventos por minuto" value={fmtNumber(events.eventsPerMinute, 3)} />
      </Section>

      <Section icon={<TimerReset size={17} />} title="Dinámica del recorrido">
        <Metric label="Tiempo en movimiento" value={fmtDuration(operator.movingMs)} hint="Segmentos analíticos > 2 km/h" />
        <Metric label="Tiempo detenido" value={fmtDuration(operator.stoppedMs)} hint="Solo tiempo con GPS observado" />
        <Metric label="Tiempo no observado" value={fmtDuration(operator.unobservedMs)} hint="Huecos GPS inferidos" />
        <Metric label="Porcentaje en movimiento" value={fmtNumber(operator.movementRatioPct, 2, '%')} hint="No es una calificación del operador" />
        <Metric label="Cobertura GPS" value={fmtNumber(operator.gpsCoveragePct, 2, '%')} />
      </Section>

      <Section icon={<Smartphone size={17} />} title="Calidad del dispositivo">
        <Metric label="Cobertura GPS" value={fmtNumber(device.gpsCoveragePct, 2, '%')} />
        <Metric label="Precisión promedio" value={fmtNumber(device.averageAccuracyM, 1, ' m')} />
        <Metric label="Batería histórica" value={availabilityMetric(device.battery)} hint="Requiere instrumentación por recorrido" />
        <Metric label="Heartbeat histórico" value={availabilityMetric(device.heartbeat)} hint="Requiere persistencia temporal" />
        <Metric label="Reconexiones" value={availabilityMetric(device.reconnections)} hint="Requiere contador por recorrido" />
      </Section>

      <Section icon={<Clock3 size={17} />} title="Calidad de sincronización">
        <Metric label="Payload estimado" value={fmtBytes(sync.payloadBytesEstimated)} hint="Trip + eventos + chunks recibidos" />
        <Metric label="Tiempo de subida" value={availabilityMetric(sync.uploadDurationMs)} hint="Aún no persistido" />
        <Metric label="Tiempo cloud" value={availabilityMetric(sync.cloudDurationMs)} hint="Aún no persistido" />
        <Metric label="Tiempo análisis web" value={fmtNumber(sync.webAnalysisMs, 3, ' ms')} />
      </Section>
    </div>

    <div className="ops-note"><WifiOff size={16} /> Las pérdidas de señal son inferidas mediante huecos temporales entre fixes GPS. Máxima observada conserva el pico válido; máxima sostenida exige continuidad temporal y P95 describe el rango alto habitual.</div>
  </section>;
}
