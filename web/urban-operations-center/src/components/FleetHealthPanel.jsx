import React, { useMemo } from "react";
import { Battery, Clock, Cpu, ShieldCheck, Smartphone, Wifi } from "lucide-react";
import "./FleetHealthPanel.css";

const val = (v) => v === null || v === undefined || v === "" ? "—" : String(v);

function millis(v) {
  if (!v) return 0;
  if (typeof v === "number") return v;
  if (v.toMillis) return v.toMillis();
  if (v.seconds) return v.seconds * 1000;
  const parsed = new Date(v).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function minutesSince(v) {
  const ms = millis(v);
  return ms ? Math.max(0, Math.floor((Date.now() - ms) / 60000)) : null;
}

function age(v) {
  const m = minutesSince(v);
  if (m === null) return "Sin conexión";
  if (m < 1) return "Ahora";
  if (m < 60) return `Hace ${m} min`;
  return `Hace ${Math.floor(m / 60)} h ${m % 60} min`;
}

function fmt(v) {
  const ms = millis(v);
  return ms ? new Date(ms).toLocaleString("es-MX") : "—";
}

function healthState(device) {
  const lastSeen = device.lastSeenClient || device.lastSeen;
  const minutes = minutesSince(lastSeen);
  const battery = Number(device.battery?.level ?? device.battery ?? 0);
  const licenseStatus = String(device.license?.licenseStatus || "").toUpperCase();
  const installationStatus = String(device.license?.installationStatus || device.status || "").toUpperCase();

  if (installationStatus === "BLOCKED" || licenseStatus === "BLOCKED" || licenseStatus === "SUSPENDED") {
    return { label: "Bloqueado", cls: "fleet-danger" };
  }
  if (minutes === null) return { label: "Sin reporte", cls: "fleet-muted" };
  if (minutes > 30) return { label: "Desconectado", cls: "fleet-danger" };
  if (battery > 0 && battery < 20) return { label: "Batería baja", cls: "fleet-late" };
  if (minutes > 10) return { label: "Inactivo", cls: "fleet-warn" };
  return { label: "En línea", cls: "fleet-ok" };
}

function getBattery(device) {
  return device.battery?.level ?? device.battery ?? device.batteryPct;
}

function getDeviceName(device) {
  return [device.device?.manufacturer, device.device?.model].filter(Boolean).join(" ") || device.id;
}

export default function FleetHealthPanel({ installations }) {
  const counters = useMemo(() => {
    return installations.reduce((acc, device) => {
      const state = healthState(device).label;
      acc[state] = (acc[state] || 0) + 1;
      return acc;
    }, {});
  }, [installations]);

  return <div className="fleet-wrap">
    <section className="grid stats fleet-stats">
      <div className="stat"><b>{installations.length}</b><span>Equipos</span></div>
      <div className="stat"><b>{counters["En línea"] || 0}</b><span>Activos</span></div>
      <div className="stat"><b>{(counters.Inactivo || 0) + (counters.Desconectado || 0)}</b><span>Sin reporte</span></div>
      <div className="stat"><b>{(counters.Bloqueado || 0) + (counters["Batería baja"] || 0)}</b><span>Requieren atención</span></div>
    </section>

    <section className="card fleet-card">
      <div className="fleet-head">
        <div>
          <h3><Wifi size={18} /> Estado de la Flota</h3>
          <p>Monitoreo técnico de los equipos autorizados en campo.</p>
        </div>
      </div>

      {installations.length === 0 ? <div className="empty">Esperando reportes de equipos...</div> : <div className="table fleet-table"><table>
        <thead><tr>
          <th>Estado</th>
          <th>Equipo</th>
          <th>Proyecto / Cliente</th>
          <th>Licencia</th>
          <th>Rastreo GPS</th>
          <th>Batería</th>
          <th>Último Reporte</th>
          <th>Levantamiento Activo</th>
        </tr></thead>
        <tbody>{installations.map((device) => {
          const st = healthState(device);
          const b = getBattery(device);
          return <tr key={device.id}>
            <td><span className={`fleet-badge ${st.cls}`}>{st.label}</span></td>
            <td>
              <b><Smartphone size={13} /> {val(getDeviceName(device))}</b>
              <span>{val(device.id.substring(0, 8))}</span>
              <small>v{val(device.device?.appVersionName)}</small>
            </td>
            <td>
              <b>{val(device.projectId)}</b>
              <span>{val(device.workspaceId)}</span>
            </td>
            <td>
              <b><ShieldCheck size={13} /> {val(device.license?.licenseStatus === 'ACTIVE' ? 'ACTIVA' : 'VERIFICAR')}</b>
              <small>{val(device.license?.plan)}</small>
            </td>
            <td>
              <b><Cpu size={13} /> {val(device.config?.gpsProfile)}</b>
              <span>Auto-envío {val(device.config?.heartbeatIntervalSeconds)}s</span>
            </td>
            <td><Battery size={13} /> {val(b)}{b !== undefined && b !== null ? "%" : ""}</td>
            <td>
              <b><Clock size={13} /> {age(device.lastSeenClient || device.lastSeen)}</b>
              <span>{fmt(device.lastSeenClient || device.lastSeen)}</span>
            </td>
            <td>{val(device.activeTripId)}</td>
          </tr>;
        })}</tbody>
      </table></div>}
    </section>
  </div>;
}
