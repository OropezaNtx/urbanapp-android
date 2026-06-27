import React, { useEffect, useMemo, useState } from 'react';
import { Pause, Play, RotateCcw, SkipBack, SkipForward } from 'lucide-react';
import { buildTrackMetrics } from './TrackSummary';

const num = (v) => Number(v ?? 0) || 0;
const val = (v) => v === null || v === undefined || v === '' ? '—' : String(v);

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
function eventTime(e) { return num(e.timestamp || e.stopTime || e.startTime || e.createdAt); }
function eventType(e) { return e.eventType ?? e.stopType ?? 'EVENT'; }
function eventDelay(e) { return e.delayCodes ?? ''; }
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
  const delay = String(eventDelay(e)).trim();
  if (type.includes('BANDERA')) return 'playback-flag';
  if (type.includes('DEMORA') || delay) return 'playback-delay';
  if (type.includes('ASD')) return 'playback-asd';
  return 'playback-event';
}

function buildProjection(points, events) {
  const coords = [
    ...points.map((p) => ({ lat: Number(p.lat), lon: Number(p.lon) })),
    ...events.filter((e) => validCoord(eventLat(e), eventLon(e))).map((e) => ({ lat: Number(eventLat(e)), lon: Number(eventLon(e)) })),
  ];

  if (!coords.length) return null;

  const minLat = Math.min(...coords.map((c) => c.lat));
  const maxLat = Math.max(...coords.map((c) => c.lat));
  const minLon = Math.min(...coords.map((c) => c.lon));
  const maxLon = Math.max(...coords.map((c) => c.lon));
  const pad = 32;
  const width = 1000;
  const height = 420;
  const latSpan = Math.max(maxLat - minLat, 0.00001);
  const lonSpan = Math.max(maxLon - minLon, 0.00001);

  return {
    width,
    height,
    project: (lat, lon) => ({
      x: pad + ((Number(lon) - minLon) / lonSpan) * (width - pad * 2),
      y: height - pad - ((Number(lat) - minLat) / latSpan) * (height - pad * 2),
    }),
  };
}

function buildFrames(chunks = [], events = []) {
  const track = buildTrackMetrics(Array.isArray(chunks) ? chunks : []);
  const trackFrames = track.points.map((p, idx) => ({
    kind: 'track',
    time: num(p.time),
    lat: Number(p.lat),
    lon: Number(p.lon),
    label: `Punto GPS ${idx + 1}`,
    point: p,
  }));

  const eventFrames = (Array.isArray(events) ? events : [])
    .filter((e) => validCoord(eventLat(e), eventLon(e)) && eventTime(e) > 0)
    .map((e) => ({
      kind: 'event',
      time: eventTime(e),
      lat: Number(eventLat(e)),
      lon: Number(eventLon(e)),
      label: eventLabel(e),
      event: e,
    }));

  return [...trackFrames, ...eventFrames].sort((a, b) => a.time - b.time);
}

function usePlayback(totalFrames) {
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);

  useEffect(() => {
    if (!playing || totalFrames <= 1) return undefined;
    const delay = Math.max(120, 700 / speed);
    const id = window.setInterval(() => {
      setIndex((i) => {
        if (i >= totalFrames - 1) {
          setPlaying(false);
          return i;
        }
        return i + 1;
      });
    }, delay);
    return () => window.clearInterval(id);
  }, [playing, speed, totalFrames]);

  return { index, setIndex, playing, setPlaying, speed, setSpeed };
}

function CurrentFrameCard({ frame }) {
  if (!frame) return <div className="empty">Sin frame activo.</div>;
  if (frame.kind === 'event') {
    const e = frame.event;
    return <div className={`playback-current ${eventClassName(e)}`}>
      <span>{fmtTime(frame.time)} · Evento</span>
      <b>{eventLabel(e)}</b>
      <small>WP {val(e.waypointStartId)} → {val(e.waypointStopId)} · Suben {totalUp(e)} · Bajan {totalDown(e)} · GPS {val(e.stopAccM ?? e.accuracy ?? e.startAccM)} m</small>
    </div>;
  }
  return <div className="playback-current playback-track">
    <span>{fmtTime(frame.time)} · Track</span>
    <b>{frame.label}</b>
    <small>{frame.lat.toFixed(6)}, {frame.lon.toFixed(6)} · GPS {val(frame.point?.accuracy)} m · {val(frame.point?.provider)}</small>
  </div>;
}

export default function TripPlayback({ chunks = [], events = [] }) {
  const frames = useMemo(() => buildFrames(chunks, events), [chunks, events]);
  const track = buildTrackMetrics(Array.isArray(chunks) ? chunks : []);
  const projection = buildProjection(track.points, events);
  const { index, setIndex, playing, setPlaying, speed, setSpeed } = usePlayback(frames.length);
  const current = frames[index];

  if (!projection || !frames.length) {
    return <div className="empty">No hay puntos suficientes para reproducir el viaje.</div>;
  }

  const trackLine = track.points.map((p) => {
    const xy = projection.project(p.lat, p.lon);
    return `${xy.x},${xy.y}`;
  }).join(' ');

  const traveledLine = frames
    .slice(0, index + 1)
    .filter((f) => f.kind === 'track')
    .map((f) => {
      const xy = projection.project(f.lat, f.lon);
      return `${xy.x},${xy.y}`;
    }).join(' ');

  const currentXY = current ? projection.project(current.lat, current.lon) : null;
  const eventMarkers = frames.filter((f) => f.kind === 'event').map((f) => ({ ...f, xy: projection.project(f.lat, f.lon) }));
  const progress = frames.length > 1 ? (index / (frames.length - 1)) * 100 : 0;

  return <div className="playback-wrap">
    <div className="playback-head">
      <div>
        <b>Reproducción del recorrido</b>
        <span>{frames.length} frames · {track.totalPoints} puntos GPS · {eventMarkers.length} eventos</span>
      </div>
      <div className="playback-controls">
        <button onClick={() => setIndex(0)}><RotateCcw size={16} />Reset</button>
        <button onClick={() => setIndex((i) => Math.max(0, i - 1))}><SkipBack size={16} /></button>
        <button onClick={() => setPlaying(!playing)}>{playing ? <Pause size={16} /> : <Play size={16} />}{playing ? 'Pausar' : 'Play'}</button>
        <button onClick={() => setIndex((i) => Math.min(frames.length - 1, i + 1))}><SkipForward size={16} /></button>
        <select value={speed} onChange={(e) => setSpeed(Number(e.target.value))}>
          <option value="0.5">0.5x</option>
          <option value="1">1x</option>
          <option value="2">2x</option>
          <option value="4">4x</option>
        </select>
      </div>
    </div>

    <div className="playback-progress">
      <input
        type="range"
        min="0"
        max={Math.max(0, frames.length - 1)}
        value={index}
        onChange={(e) => setIndex(Number(e.target.value))}
      />
      <span>{Math.round(progress)}%</span>
    </div>

    <CurrentFrameCard frame={current} />

    <svg className="playback-map" viewBox={`0 0 ${projection.width} ${projection.height}`} role="img" aria-label="Playback del recorrido">
      <rect x="0" y="0" width={projection.width} height={projection.height} rx="18" />
      {trackLine && <polyline points={trackLine} className="playback-track-line" />}
      {traveledLine && <polyline points={traveledLine} className="playback-traveled-line" />}
      {eventMarkers.map((m, idx) => <g key={`${m.time}-${idx}`} opacity={m.time <= current.time ? 1 : .28}>
        <circle cx={m.xy.x} cy={m.xy.y} r="6" className={eventClassName(m.event)} />
        <title>{eventLabel(m.event)}</title>
      </g>)}
      {currentXY && <g>
        <circle cx={currentXY.x} cy={currentXY.y} r="16" className="vehicle-pulse" />
        <circle cx={currentXY.x} cy={currentXY.y} r="8" className="vehicle-dot" />
      </g>}
    </svg>
  </div>;
}
