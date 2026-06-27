import React from 'react';
import { buildTrackMetrics } from './TrackSummary';

const num = (v) => Number(v ?? 0) || 0;
const val = (v) => v === null || v === undefined || v === '' ? '—' : String(v);

const fmtTime = (v) => {
  if (!v) return '—';
  const d = new Date(Number(v));
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleTimeString('es-MX');
};

const fmtDuration = (ms) => {
  if (!Number.isFinite(Number(ms))) return '—';
  const totalSec = Math.max(0, Math.floor(Number(ms) / 1000));
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) return `${h}h ${m}m ${s}s`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
};

function eventType(e) { return e.eventType ?? e.stopType ?? ''; }
function delayCodes(e) { return e.delayCodes ?? ''; }
function menUp(e) { return num(e.menUp ?? e.paxMenUp); }
function womenUp(e) { return num(e.womenUp ?? e.paxWomenUp); }
function menDown(e) { return num(e.menDown ?? e.paxMenDown); }
function womenDown(e) { return num(e.womenDown ?? e.paxWomenDown); }
function totalUp(e) { return menUp(e) + womenUp(e); }
function totalDown(e) { return menDown(e) + womenDown(e); }

function eventLabel(e) {
  const type = String(eventType(e) || '').trim();
  const delay = String(delayCodes(e) || '').trim();
  if (type && delay && !type.includes(delay)) return `${type} + ${delay}`;
  return type || delay || 'Evento';
}

function eventTime(e) {
  return num(e.timestamp || e.stopTime || e.startTime || e.createdAt);
}

function eventDuration(e) {
  const start = num(e.startTime);
  const stop = num(e.stopTime || e.timestamp);
  if (start > 100_000_000_000 && stop > 100_000_000_000 && stop >= start) return stop - start;
  return 0;
}

function eventClass(e) {
  const type = String(eventType(e)).toUpperCase();
  const delay = String(delayCodes(e)).toUpperCase();
  if (type.includes('BANDERA')) return 'timeline-flag';
  if (type.includes('DEMORA') || delay) return 'timeline-delay';
  if (type.includes('ASD')) return 'timeline-asd';
  return 'timeline-event';
}

function metric(label, value, hint) {
  return { label, value, hint };
}

export function buildTripInsights(trip, events = [], chunks = []) {
  const sorted = [...events].sort((a, b) => eventTime(a) - eventTime(b));
  const track = buildTrackMetrics(chunks);
  const up = sorted.reduce((sum, e) => sum + totalUp(e), 0);
  const down = sorted.reduce((sum, e) => sum + totalDown(e), 0);
  const delayEvents = sorted.filter((e) => String(eventType(e)).toUpperCase().includes('DEMORA') || String(delayCodes(e)).trim());
  const asdEvents = sorted.filter((e) => String(eventType(e)).toUpperCase().includes('ASD'));
  const flagEvents = sorted.filter((e) => String(eventType(e)).toUpperCase().includes('BANDERA'));
  const delayMs = delayEvents.reduce((sum, e) => sum + eventDuration(e), 0);
  const tripStart = num(trip?.startTime || track.startTime || eventTime(sorted[0]));
  const tripEnd = num(trip?.endTime || track.endTime || eventTime(sorted[sorted.length - 1]));
  const tripMs = tripStart && tripEnd && tripEnd >= tripStart ? tripEnd - tripStart : track.durationMs;
  const km = track.distance / 1000;
  const avgKmh = tripMs && km ? km / (tripMs / 3_600_000) : 0;
  const paxKm = km ? up / km : 0;

  return {
    track,
    metrics: [
      metric('Ascensos', up, 'Total de pasajeros que subieron'),
      metric('Descensos', down, 'Total de pasajeros que bajaron'),
      metric('A bordo final', Math.max(0, up - down), 'Balance final del viaje'),
      metric('Eventos ASD', asdEvents.length, 'Capturas operativas'),
      metric('Demoras', delayEvents.length, 'Eventos con demora o código'),
      metric('Tiempo en demora', fmtDuration(delayMs), 'Suma de duración de eventos con inicio/fin'),
      metric('Distancia GPS', `${km.toFixed(3)} km`, 'Calculada desde track_chunks'),
      metric('Vel. prom.', avgKmh ? `${avgKmh.toFixed(1)} km/h` : '—', 'Distancia GPS / duración'),
      metric('Pax/km', paxKm ? paxKm.toFixed(1) : '—', 'Ascensos por kilómetro'),
      metric('Precisión GPS', track.avgAccuracy ? `${track.avgAccuracy.toFixed(1)} m` : '—', 'Promedio de puntos GPS'),
    ],
    timeline: sorted.map((e, idx) => ({
      id: e.id || e.cloudEventId || e.eventId || idx,
      index: idx + 1,
      time: eventTime(e),
      label: eventLabel(e),
      cls: eventClass(e),
      wp: `${val(e.waypointStartId)} → ${val(e.waypointStopId)}`,
      up: totalUp(e),
      down: totalDown(e),
      hmUp: `${menUp(e)}/${womenUp(e)}`,
      hmDown: `${menDown(e)}/${womenDown(e)}`,
      gps: e.locationStatus || e.stopProvider || e.provider || e.startProvider || '—',
      accuracy: e.stopAccM ?? e.accuracy ?? e.startAccM,
      duration: eventDuration(e),
      notes: e.notes || e.otherDelayDesc || '',
    })),
    counts: { asd: asdEvents.length, delays: delayEvents.length, flags: flagEvents.length },
  };
}

function InsightCard({ item }) {
  return <div className="insight-card">
    <span>{item.label}</span>
    <b>{item.value}</b>
    <small>{item.hint}</small>
  </div>;
}

export default function TripInsights({ trip, events = [], chunks = [] }) {
  const insights = buildTripInsights(trip, events, chunks);

  return <div className="insights-wrap">
    <div className="insight-grid">
      {insights.metrics.map((m) => <InsightCard key={m.label} item={m} />)}
    </div>

    <div className="timeline-wrap">
      <div className="timeline-head">
        <div>
          <b>Línea de tiempo operacional</b>
          <span>{insights.timeline.length} eventos · {insights.counts.delays} demoras · {insights.counts.flags} banderas</span>
        </div>
      </div>

      {!insights.timeline.length && <div className="empty">Sin eventos para timeline.</div>}

      <div className="timeline">
        {insights.timeline.map((e) => <div className={`timeline-item ${e.cls}`} key={e.id}>
          <div className="timeline-dot" />
          <div className="timeline-card">
            <div className="timeline-title">
              <b>{fmtTime(e.time)} · {e.label}</b>
              <span>WP {e.wp}</span>
            </div>
            <div className="timeline-data">
              <span>Suben <b>{e.up}</b> ({e.hmUp})</span>
              <span>Bajan <b>{e.down}</b> ({e.hmDown})</span>
              <span>GPS <b>{val(e.accuracy)}</b> m</span>
              {e.duration > 0 && <span>Duración <b>{fmtDuration(e.duration)}</b></span>}
            </div>
            {e.notes && <p>{e.notes}</p>}
          </div>
        </div>)}
      </div>
    </div>
  </div>;
}
