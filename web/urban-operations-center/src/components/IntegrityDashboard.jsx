import React, { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, RefreshCw, ShieldCheck } from 'lucide-react';
import { fetchTripDetail } from '../services/firestore';
import './IntegrityDashboard.css';

const MAX_AUDIT_TRIPS = 12;

const val = (v) => v === null || v === undefined || v === '' ? '—' : String(v);

function toMillis(v) {
  if (!v) return 0;
  if (typeof v === 'number') return v;
  if (v?.toMillis) return v.toMillis();
  if (v?.seconds) return v.seconds * 1000;
  const parsed = new Date(v).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function fmt(v) {
  const ms = toMillis(v);
  if (!ms) return '—';
  return new Date(ms).toLocaleString('es-MX');
}

function isClosed(trip) {
  const status = String(trip?.status || '').toUpperCase();
  if (status === 'CLOSED') return true;
  if (status === 'ACTIVE') return false;
  return Boolean(trip?.endTime);
}

function qualityKind(state) {
  if (state === 'VERIFIED') return 'ok';
  if (state === 'WARNING') return 'warning';
  if (state === 'CRITICAL') return 'error';
  return 'neutral';
}

function severity(row) {
  if (row.error) return 'error';
  if (row.quality?.primaryState === 'CRITICAL') return 'error';
  if (row.quality?.primaryState === 'WARNING') return isClosed(row.trip) ? 'warning' : 'pending';
  if (!isClosed(row.trip) || row.cloud?.state === 'SYNC_PENDING' || row.web?.state === 'CLOUD_INCOMPLETE') return 'pending';
  return 'ok';
}

function StatusBadge({ state, kind = 'neutral' }) {
  return <span className={`integrity-badge integrity-${kind}`}>{val(state)}</span>;
}

function QualityStates({ quality }) {
  if (!quality?.states?.length) return <span>—</span>;
  return <div className="quality-state-stack">
    {quality.states.map((state) => <StatusBadge key={state} state={state} kind={qualityKind(state)} />)}
  </div>;
}

function HealthStat({ label, value, hint, tone = 'neutral' }) {
  return <div className={`integrity-stat integrity-stat-${tone}`}>
    <span>{label}</span>
    <b>{value}</b>
    {hint && <small>{hint}</small>}
  </div>;
}

async function auditTrip(trip) {
  try {
    const detail = await fetchTripDetail(trip.id, trip.tripId);
    return {
      trip,
      cloud: detail?.completeness || null,
      web: detail?.webCompleteness || null,
      quality: detail?.quality || null,
      auditedAt: Date.now(),
      error: null,
    };
  } catch (error) {
    return {
      trip,
      cloud: null,
      web: null,
      quality: null,
      auditedAt: Date.now(),
      error: error?.message || String(error),
    };
  }
}

export default function IntegrityDashboard({ trips = [], onOpenTrip }) {
  const [rows, setRows] = useState([]);
  const [auditing, setAuditing] = useState(false);
  const [lastAuditAt, setLastAuditAt] = useState(null);

  const candidates = useMemo(
    () => [...trips]
      .sort((a, b) => toMillis(b.startTime) - toMillis(a.startTime))
      .slice(0, MAX_AUDIT_TRIPS),
    [trips]
  );

  async function runAudit() {
    if (!candidates.length || auditing) return;
    setAuditing(true);
    try {
      const results = [];
      for (const trip of candidates) {
        results.push(await auditTrip(trip));
      }
      setRows(results);
      setLastAuditAt(Date.now());
    } finally {
      setAuditing(false);
    }
  }

  useEffect(() => {
    runAudit();
    // La reauditoría posterior es explícita con el botón para controlar lecturas cloud.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [candidates.map((t) => t.id).join('|')]);

  const summary = useMemo(() => {
    const audited = rows.length;
    const complete = rows.filter((r) => r.web?.complete === true).length;
    const verified = rows.filter((r) => r.quality?.primaryState === 'VERIFIED').length;
    const pending = rows.filter((r) => severity(r) === 'pending').length;
    const issues = rows.filter((r) => ['warning', 'error'].includes(severity(r))).length;
    const filteredGps = rows.reduce((sum, r) => sum + Number(r.cloud?.invalidGpsPointCount || 0), 0);
    const rawPoints = rows.reduce((sum, r) => sum + Number(r.cloud?.rawPointCount || 0), 0);
    const scored = rows.filter((r) => Number.isFinite(Number(r.quality?.score)));
    const avgScore = scored.length
      ? Math.round(scored.reduce((sum, r) => sum + Number(r.quality.score), 0) / scored.length)
      : 0;
    return { audited, complete, verified, pending, issues, filteredGps, rawPoints, avgScore };
  }, [rows]);

  const systemState = summary.issues > 0
    ? 'ATTENTION_REQUIRED'
    : summary.pending > 0
      ? 'SYNC_IN_PROGRESS'
      : summary.audited > 0 && summary.verified === summary.audited
        ? 'HEALTHY'
        : 'WAITING_FOR_AUDIT';

  return <div className="integrity-dashboard">
    <section className="integrity-hero">
      <div>
        <div className="integrity-title"><ShieldCheck size={22} /><span>Integrity Dashboard</span></div>
        <p>Salud extremo a extremo enriquecida con Quality Engine 3.1: integridad, score y observaciones no destructivas.</p>
      </div>
      <div className="integrity-actions">
        <StatusBadge
          state={systemState}
          kind={systemState === 'HEALTHY' ? 'ok' : systemState === 'ATTENTION_REQUIRED' ? 'error' : 'pending'}
        />
        <button onClick={runAudit} disabled={auditing || !candidates.length}>
          <RefreshCw size={16} className={auditing ? 'integrity-spin' : ''} />
          {auditing ? 'Auditando...' : 'Reauditar'}
        </button>
      </div>
    </section>

    <section className="integrity-summary-grid quality-summary-grid">
      <HealthStat label="Auditados" value={summary.audited} hint={`de ${candidates.length} recientes`} />
      <HealthStat label="Verified" value={summary.verified} hint="Quality Engine" tone="ok" />
      <HealthStat label="Quality promedio" value={`${summary.avgScore}/100`} hint="Score explicable" tone={summary.avgScore >= 90 ? 'ok' : 'warning'} />
      <HealthStat label="Web completos" value={summary.complete} hint="WEB_COMPLETE" tone="ok" />
      <HealthStat label="En proceso" value={summary.pending} hint="Activos / sync pendiente" tone="pending" />
      <HealthStat label="Incidencias" value={summary.issues} hint="WARNING / CRITICAL" tone={summary.issues ? 'error' : 'ok'} />
      <HealthStat label="Puntos cloud" value={summary.rawPoints} hint="Raw confirmados" />
      <HealthStat label="Auto-cleaned" value={summary.filteredGps} hint="GPS inválido conservado en cloud" tone="ok" />
    </section>

    <section className="card integrity-card">
      <div className="integrity-card-head">
        <div>
          <h3><ShieldCheck size={18} /> Recorridos auditados</h3>
          <span>Cloud y Web se conservan intactos; Quality Engine añade score, estados y observaciones.</span>
        </div>
        <small>Última auditoría: {lastAuditAt ? fmt(lastAuditAt) : '—'}</small>
      </div>

      {!rows.length && !auditing && <div className="empty">No hay recorridos disponibles para auditar.</div>}
      {auditing && !rows.length && <div className="empty">Ejecutando auditoría de integridad...</div>}

      {!!rows.length && <div className="table integrity-table"><table>
        <thead><tr>
          <th>Trip</th>
          <th>Ruta</th>
          <th>Operación</th>
          <th>Cloud</th>
          <th>Web</th>
          <th>Quality</th>
          <th>Score</th>
          <th>Eventos</th>
          <th>Chunks</th>
          <th>Puntos raw</th>
          <th>GPS útiles</th>
          <th>Auto-cleaned</th>
          <th>Delta</th>
          <th>Resultado</th>
        </tr></thead>
        <tbody>{rows.map((row) => {
          const level = severity(row);
          const observations = row.quality?.observations?.map((o) => o.code) || [];
          const issues = row.error
            ? row.error
            : row.quality?.failedChecks > 0
              ? `${row.quality.failedChecks} CHECK(S) FAILED`
              : observations.length
                ? observations.join(' · ')
                : 'VERIFIED';
          return <tr key={row.trip.id} className={`integrity-row integrity-row-${level}`} onClick={() => onOpenTrip?.(row.trip)}>
            <td><b>{val(row.trip.localTripId ?? row.trip.tripId ?? row.trip.id)}</b><br/><small>{fmt(row.trip.startTime)}</small></td>
            <td>{val(row.trip.routeName)}<br/><small>{val(row.trip.direction)}</small></td>
            <td><StatusBadge state={isClosed(row.trip) ? 'CLOSED' : 'ACTIVE'} kind={isClosed(row.trip) ? 'neutral' : 'pending'} /></td>
            <td><StatusBadge state={row.cloud?.state || (row.error ? 'ERROR' : '—')} kind={row.cloud?.complete ? 'ok' : level === 'error' ? 'error' : 'pending'} /></td>
            <td><StatusBadge state={row.web?.state || (row.error ? 'ERROR' : '—')} kind={row.web?.complete ? 'ok' : level === 'error' ? 'error' : 'pending'} /></td>
            <td><QualityStates quality={row.quality} /></td>
            <td><b className={`quality-score quality-score-${qualityKind(row.quality?.primaryState)}`}>{val(row.quality?.score)}</b></td>
            <td>{val(row.cloud?.eventCount)}</td>
            <td>{val(row.cloud?.chunkCount)}</td>
            <td>{val(row.cloud?.rawPointCount)}</td>
            <td>{val(row.cloud?.validGpsPointCount)}</td>
            <td>{val(row.cloud?.invalidGpsPointCount)}</td>
            <td>{val(row.cloud?.pointPayloadDelta)}</td>
            <td className="integrity-result">
              {level === 'ok' && <CheckCircle2 size={16} />}
              {(level === 'warning' || level === 'error') && <AlertTriangle size={16} />}
              <span>{issues}</span>
            </td>
          </tr>;
        })}</tbody>
      </table></div>}
    </section>
  </div>;
}
