import React, { useMemo, useState } from 'react';
import { buildTrackMetrics } from './TrackSummary';

const val = (v) => v === null || v === undefined || v === '' ? '—' : String(v);
const num = (v) => Number(v ?? 0) || 0;

const fmtTime = (v) => {
  if (!v) return '—';
  const d = new Date(Number(v));
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleTimeString('es-MX');
};

function validCoord(lat, lon) {
  const a = Number(lat);
  const b = Number(lon);
  return Number.isFinite(a) && Number.isFinite(b) && !(a === 0 && b === 0);
}

function eventLat(e) { return e.stopLat ?? e.lat ?? e.startLat; }
function eventLon(e) { return e.stopLon ?? e.lon ?? e.startLon; }
function eventType(e) { return e.eventType ?? e.stopType ?? 'EVENT'; }
function eventDelay(e) { return e.delayCodes ?? ''; }
function eventTime(e) { return e.timestamp ?? e.stopTime ?? e.startTime ?? e.createdAt; }
function menUp(e) { return num(e.menUp ?? e.paxMenUp); }
function womenUp(e) { return num(e.womenUp ?? e.paxWomenUp); }
function menDown(e) { return num(e.menDown ?? e.paxMenDown); }
function womenDown(e) { return num(e.womenDown ?? e.paxWomenDown); }
function totalUp(e) { return menUp(e) + womenUp(e); }
function totalDown(e) { return menDown(e) + womenDown(e); }

function eventLabel(e) {
  const type = String(eventType(e) || '').trim();
  const delay = String(eventDelay(e) || '').trim();
  if (type && delay && !type.includes(delay)) return `${type} + ${delay}`;
  return type || delay || 'EVENT';
}

function eventClassName(e) {
  const type = String(eventType(e)).toUpperCase();
  const delay = String(eventDelay(e)).toUpperCase();
  if (type.includes('BANDERA')) return 'flag-marker';
  if (type.includes('DEMORA') || delay) return 'delay-marker';
  if (type.includes('ASD')) return 'asd-marker';
  return 'event-marker';
}

function buildProjection(points, events) {
  const coords = [
    ...points.map((p) => ({ lat: Number(p.lat), lon: Number(p.lon) })),
    ...events
      .filter((e) => validCoord(eventLat(e), eventLon(e)))
      .map((e) => ({ lat: Number(eventLat(e)), lon: Number(eventLon(e)) })),
  ];

  if (!coords.length) return null;

  const minLat = Math.min(...coords.map((c) => c.lat));
  const maxLat = Math.max(...coords.map((c) => c.lat));
  const minLon = Math.min(...coords.map((c) => c.lon));
  const maxLon = Math.max(...coords.map((c) => c.lon));
  const pad = 30;
  const width = 1000;
  const height = 460;
  const latSpan = Math.max(maxLat - minLat, 0.00001);
  const lonSpan = Math.max(maxLon - minLon, 0.00001);

  const project = (lat, lon) => ({
    x: pad + ((Number(lon) - minLon) / lonSpan) * (width - pad * 2),
    y: height - pad - ((Number(lat) - minLat) / latSpan) * (height - pad * 2),
  });

  return { width, height, project };
}

function MarkerTooltip({ marker }) {
  if (!marker) return null;
  const e = marker.event;

  return <div className="map-tooltip" style={{ left: marker.screenX, top: marker.screenY }}>
    <b>{fmtTime(eventTime(e))} · {eventLabel(e)}</b>
    <div>WP {val(e.waypointStartId)} → {val(e.waypointStopId)}</div>
    <div>Suben <strong>{totalUp(e)}</strong> ({menUp(e)}/{womenUp(e)}) · Bajan <strong>{totalDown(e)}</strong> ({menDown(e)}/{womenDown(e)})</div>
    <div>GPS {val(e.locationStatus || e.stopProvider || e.provider || e.startProvider)} · {val(e.stopAccM ?? e.accuracy ?? e.startAccM)} m</div>
    {(e.notes || e.otherDelayDesc) && <p>{e.notes || e.otherDelayDesc}</p>}
  </div>;
}

export default function TripMap({ chunks = [], events = [] }) {
  const [hovered, setHovered] = useState(null);
  const track = buildTrackMetrics(chunks);
  const points = track.points;
  const projection = buildProjection(points, events);

  const eventCounts = useMemo(() => {
    return events.reduce((acc, e) => {
      const type = String(eventType(e)).toUpperCase();
      const delay = String(eventDelay(e)).trim();
      if (type.includes('BANDERA')) acc.bandera += 1;
      else if (type.includes('DEMORA') || delay) acc.demora += 1;
      else if (type.includes('ASD')) acc.asd += 1;
      else acc.otro += 1;
      return acc;
    }, { asd: 0, demora: 0, bandera: 0, otro: 0 });
  }, [events]);

  if (!projection) {
    return <div className="empty">Sin coordenadas para dibujar mapa.</div>;
  }

  const line = points.map((p) => {
    const xy = projection.project(p.lat, p.lon);
    return `${xy.x},${xy.y}`;
  }).join(' ');

  const first = points[0];
  const last = points[points.length - 1];
  const firstXY = first ? projection.project(first.lat, first.lon) : null;
  const lastXY = last ? projection.project(last.lat, last.lon) : null;
  const eventMarkers = events
    .filter((e) => validCoord(eventLat(e), eventLon(e)))
    .map((e) => ({ ...e, xy: projection.project(eventLat(e), eventLon(e)) }));

  const openGoogleMaps = () => {
    if (!first || !last) return;
    const url = `https://www.google.com/maps/dir/${first.lat},${first.lon}/${last.lat},${last.lon}`;
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  return <div className="map-wrap">
    <div className="map-toolbar">
      <div>
        <b>Vista de recorrido</b>
        <span>{points.length} puntos · {eventMarkers.length} eventos con coordenada</span>
      </div>
      <button onClick={openGoogleMaps}>Abrir inicio/fin en Google Maps</button>
    </div>

    <div className="map-event-summary">
      <span><i className="legend-asd" /> ASD {eventCounts.asd}</span>
      <span><i className="legend-delay" /> Demoras {eventCounts.demora}</span>
      <span><i className="legend-flag" /> Banderas {eventCounts.bandera}</span>
      <span><i className="legend-event" /> Otros {eventCounts.otro}</span>
    </div>

    <div className="map-stage" onMouseLeave={() => setHovered(null)}>
      <svg className="trip-map" viewBox={`0 0 ${projection.width} ${projection.height}`} role="img" aria-label="Mapa esquemático del recorrido">
        <rect x="0" y="0" width={projection.width} height={projection.height} rx="18" />
        {line && <polyline points={line} className="track-line" />}
        {firstXY && <g>
          <circle cx={firstXY.x} cy={firstXY.y} r="9" className="start-marker" />
          <text x={firstXY.x + 12} y={firstXY.y - 8}>Inicio</text>
        </g>}
        {lastXY && <g>
          <circle cx={lastXY.x} cy={lastXY.y} r="9" className="end-marker" />
          <text x={lastXY.x + 12} y={lastXY.y + 18}>Fin</text>
        </g>}
        {eventMarkers.map((e, idx) => <g
          key={e.id || e.cloudEventId || idx}
          className="map-event-hit"
          onMouseMove={(ev) => setHovered({ event: e, screenX: ev.clientX + 14, screenY: ev.clientY + 14 })}
        >
          <circle cx={e.xy.x} cy={e.xy.y} r="10" className="marker-halo" />
          <circle cx={e.xy.x} cy={e.xy.y} r="6" className={eventClassName(e)} />
          <title>{`${eventLabel(e)} · WP ${val(e.waypointStartId)}→${val(e.waypointStopId)} · suben ${totalUp(e)} bajan ${totalDown(e)}`}</title>
        </g>)}
      </svg>
      <MarkerTooltip marker={hovered} />
    </div>

    <div className="map-legend">
      <span><i className="legend-start" /> Inicio</span>
      <span><i className="legend-end" /> Fin</span>
      <span><i className="legend-asd" /> ASD</span>
      <span><i className="legend-delay" /> Demora</span>
      <span><i className="legend-flag" /> Bandera</span>
    </div>
  </div>;
}
