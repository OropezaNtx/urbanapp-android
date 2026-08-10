import React, { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  BatteryWarning,
  CheckCircle2,
  CircleDot,
  CloudCog,
  Gauge,
  MapPinned,
  RefreshCw,
  Route,
  ShieldAlert,
  Smartphone,
  UserRoundCheck,
  WifiOff,
} from 'lucide-react';
import { fetchTripDetail } from '../services/firestore';
import { buildOperationalIntelligence } from '../services/operationalIntelligence';
import './OperationalIntelligenceDashboard.css';

const MAX_TRIPS = 24;
const BATCH_SIZE = 4;

function finite(value) {
  return value !== null && value !== undefined && Number.isFinite(Number(value));
}

function pct(value, digits = 1) {
  return finite(value) ? `${Number(value).toFixed(digits)}%` : '—';
}

function score(value) {
  return finite(value) ? Number(value).toFixed(0) : '—';
}

function meters(value) {
  return finite(value) ? `${Number(value).toFixed(1)} m` : '—';
}

function km(value) {
  return finite(value) ? `${Number(value).toFixed(2)} km` : '—';
}

function stateLabel(state) {
  return ({
    HEALTHY: 'Saludable',
    DEGRADED: 'Degradado',
    CRITICAL: 'Crítico',
    RECOVERED: 'Recuperado',
    IN_PROGRESS: 'En curso',
    NOT_AVAILABLE: 'Sin telemetría',
  })[state] || state || '—';
}

function Kpi({ icon, label, value, hint, tone = 'neutral' }) {
  return <div className={`oi-kpi oi-tone-${tone}`}>
    <div className="oi-kpi-icon">{icon}</div>
    <div>
      <span>{label}</span>
      <b>{value}</b>
      {hint && <small>{hint}</small>}
    </div>
  </div>;
}

function StateBadge({ state }) {
  return <span className={`oi-state oi-state-${String(state || 'NOT_AVAILABLE').toLowerCase()}`}>{stateLabel(state)}</span>;
}

function Empty({ children }) {
  return <div className="oi-empty">{children}</div>;
}

async function loadDetails(trips, onProgress) {
  const records = [];
  for (let i = 0; i < trips.length; i += BATCH_SIZE) {
    const batch = trips.slice(i, i + BATCH_SIZE);
    const results = await Promise.all(batch.map(async (trip) => {
      try {
        const detail = await fetchTripDetail(trip.id, trip.tripId);
        return { trip, detail, error: null };
      } catch (error) {
        return { trip, detail: null, error };
      }
    }));
    records.push(...results.filter((item) => item.detail));
    onProgress?.(Math.min(trips.length, i + batch.length), trips.length);
  }
  return records;
}

function AttentionTable({ items, onOpenTrip }) {
  if (!items.length) return <Empty>No hay recorridos cerrados que requieran atención en la ventana analizada.</Empty>;
  return <div className="oi-table-wrap"><table className="oi-table">
    <thead><tr><th>Estado</th><th>Trip</th><th>Ruta</th><th>Operador</th><th>Equipo</th><th>Score</th><th>Dominio afectado</th></tr></thead>
    <tbody>{items.map((item) => {
      const domains = [
        ['GPS', item.gpsState],
        ['Red', item.networkState],
        ['Dispositivo', item.deviceState],
        ['Recovery', item.recoveryState],
        ['Sync', item.syncState],
      ].filter(([, state]) => state === 'DEGRADED' || state === 'CRITICAL').map(([name]) => name);
      return <tr key={item.id} onClick={() => onOpenTrip?.(item.trip)}>
        <td><StateBadge state={item.operationalState} /></td>
        <td><b>{item.trip?.localTripId ?? item.trip?.tripId ?? item.id}</b></td>
        <td>{item.route}</td>
        <td>{item.operator}</td>
        <td>{item.device}</td>
        <td>{score(item.healthScore)}</td>
        <td>{domains.join(', ') || '—'}</td>
      </tr>;
    })}</tbody>
  </table></div>;
}

function RankingCard({ icon, title, subtitle, rows, renderMetric }) {
  return <section className="oi-ranking-card">
    <div className="oi-ranking-head">{icon}<div><b>{title}</b><small>{subtitle}</small></div></div>
    {!rows.length ? <Empty>Sin datos suficientes.</Empty> : <div className="oi-ranking-list">
      {rows.map((row, index) => <div className="oi-ranking-row" key={row.key}>
        <span className="oi-rank">{index + 1}</span>
        <div className="oi-ranking-name"><b>{row.label}</b><small>{row.trips} recorrido(s)</small></div>
        <div className="oi-ranking-value">{renderMetric(row)}</div>
      </div>)}
    </div>}
  </section>;
}

export default function OperationalIntelligenceDashboard({ trips = [], onOpenTrip }) {
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [progress, setProgress] = useState({ done: 0, total: 0 });

  const candidates = useMemo(() => trips.slice(0, MAX_TRIPS), [trips]);

  async function audit() {
    if (!candidates.length) {
      setReport(buildOperationalIntelligence([]));
      return;
    }
    setLoading(true);
    setError('');
    setProgress({ done: 0, total: candidates.length });
    try {
      const records = await loadDetails(candidates, (done, total) => setProgress({ done, total }));
      setReport(buildOperationalIntelligence(records));
    } catch (e) {
      setError(e?.message || String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { audit(); }, [candidates]);

  const summary = report?.summary || {};
  const today = report?.today || {};
  const rankings = report?.rankings || {};

  return <div className="oi-dashboard">
    <section className="oi-hero">
      <div>
        <span className="oi-eyebrow">Operational Intelligence · Engine {report?.version || '3.3.0'}</span>
        <h2>Inteligencia Operativa</h2>
        <p>Consolida integridad, GPS, telemetría, recovery y sincronización sin alterar las métricas fuente.</p>
      </div>
      <button onClick={audit} disabled={loading}><RefreshCw size={16} className={loading ? 'oi-spin' : ''} />{loading ? `Analizando ${progress.done}/${progress.total}` : 'Reanalizar'}</button>
    </section>

    {error && <div className="error">{error}</div>}

    <section className="oi-kpis">
      <Kpi icon={<ShieldAlert size={19}/>} label="Fallidos hoy" value={today.failed?.length ?? 0} hint="Cerrados en estado CRITICAL" tone={(today.failed?.length ?? 0) ? 'critical' : 'healthy'} />
      <Kpi icon={<AlertTriangle size={19}/>} label="Degradados hoy" value={today.degraded?.length ?? 0} hint="Requieren revisión, datos disponibles" tone={(today.degraded?.length ?? 0) ? 'warning' : 'healthy'} />
      <Kpi icon={<CheckCircle2 size={19}/>} label="Recuperados hoy" value={today.recovered?.length ?? 0} hint="Incidente resuelto por recovery" tone="recovered" />
      <Kpi icon={<CircleDot size={19}/>} label="En curso hoy" value={today.inProgress?.length ?? 0} hint="Evaluación todavía provisional" tone="progress" />
      <Kpi icon={<Gauge size={19}/>} label="Health promedio" value={finite(summary.averageHealthScore) ? `${score(summary.averageHealthScore)}/100` : '—'} hint={`${summary.evaluable ?? 0} recorridos evaluables`} tone="neutral" />
      <Kpi icon={<MapPinned size={19}/>} label="Cobertura GPS promedio" value={pct(summary.averageGpsCoveragePct, 1)} hint="Recorridos con GPS disponible" tone="neutral" />
      <Kpi icon={<Activity size={19}/>} label="Telemetría disponible" value={pct(summary.telemetryCoveragePct, 1)} hint="Sobre recorridos cerrados analizados" tone="neutral" />
      <Kpi icon={<CloudCog size={19}/>} label="Ventana analizada" value={summary.total ?? 0} hint={`Máximo ${MAX_TRIPS} recorridos recientes`} tone="neutral" />
    </section>

    <section className="oi-section-card">
      <div className="oi-section-head">
        <div><h3><AlertTriangle size={18}/> Requieren atención</h3><p>Recorridos cerrados con Health DEGRADED o CRITICAL. Haz clic para abrir el detalle original.</p></div>
        <span>{report?.attention?.length ?? 0} caso(s)</span>
      </div>
      <AttentionTable items={report?.attention || []} onOpenTrip={onOpenTrip} />
    </section>

    <div className="oi-ranking-grid">
      <RankingCard
        icon={<Smartphone size={18}/>}
        title="Dispositivos con peor GPS"
        subtitle="Prioriza cobertura; usa precisión como desempate."
        rows={rankings.poorGpsDevices || []}
        renderMetric={(row) => <><b>{pct(row.averageGpsCoveragePct, 1)}</b><small>{meters(row.averageAccuracyM)}</small></>}
      />
      <RankingCard
        icon={<WifiOff size={18}/>}
        title="Problemas de red por dispositivo"
        subtitle="Cantidad de recorridos degradados por conectividad."
        rows={rankings.networkDevices || []}
        renderMetric={(row) => <><b>{row.networkDegradedTrips}</b><small>degradado(s)</small></>}
      />
      <RankingCard
        icon={<Route size={18}/>}
        title="Rutas con mayor carga de eventos"
        subtitle="Eventos por km cuando existe distancia analítica."
        rows={rankings.eventHeavyRoutes || []}
        renderMetric={(row) => <><b>{finite(row.eventsPerKm) ? row.eventsPerKm.toFixed(2) : row.eventCount}</b><small>{row.incidents} incidencias · {km(row.distanceKm)}</small></>}
      />
      <RankingCard
        icon={<UserRoundCheck size={18}/>}
        title="Calidad por operador"
        subtitle="Health promedio y cobertura GPS agregados."
        rows={rankings.operatorQuality || []}
        renderMetric={(row) => <><b>{finite(row.averageHealthScore) ? `${score(row.averageHealthScore)}/100` : '—'}</b><small>GPS {pct(row.averageGpsCoveragePct, 1)}</small></>}
      />
      <RankingCard
        icon={<BatteryWarning size={18}/>}
        title="Equipos con recoveries"
        subtitle="Recuperaciones confirmadas del tracking."
        rows={rankings.recoveryDevices || []}
        renderMetric={(row) => <><b>{row.recoveries}</b><small>recovery(s)</small></>}
      />
    </div>

    <section className="oi-section-card oi-methodology">
      <h3>Cómo leer este panel</h3>
      <p><b>IN_PROGRESS</b> no equivale a fallo. Los recorridos abiertos no se clasifican como críticos por información todavía pendiente. Los históricos sin Telemetry v1 siguen aportando GPS y eventos, pero no reciben métricas inventadas de batería, red o recovery.</p>
    </section>
  </div>;
}
