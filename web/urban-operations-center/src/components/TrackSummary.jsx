import React from 'react';

const num = (v) => Number(v ?? 0) || 0;
const val = (v) => v === null || v === undefined || v === '' ? '—' : String(v);

const fmtTime = (v) => {
  if (!v) return '—';
  const d = new Date(Number(v));
  return isNaN(d.getTime()) ? String(v) : d.toLocaleTimeString('es-MX');
};

const fmtDuration = (ms) => {
  if (!Number.isFinite(Number(ms))) return '—';
  const totalSec = Math.max(0, Math.floor(Number(ms) / 1000));
  const h = String(Math.floor(totalSec / 3600)).padStart(2, '0');
  const m = String(Math.floor((totalSec % 3600) / 60)).padStart(2, '0');
  const s = String(totalSec % 60).padStart(2, '0');
  return `${h}:${m}:${s}`;
};

function validPoint(p) {
  const lat = Number(p?.lat);
  const lon = Number(p?.lon);
  return Number.isFinite(lat) && Number.isFinite(lon) && !(lat === 0 && lon === 0);
}

function distanceM(a, b) {
  if (!validPoint(a) || !validPoint(b)) return 0;
  const R = 6371000;
  const lat1 = Number(a.lat) * Math.PI / 180;
  const lat2 = Number(b.lat) * Math.PI / 180;
  const dLat = (Number(b.lat) - Number(a.lat)) * Math.PI / 180;
  const dLon = (Number(b.lon) - Number(a.lon)) * Math.PI / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

export function buildTrackMetrics(chunks = []) {
  const points = chunks
    .flatMap((chunk) => (chunk.points || []).map((p, idx) => ({
      ...p,
      chunkId: chunk.chunkId || chunk.id,
      chunkIndex: chunk.chunkIndex,
      pointIndex: idx,
      source: chunk.source,
    })))
    .filter(validPoint)
    .sort((a, b) => num(a.time) - num(b.time));

  const totalPoints = points.length;
  const startTime = totalPoints ? num(points[0].time) : null;
  const endTime = totalPoints ? num(points[totalPoints - 1].time) : null;
  const distance = points.reduce((sum, p, idx) => idx === 0 ? 0 : sum + distanceM(points[idx - 1], p), 0);
  const avgAccuracy = totalPoints ? points.reduce((sum, p) => sum + num(p.accuracy), 0) / totalPoints : null;

  return {
    chunkCount: chunks.length,
    totalPoints,
    startTime,
    endTime,
    durationMs: startTime && endTime ? endTime - startTime : null,
    distance,
    avgAccuracy,
    points,
  };
}

function MiniField({ label, value }) {
  return <div className="field"><span>{label}</span><b>{val(value)}</b></div>;
}

export default function TrackSummary({ chunks = [] }) {
  const track = buildTrackMetrics(chunks);

  if (!track.totalPoints) {
    return <div className="empty">Sin puntos GPS sincronizados todavía.</div>;
  }

  return <>
    <div className="track-grid">
      <MiniField label="Chunks" value={track.chunkCount} />
      <MiniField label="Puntos GPS" value={track.totalPoints} />
      <MiniField label="Inicio track" value={fmtTime(track.startTime)} />
      <MiniField label="Fin track" value={fmtTime(track.endTime)} />
      <MiniField label="Duración GPS" value={fmtDuration(track.durationMs)} />
      <MiniField label="Distancia aprox." value={`${(track.distance / 1000).toFixed(3)} km`} />
      <MiniField label="Precisión promedio" value={track.avgAccuracy ? `${track.avgAccuracy.toFixed(1)} m` : '—'} />
    </div>

    <h4>Puntos GPS</h4>
    <div className="table compact"><table>
      <thead><tr><th>#</th><th>Hora</th><th>Lat</th><th>Lon</th><th>Precisión</th><th>Provider</th><th>Chunk</th></tr></thead>
      <tbody>{track.points.slice(0, 250).map((p, idx) => <tr key={`${p.chunkId}-${p.pointIndex}-${idx}`}>
        <td>{idx + 1}</td>
        <td>{fmtTime(p.time)}</td>
        <td>{val(p.lat)}</td>
        <td>{val(p.lon)}</td>
        <td>{val(p.accuracy)}</td>
        <td>{val(p.provider)}</td>
        <td>{val(p.chunkId)}</td>
      </tr>)}</tbody>
    </table></div>
    {track.points.length > 250 && <p>Mostrando los primeros 250 puntos de {track.points.length}.</p>}
  </>;
}
