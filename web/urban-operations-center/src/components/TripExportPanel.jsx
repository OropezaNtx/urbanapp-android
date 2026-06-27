import React from 'react';
import { Download, FileJson, Globe2, Map, Table2 } from 'lucide-react';
import { buildExportStats, downloadGeoJson, downloadGpx, downloadKml, downloadTrackCsv } from '../exporters/geo';

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

function ExportButton({ icon, title, text, disabled, onClick }) {
  return <button className="export-button" disabled={disabled} onClick={onClick}>
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
        <b>Paquete geoespacial</b>
        <span>Archivos listos para Google Earth, QGIS, ArcGIS y auditoría externa.</span>
      </div>
      <div className="export-badges">
        <span>{stats.pointCount} puntos GPS</span>
        <span>{stats.eventPointCount} eventos georreferenciados</span>
        <span>{stats.distanceKm.toFixed(3)} km</span>
        <span>{fmtDuration(stats.durationMs)}</span>
      </div>
    </div>

    <div className="export-actions">
      <ExportButton
        icon={<Globe2 size={18} />}
        title="GPX"
        text="Track + waypoints para GPS/QGIS"
        disabled={!canExport}
        onClick={() => downloadGpx(trip, events, chunks)}
      />
      <ExportButton
        icon={<Map size={18} />}
        title="KML"
        text="Google Earth / Google My Maps"
        disabled={!canExport}
        onClick={() => downloadKml(trip, events, chunks)}
      />
      <ExportButton
        icon={<FileJson size={18} />}
        title="GeoJSON"
        text="GIS web, QGIS y pipelines"
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
      <Download size={15} /> El GPX/KML incluye la línea del recorrido y los eventos como puntos con metadatos de pasajeros, WP, precisión y notas.
    </div>
  </div>;
}
