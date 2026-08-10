import React, { useMemo } from 'react';
import { AlertTriangle, CheckCircle2, Cloud, HeartPulse, Radio, RotateCcw, ShieldAlert, Smartphone, Wifi } from 'lucide-react';
import { buildOperationalHealth } from '../services/operationalHealth';
import './OperationalHealthPanel.css';

const LABELS = {
  HEALTHY: 'Saludable',
  DEGRADED: 'Degradado',
  RECOVERED: 'Recuperado',
  CRITICAL: 'Crítico',
  NOT_AVAILABLE: 'Sin telemetría',
};

const DOMAIN_LABELS = {
  gps: 'GPS',
  device: 'Dispositivo',
  network: 'Red',
  recovery: 'Recovery',
  sync: 'Sincronización',
};

const DOMAIN_ICONS = {
  gps: Radio,
  device: Smartphone,
  network: Wifi,
  recovery: RotateCcw,
  sync: Cloud,
};

function StateBadge({ state }) {
  return <span className={`health-state health-${String(state || 'NOT_AVAILABLE').toLowerCase()}`}>
    {LABELS[state] || state || 'Sin telemetría'}
  </span>;
}

function DomainCard({ name, domain }) {
  const Icon = DOMAIN_ICONS[name] || HeartPulse;
  return <div className={`health-domain health-domain-${String(domain?.state || 'NOT_AVAILABLE').toLowerCase()}`}>
    <div className="health-domain-head">
      <span><Icon size={17} /> {DOMAIN_LABELS[name] || name}</span>
      <StateBadge state={domain?.state || 'NOT_AVAILABLE'} />
    </div>
    <small>{domain?.issues?.length ? `${domain.issues.length} observación(es)` : domain?.state === 'NOT_AVAILABLE' ? 'No existe Telemetry v1 para este recorrido.' : 'Sin incidencias detectadas.'}</small>
  </div>;
}

function IssueIcon({ severity }) {
  if (severity === 'CRITICAL') return <ShieldAlert size={17} />;
  if (severity === 'WARNING') return <AlertTriangle size={17} />;
  return <CheckCircle2 size={17} />;
}

export default function OperationalHealthPanel({ analytics }) {
  const tripDocId = analytics?.telemetry?.tripId ?? null;
  const health = useMemo(() => buildOperationalHealth({ tripDocId, analytics }), [tripDocId, analytics]);
  if (!health) return null;

  return <div className="operational-health">
    <div className="operational-health-head">
      <div>
        <span className="health-eyebrow">Operational Health</span>
        <h4>Salud operativa del levantamiento</h4>
        <p>Interpreta GPS, dispositivo, red, recovery y sincronización sin modificar las métricas originales.</p>
      </div>
      <div className="health-score-wrap">
        <strong>{health.telemetryAvailable ? health.score : '—'}</strong>
        <span>{health.telemetryAvailable ? '/ 100' : 'sin score'}</span>
        <StateBadge state={health.state} />
      </div>
    </div>

    <div className="health-domains">
      {Object.entries(health.domains).map(([name, domain]) => <DomainCard key={name} name={name} domain={domain} />)}
    </div>

    {!health.telemetryAvailable && <div className="health-empty">
      Este recorrido es anterior a Telemetry v1. Las métricas GPS siguen disponibles, pero no se asigna una salud operativa global sin evidencia histórica de batería, heartbeat, red y sincronización.
    </div>}

    {health.telemetryAvailable && <div className="health-summary">
      <span><b>{health.summary.critical}</b> críticas</span>
      <span><b>{health.summary.warnings}</b> advertencias</span>
      <span><b>{health.summary.info}</b> informativas</span>
    </div>}

    {health.issues.length > 0 && <div className="health-issues">
      <h5>Hallazgos</h5>
      {health.issues.map((item) => <div key={`${item.domain}-${item.code}`} className={`health-issue health-issue-${item.severity.toLowerCase()}`}>
        <IssueIcon severity={item.severity} />
        <div>
          <b>{item.code}</b>
          <span>{item.message}</span>
        </div>
        {item.deduction > 0 && <small>-{item.deduction} pts</small>}
      </div>)}
    </div>}

    {health.telemetryAvailable && health.issues.length === 0 && <div className="health-clean">
      <CheckCircle2 size={18} /> No se detectaron condiciones operativas degradadas con las señales disponibles.
    </div>}

    <div className="health-version">Health Engine {health.version}</div>
  </div>;
}
