import React, { useMemo } from "react";
import { Battery, Clock, Crosshair, MapPin, Navigation, Radio, Route } from "lucide-react";
import "./LiveDevicesMap.css";

const W = 1000;
const H = 380;
const PAD = 64;

const val = (v) => v === null || v === undefined || v === "" ? "—" : String(v);

function millis(v) {
  if (!v) return 0;
  if (typeof v === "number") return v;
  if (v.toMillis) return v.toMillis();
  if (v.seconds) return v.seconds * 1000;
  const parsed = new Date(v).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function minutes(v) {
  const ms = millis(v);
  return ms ? Math.max(0, Math.floor((Date.now() - ms) / 60000)) : null;
}

function age(v) {
  const m = minutes(v);
  if (m === null) return "Sin reporte";
  if (m < 1) return "Ahora";
  if (m < 60) return `Hace ${m} min`;
  return `Hace ${Math.floor(m / 60)} h ${m % 60} min`;
}

function fmt(v) {
  const ms = millis(v);
  return ms ? new Date(ms).toLocaleString("es-MX") : "—";
}

function lat(d) { return Number(d.position?.lat ?? d.lat); }
function lon(d) { return Number(d.position?.lon ?? d.lon); }
function valid(d) { return Number.isFinite(lat(d)) && Number.isFinite(lon(d)) && Math.abs(lat(d)) > 0.000001 && Math.abs(lon(d)) > 0.000001; }
function battery(d) { return d.battery?.level ?? d.battery ?? d.batteryPct; }
function route(d) { return d.route?.name ?? d.routeName ?? d.planningRouteId; }
function direction(d) { return d.route?.direction ?? d.direction; }
function observer(d) { return d.observer?.name ?? d.observerName ?? d.aforador; }
function deviceName(d) { return [d.device?.manufacturer, d.device?.model].filter(Boolean).join(" ") || d.id; }
function vehicle(d) { return [d.vehicle?.eco ?? d.vehicleEco, d.vehicle?.plate ?? d.plateNumber].filter(Boolean).join(" / "); }
function speed(d) { return Number(d.position?.speed ?? d.speed ?? 0) || 0; }
function heading(d) { return Number(d.position?.heading ?? d.heading ?? 0) || 0; }

function state(d) {
  const trip = String(d.tripStatus || "").toUpperCase();
  if (trip === "FINISHED" || trip === "IDLE") return { label: "Finalizado", cls: "s-muted", priority: 4 };
  const gps = String(d.gpsStatus || "").toUpperCase();
  if (["LOST", "OFF", "POOR"].includes(gps)) return { label: "GPS bajo", cls: "s-danger", priority: 1 };
  const m = minutes(d.lastUpdateClient || d.lastUpdateServer);
  if (m === null) return { label: "Sin reporte", cls: "s-muted", priority: 4 };
  if (m <= 2) return { label: "Vivo", cls: "s-ok", priority: 5 };
  if (m <= 10) return { label: "Reciente", cls: "s-warn", priority: 3 };
  if (m <= 30) return { label: "Atrasado", cls: "s-late", priority: 2 };
  return { label: "Perdido", cls: "s-danger", priority: 1 };
}

function projector(devices) {
  if (!devices.length) return null;
  let minLat = Infinity, maxLat = -Infinity, minLon = Infinity, maxLon = -Infinity;
  devices.forEach((d) => {
    minLat = Math.min(minLat, lat(d)); maxLat = Math.max(maxLat, lat(d));
    minLon = Math.min(minLon, lon(d)); maxLon = Math.max(maxLon, lon(d));
  });
  if (Math.abs(maxLat - minLat) < 0.001) { minLat -= 0.002; maxLat += 0.002; }
  if (Math.abs(maxLon - minLon) < 0.001) { minLon -= 0.002; maxLon += 0.002; }
  return (d) => ({
    x: PAD + ((lon(d) - minLon) / (maxLon - minLon)) * (W - PAD * 2),
    y: PAD + ((maxLat - lat(d)) / (maxLat - minLat)) * (H - PAD * 2),
  });
}

function DeviceDot({ d, p, selected, onSelect }) {
  const st = state(d);
  const label = observer(d) || deviceName(d);
  return <g className={`live-dot ${st.cls} ${selected ? "selected" : ""}`} transform={`translate(${p.x} ${p.y})`} onClick={() => onSelect(d.id)}>
    <circle className="pulse" r="25" />
    <circle className="core" r="11" />
    <g transform={`rotate(${heading(d)})`}><path className="heading-arrow" d="M0 -27 L7 -13 L0 -17 L-7 -13 Z" /></g>
    <text x="17" y="5">{label}</text>
  </g>;
}

function InfoCard({ d }) {
  if (!d) return null;
  const st = state(d);
  const updated = d.lastUpdateClient || d.lastUpdateServer;
  const b = battery(d);
  const kmh = Math.round(speed(d) * 3.6);
  return <div className="live-map-info">
    <div className="live-map-info-head">
      <div><b>{val(observer(d) || deviceName(d))}</b><span>{val(d.installationId || d.id)}</span></div>
      <span className={`state-pill ${st.cls}`}>{st.label}</span>
    </div>
    <div className="info-grid">
      <span><Navigation size={14} /> {val(route(d))}</span>
      <span><MapPin size={14} /> {val(direction(d))}</span>
      <span><Battery size={14} /> {val(b)}{b !== undefined && b !== null ? "%" : ""}</span>
      <span><Clock size={14} /> {age(updated)}</span>
      <span><Crosshair size={14} /> {val(vehicle(d))}</span>
      <span><Route size={14} /> {kmh} km/h</span>
    </div>
    <p>GPS {val(d.gpsStatus)} · {val(d.syncReason)} · {fmt(updated)} · {val(lat(d))}, {val(lon(d))}</p>
  </div>;
}

function OpsStrip({ devices }) {
  const total = devices.length;
  const validCount = devices.filter(valid).length;
  const active = devices.filter((d) => String(d.tripStatus || "").toUpperCase() === "ACTIVE").length;
  const attention = devices.filter((d) => ["s-danger", "s-late"].includes(state(d).cls)).length;
  return <div className="ops-strip">
    <span><Radio size={14} /> {total} reportando</span>
    <span><MapPin size={14} /> {validCount} con ubicación</span>
    <span><Navigation size={14} /> {active} activos</span>
    <span className={attention ? "attention" : ""}><Crosshair size={14} /> {attention} atención</span>
  </div>;
}

export default function LiveDevicesMap({ devices, selectedId, onSelectDevice }) {
  const validDevices = useMemo(() => devices.filter(valid), [devices]);
  const project = useMemo(() => projector(validDevices), [validDevices]);
  const selected = useMemo(() => devices.find((d) => d.id === selectedId) || validDevices.slice().sort((a, b) => state(a).priority - state(b).priority)[0] || null, [devices, selectedId, validDevices]);
  const points = useMemo(() => project ? validDevices.map((d) => ({ d, p: project(d) })) : [], [project, validDevices]);

  return <section className="card live-map-card">
    <div className="live-map-head"><div><h3><MapPin size={18} /> Mapa live simplificado</h3><p>Vista operativa sin API key, alimentada por <b>live_devices</b>.</p></div><div className="map-count"><b>{validDevices.length}</b><span>ubicaciones válidas</span></div></div>
    <OpsStrip devices={devices} />
    <div className="live-map-stage">
      {!validDevices.length ? <div className="live-map-empty">Aún no hay ubicaciones válidas para pintar en el mapa.</div> : <>
        <svg className="live-map-svg" viewBox={`0 0 ${W} ${H}`}>
          <rect width={W} height={H} rx="22" className="map-bg" />
          {Array.from({ length: 11 }).map((_, i) => <g key={i} className="map-grid"><line x1={(W / 10) * i} y1="0" x2={(W / 10) * i} y2={H} /><line x1="0" y1={(H / 10) * i} x2={W} y2={(H / 10) * i} /></g>)}
          <path className="fake-road main" d="M30 285 C190 235 270 310 430 240 S700 195 970 110" />
          <path className="fake-road alt" d="M95 55 C185 135 250 205 380 238 S560 310 855 340" />
          <path className="fake-road alt" d="M30 150 C225 130 380 115 545 145 S800 195 965 175" />
          {points.map(({ d, p }) => <DeviceDot key={d.id} d={d} p={p} selected={d.id === selected?.id} onSelect={onSelectDevice} />)}
        </svg>
        <InfoCard d={selected} />
        <div className="live-map-legend"><span><i className="s-ok" />Vivo</span><span><i className="s-warn" />Reciente</span><span><i className="s-late" />Atrasado</span><span><i className="s-danger" />Atención</span><span><i className="s-muted" />Finalizado</span></div>
      </>}
    </div>
  </section>;
}
