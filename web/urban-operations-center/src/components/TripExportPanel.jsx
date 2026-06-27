import React from 'react';
import { Download, FileJson, Globe2, Map, MapPin, Table2 } from 'lucide-react';
import {
  buildExportStats,
  downloadGarminTrackGpx,
  downloadGarminWaypointsGpx,
  downloadGeoJson,
  downloadKmz,
  downloadMapSourceCombinedGpx,
  downloadTrackCsv,
} from '../exporters/geo';

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

function ExportButton({ icon, title, text, disabled, onClick, primary = false }) {
  return <button className={`export-button ${primary ? 'export-primary' : ''}`} disabled={disabled} onClick={onClick}>
    {icon}
    <span>
      <b>{title}</b>
      <small>{text}</small>
    </span>
  </button>;
}

export default function TripExportPanel({ trip, events = [], chunks = [] }) {
  const stats = buildExportStats(events, chunks);
  const hasTrack = stats.pointCount > 0;
  const hasEvents = stats.eventPointCount > 0;
  const canExport = hasTrack || hasEvents;

  return <div className="export-panel">
    <div className="export-head">
      <div>
        <b>Paquete geoespacial Garmin</b>
        <span>Formatos compatibles con el flujo actual: WP Garmin, Track Garmin, GPX juntos y KMZ.</span>
      </div>
      <div className="export-badges">
        <span>{stats.pointCount} puntos GPS</span>
        <span>{stats.eventPointCount} WP georreferenciados</span>
        <span>{stats.distanceKm.toFixed(3)} km</span>
        <span>{fmtDuration(stats.durationMs)}</span>
      </div>
    </div>

    <div className="export-group-title">Formatos principales de operación</div>
    <div className="export-actions">
      <ExportButton
        primary
        icon={<MapPin size={18} />}
        title="WP Garmin GPX"
        text="Waypoints 001, 002... con Flag, Blue"
        disabled={!hasEvents}
        onClick={() => downloadGarminWaypointsGpx(trip, events)}
      />
      <ExportButton
        primary
        icon={<Globe2 size={18} />}
        title="Track Garmin GPX"
        text="Track eTrex/MapSource con DisplayColor"
        disabled={!hasTrack}
        onClick={() => downloadGarminTrackGpx(trip, events, chunks)}
      />
      <ExportButton
        primary
        icon={<Map size={18} />}
        title="GPX Juntos"
        text="WP + track estilo MapSource 6.16.3"
        disabled={!canExport}
        onClick={() => downloadMapSourceCombinedGpx(trip, events, chunks)}
      />
      <ExportButton
        primary
        icon={<Map size={18} />}
        title="KMZ"
        text="Google Earth / entrega visual"
        disabled={!canExport}
        onClick={() => downloadKmz(trip, events, chunks)}
      />
    </div>

    <div className="export-group-title">Formatos auxiliares</div>
    <div className="export-actions secondary">
      <ExportButton
        icon={<FileJson size={18} />}
        title="GeoJSON"
        text="QGIS, web GIS y pipelines"
        disabled={!canExport}
        onClick={() => downloadGeoJson(trip, events, chunks)}
      />
      <ExportButton
        icon={<Table2 size={18} />}
        title="CSV Track"
        text="Todos los puntos GPS en tabla"
        disabled={!hasTrack}
        onClick={() => downloadTrackCsv(trip, events, chunks)}
      />
    </div>

    {!canExport && <div className="empty">Este viaje todavía no tiene puntos GPS ni eventos con coordenada para exportar.</div>}
    <div className="export-note">
      <Download size={15} /> Los GPX principales replican la estructura Garmin: metadata Garmin, WP numerados, símbolo Flag Blue, track con extensiones Garmin y GPX combinado estilo MapSource.
    </div>
  </div>;
}
