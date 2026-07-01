import React, { useMemo, useState } from "react";
import { Activity, MapPin, Search } from "lucide-react";
import "./LiveDevicesPanel.css";

const val = (value) => value === null || value === undefined || value === "" ? "—" : String(value);

function toMillis(value) {
  if (!value) return 0;
  if (typeof value === "number") return value;
  if (value.toMillis) return value.toMillis();
  if (value.seconds) return value.seconds * 1000;

  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatDate(value) {
  const ms = toMillis(value);
  if (!ms) return "—";
  return new Date(ms).toLocaleString("es-MX");
}

function minutesSince(value) {
  const ms = toMillis(value);
  if (!ms) return null;
  return Math.max(0, Math.floor((Date.now() - ms) / 60000));
}

function ageLabel(value) {
  const minutes = minutesSince(value);
  if (minutes === null) return "Sin reporte";
  if (minutes < 1) return "Ahora";
  if (minutes < 60) return `Hace ${minutes} min`;

  const hours = Math.floor(minutes / 60);
  return `Hace ${hours} h ${minutes % 60} min`;
}

function liveState(device) {
  const tripStatus = String(device.tripStatus || "").toUpperCase();
  if (tripStatus === "FINISHED" || tripStatus === "IDLE" || tripStatus === "CLOSED") {
    return { label: "Completado", className: "live-muted" };
  }

  const gpsStatus = String(device.gpsStatus || "").toUpperCase();
  if (gpsStatus === "LOST" || gpsStatus === "OFF" || gpsStatus === "POOR") {
    return { label: "GPS débil", className: "live-danger" };
  }

  const minutes = minutesSince(device.lastUpdateClient || device.lastUpdateServer);
  if (minutes === null) return { label: "Sin reporte", className: "live-muted" };
  if (minutes <= 2) return { label: "Transmitiendo", className: "live-ok" };
  if (minutes <= 10) return { label: "Reciente", className: "live-warn" };
  if (minutes <= 30) return { label: "Demorado", className: "live-late" };

  return { label: "Inactivo", className: "live-danger" };
}

function getLat(device) {
  return device.position?.lat ?? device.lat;
}

function getLon(device) {
  return device.position?.lon ?? device.lon;
}

function getBattery(device) {
  return device.battery?.level ?? device.battery ?? device.batteryPct;
}

function getRoute(device) {
  return device.route?.name ?? device.routeName ?? device.planningRouteId;
}

function getDirection(device) {
  return device.route?.direction ?? device.direction;
}

function getVehicle(device) {
  const eco = device.vehicle?.eco ?? device.vehicleEco;
  const plate = device.vehicle?.plate ?? device.plateNumber;
  return [eco, plate].filter(Boolean).join(" / ");
}

function getObserver(device) {
  return device.observer?.name ?? device.observerName ?? device.aforador;
}

function getDeviceName(device) {
  const model = device.device?.model;
  const manufacturer = device.device?.manufacturer;
  return [manufacturer, model].filter(Boolean).join(" ") || device.id;
}

function LiveBadge({ device }) {
  const state = liveState(device);
  return <span className={`live-badge ${state.className}`}>{state.label}</span>;
}

export default function LiveDevicesPanel({ devices }) {
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();

    return devices.filter((device) => {
      const state = liveState(device).label.toUpperCase();
      const matchesStatus = statusFilter === "ALL" || state === statusFilter;
      const haystack = [
        device.id,
        device.installationId,
        device.tripId,
        getRoute(device),
        getDirection(device),
        getObserver(device),
        getVehicle(device),
        getDeviceName(device),
        device.gpsStatus,
        device.tripStatus,
        device.syncReason,
      ].join(" ").toLowerCase();

      return matchesStatus && (!q || haystack.includes(q));
    });
  }, [devices, query, statusFilter]);

  const counters = useMemo(() => {
    return devices.reduce((acc, device) => {
      const state = liveState(device).label;
      acc[state] = (acc[state] || 0) + 1;
      return acc;
    }, {});
  }, [devices]);

  return <div className="live-wrap">
    <section className="grid stats live-stats">
      <div className="stat"><b>{devices.length}</b><span>Equipos en Campo</span></div>
      <div className="stat"><b>{counters.Activo || 0}</b><span>Transmitiendo</span></div>
      <div className="stat"><b>{(counters.Reciente || 0) + (counters.Atrasado || 0)}</b><span>Con retraso</span></div>
      <div className="stat"><b>{(counters.Inactivo || 0) + (counters["GPS débil"] || 0)}</b><span>Requieren atención</span></div>
    </section>

    <section className="card live-toolbar">
      <div>
        <h3><Activity size={18} /> Centro de Control en Vivo</h3>
        <p>Monitoreo de actividad y ubicación de operadores en tiempo real.</p>
      </div>
      <div className="live-filters">
        <label>
          <Search size={16} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar por ruta, operador, equipo..." />
        </label>
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="ALL">Ver todos</option>
          <option value="ACTIVO">Activo</option>
          <option value="RECIENTE">Reciente</option>
          <option value="ATRASADO">Demorado</option>
          <option value="INACTIVO">Inactivo</option>
          <option value="GPS DÉBIL">GPS débil</option>
          <option value="FINALIZADO">Completado</option>
        </select>
      </div>
    </section>

    {filtered.length === 0 ? <div className="empty">No se encontraron equipos con los filtros seleccionados.</div> : <div className="table live-table"><table>
      <thead><tr>
        <th>Estado</th>
        <th>Operador / Equipo</th>
        <th>Levantamiento / Ruta</th>
        <th>Unidad</th>
        <th>Ubicación</th>
        <th>Calidad GPS</th>
        <th>Energía</th>
        <th>Último Reporte</th>
        <th>Sincronización</th>
      </tr></thead>
      <tbody>{filtered.map((device) => <tr key={device.id}>
        <td><LiveBadge device={device} /></td>
        <td>
          <b>{val(getObserver(device))}</b>
          <span>{val(getDeviceName(device))}</span>
          <small>{val(device.installationId?.substring(0,8) || device.id?.substring(0,8))}</small>
        </td>
        <td>
          <b>{val(getRoute(device))}</b>
          <span>{val(getDirection(device))}</span>
          <small>Folio: {val(device.tripId)}</small>
        </td>
        <td>{val(getVehicle(device))}</td>
        <td>
          <span><MapPin size={13} /> {val(getLat(device))}, {val(getLon(device))}</span>
          <small>Margen: {val(device.position?.accuracy ?? device.accuracy)}m</small>
        </td>
        <td>{val(device.gpsStatus)}</td>
        <td>{val(getBattery(device))}{getBattery(device) !== undefined && getBattery(device) !== null ? "%" : ""}</td>
        <td>
          <b>{ageLabel(device.lastUpdateClient || device.lastUpdateServer)}</b>
          <span>{formatDate(device.lastUpdateClient || device.lastUpdateServer)}</span>
        </td>
        <td>
          <b>{val(device.tripStatus === 'ACTIVE' ? 'OPERANDO' : 'COMPLETADO')}</b>
          <small>v{val(device.syncVersion)}</small>
        </td>
      </tr>)}</tbody>
    </table></div>}
  </div>;
}
