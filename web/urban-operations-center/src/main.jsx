import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { RefreshCw, Download, ArrowLeft, Bus, Users, MapPin, Activity, ShieldCheck } from 'lucide-react';
import { fetchDevices, fetchTripDetail, fetchTrips } from './services/firestore';
import { subscribeLiveDevices } from './services/liveDevices';
import { subscribeInstallationsHealth } from './services/installations';
import { downloadTripEventsCsv } from './exporters/csv';
import { buildTrackMetrics } from './components/TrackSummary';
import TripMap from './components/TripMap';
import TripInsights from './components/TripInsights';
import TripExportPanel from './components/TripExportPanel';
import TripPlayback from './components/TripPlayback';
import LiveDevicesPanel from './components/LiveDevicesPanel';
import LiveDevicesMap from './components/LiveDevicesMap';
import FleetHealthPanel from './components/FleetHealthPanel';
import InstallationsPanel from './components/InstallationsPanel';
import IntegrityDashboard from './components/IntegrityDashboard';
import './styles.css';

const toMillis = (v) => {
  if (!v) return 0;
  if (typeof v === 'number') return v;
  if (v.toMillis) return v.toMillis();
  if (v.seconds) return v.seconds * 1000;
  const parsed = new Date(v).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
};

const fmt = (v) => {
  const ms = toMillis(v);
  if (!ms) return '—';
  const d = new Date(ms);
  return isNaN(d.getTime()) ? String(v) : d.toLocaleString('es-MX');
};

const val = (v) => v === null || v === undefined || v === '' ? '—' : String(v);
const num = (v) => Number(v ?? 0) || 0;
const closed = (t) => {
  const status = String(t?.status || '').toUpperCase();
  if (status === 'ACTIVE') return false;
  if (status === 'CLOSED') return true;
  return Boolean(t?.endTime);
};

const menUp = (e) => num(e.menUp ?? e.paxMenUp);
const womenUp = (e) => num(e.womenUp ?? e.paxWomenUp);
const menDown = (e) => num(e.menDown ?? e.paxMenDown);
const womenDown = (e) => num(e.womenDown ?? e.paxWomenDown);
const totalUp = (e) => menUp(e) + womenUp(e);
const totalDown = (e) => menDown(e) + womenDown(e);

const eventType = (e) => e.eventType ?? e.stopType ?? '';
const eventDelay = (e) => e.delayCodes ?? '';

function eventLabel(e) {
  const type = String(eventType(e) || '').trim();
  const delay = String(eventDelay(e) || '').trim();
  if (type && delay && !type.includes(delay)) return `${type} + ${delay}`;
  return type || delay || '—';
}

function eventAccuracy(e) { return e.stopAccM ?? e.accuracy ?? e.startAccM; }
function eventGps(e) { return e.locationStatus ?? e.stopProvider ?? e.provider ?? e.startProvider; }

function Stat({ label, value }) {
  return <div className="stat"><b>{value}</b><span>{label}</span></div>;
}

function Field({ label, value }) {
  return <div className="field"><span>{label}</span><b>{val(value)}</b></div>;
}

function liveIsActive(d) {
  const status = String(d?.tripStatus || '').toUpperCase();
  return status === 'ACTIVE';
}

function App() {
  const [view, setView] = useState('dashboard');
  const [trips, setTrips] = useState([]);
  const [devices, setDevices] = useState([]);
  const [liveDevices, setLiveDevices] = useState([]);
  const [installationsHealth, setInstallationsHealth] = useState([]);
  const [selectedLiveId, setSelectedLiveId] = useState(null);
  const [selected, setSelected] = useState(null);
  const [detail, setDetail] = useState(null);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    setErr('');
    try {
      const [t, d] = await Promise.all([fetchTrips(), fetchDevices().catch(() => [])]);
      setTrips(t);
      setDevices(d);
    } catch (e) {
      setErr(e.message || String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  useEffect(() => {
    return subscribeLiveDevices(
      setLiveDevices,
      (e) => setErr(e.message || String(e))
    );
  }, []);

  useEffect(() => {
    return subscribeInstallationsHealth(
      setInstallationsHealth,
      (e) => setErr(e.message || String(e))
    );
  }, []);

  async function openTrip(t) {
    setSelected(t);
    setView('detail');
    setLoading(true);
    setErr('');
    try {
      setDetail(await fetchTripDetail(t.id, t.tripId));
    } catch (e) {
      setErr(e.message || String(e));
    } finally {
      setLoading(false);
    }
  }

  const stats = useMemo(() => ({
    total: trips.length,
    active: trips.filter(t => !closed(t)).length,
    closed: trips.filter(closed).length,
    devices: devices.length,
    live: liveDevices.length,
    liveActive: liveDevices.filter(liveIsActive).length,
    installations: installationsHealth.length,
  }), [trips, devices, liveDevices, installationsHealth]);

  const allEvents = detail?.events || [];
  const trackChunks = detail?.trackChunks || detail?.trackSummary || [];
  const track = useMemo(() => buildTrackMetrics(trackChunks), [trackChunks]);
  const completeness = detail?.completeness;

  const pax = allEvents.reduce((a, e) => ({
    up: a.up + totalUp(e),
    down: a.down + totalDown(e),
  }), { up: 0, down: 0 });

  return <div className="app">
    <header>
      <div>
        <h1>Centro de Operaciones Afora</h1>
        <p>Centro de mando operativo para levantamientos de movilidad.</p>
      </div>
      <button onClick={load}><RefreshCw size={16} />Actualizar</button>
    </header>

    {err && <div className="error">{err}</div>}

    <nav>
      <button onClick={() => setView('dashboard')}>Dashboard</button>
      <button onClick={() => setView('trips')}>Levantamientos</button>
      <button onClick={() => setView('integrity')}>Integridad</button>
      <button onClick={() => setView('devices')}>Equipos en Vivo</button>
      <button onClick={() => setView('installations')}>Licencias</button>
      <button onClick={() => setView('fleet')}>Estado de Flota</button>
    </nav>

    {view === 'dashboard' && <main>
      <section className="grid stats">
        <Stat label="Levantamientos Totales" value={stats.total} />
        <Stat label="En Curso" value={stats.active} />
        <Stat label="Reportando GPS" value={stats.liveActive} />
        <Stat label="Equipos Activos" value={stats.installations} />
      </section>
      <h2>Levantamientos Recientes</h2>
      <TripsTable trips={trips.slice(0, 10)} onOpen={openTrip} />
    </main>}

    {view === 'trips' && <main>
      <h2>Historial de Levantamientos</h2>
      <TripsTable trips={trips} onOpen={openTrip} />
    </main>}

    {view === 'integrity' && <main>
      <IntegrityDashboard trips={trips} onOpenTrip={openTrip} />
    </main>}

    {view === 'devices' && <main>
      <h2>Equipos en Vivo</h2>
      <LiveDevicesMap devices={liveDevices} selectedId={selectedLiveId} onSelectDevice={setSelectedLiveId} />
      <LiveDevicesPanel devices={liveDevices} />
      <section className="card">
        <h3><Activity size={18} /> Equipos Registrados</h3>
        <DevicesTable devices={devices} />
      </section>
    </main>}

    {view === 'installations' && <main>
      <h2>Control de Licencias</h2>
      <InstallationsPanel installations={installationsHealth} />
    </main>}

    {view === 'fleet' && <main>
      <h2>Estado de la Flota</h2>
      <FleetHealthPanel installations={installationsHealth} />
    </main>}

    {view === 'detail' && <main>
      <button className="ghost" onClick={() => setView('trips')}><ArrowLeft size={16} />Regresar</button>
      <h2>Detalle del Levantamiento</h2>

      <section className="grid stats">
        <Stat label="Eventos" value={allEvents.length} />
        <Stat label="Subidas" value={pax.up} />
        <Stat label="Bajadas" value={pax.down} />
        <Stat label="Registros GPS utilizables" value={track.totalPoints} />
      </section>

      {completeness && <section className="card">
        <h3><ShieldCheck size={18} /> Integridad Cloud</h3>
        <div className="fields">
          <Field label="Estado" value={completeness.state} />
          <Field label="Eventos" value={completeness.eventCount} />
          <Field label="Chunks" value={completeness.chunkCount} />
          <Field label="Puntos cloud" value={completeness.rawPointCount} />
          <Field label="GPS utilizables" value={completeness.validGpsPointCount} />
          <Field label="GPS filtrados" value={completeness.invalidGpsPointCount} />
          <Field label="Delta payload" value={completeness.pointPayloadDelta} />
          <Field label="Calidad GPS" value={completeness.qualityState} />
        </div>
      </section>}

      <section className="card"><h3><Bus size={18} /> Datos de Cabecera</h3><div className="fields">
        <Field label="Ruta" value={selected?.routeName} />
        <Field label="Folio" value={selected?.routeNumber ?? selected?.tripNumber} />
        <Field label="ID Planificación" value={selected?.planningRouteId ?? selected?.routeId} />
        <Field label="Sentido" value={selected?.direction} />
        <Field label="Empresa" value={selected?.company} />
        <Field label="Operador" value={selected?.aforador ?? selected?.observerName} />
        <Field label="Supervisor" value={selected?.supervisor ?? selected?.supervisorName} />
        <Field label="Identificador de Equipo" value={selected?.deviceNumber ?? selected?.deviceInstallationId} />
        <Field label="Eco" value={selected?.vehicleEco} />
        <Field label="Placas" value={selected?.plateNumber} />
        <Field label="Tipo Unidad" value={selected?.vehicleType} />
        <Field label="Capacidad" value={selected?.seatCapacity} />
        <Field label="Punto de Inicio" value={selected?.baseStart} />
        <Field label="Punto de Término" value={selected?.baseEnd} />
        <Field label="Inicio" value={fmt(selected?.startTime)} />
        <Field label="Fin" value={fmt(selected?.endTime)} />
        <Field label="Notas" value={selected?.notes} />
      </div></section>

      <section className="card"><h3><MapPin size={18} /> Mapa del Recorrido</h3><TripMap chunks={trackChunks} events={allEvents} /></section>
      <section className="card"><h3><Activity size={18} /> Reproducción (Playback)</h3><TripPlayback chunks={trackChunks} events={allEvents} /></section>
      <section className="card"><h3><Download size={18} /> Exportación de Datos</h3><TripExportPanel trip={selected} events={allEvents} chunks={trackChunks} /></section>
      <section className="card"><h3><Activity size={18} /> Análisis Operativo</h3><TripInsights trip={selected} events={allEvents} chunks={trackChunks} /></section>
      <section className="card"><div className="row"><h3><Users size={18} /> Registro de Eventos</h3><button onClick={() => downloadTripEventsCsv(selected, allEvents)}><Download size={16} />Descargar CSV</button></div><EventsTable events={allEvents} /></section>
    </main>}

    {loading && <div className="loading">Procesando...</div>}
  </div>;
}

function TripsTable({ trips, onOpen }) {
  return <div className="table"><table><thead><tr><th>Folio</th><th>Ruta</th><th>Dir</th><th>Operador</th><th>Unidad</th><th>Inicio</th><th>Fin</th><th>Estado</th></tr></thead><tbody>{trips.map(t => <tr key={t.id} onClick={() => onOpen(t)}><td>{val(t.localTripId || t.routeNumber || t.tripId || t.id)}</td><td>{val(t.routeName)}</td><td>{val(t.direction)}</td><td>{val(t.aforador ?? t.observerName)}</td><td>{val(t.vehicleEco)} / {val(t.plateNumber)}</td><td>{fmt(t.startTime)}</td><td>{fmt(t.endTime)}</td><td>{closed(t) ? 'Completado' : 'Activo'}</td></tr>)}</tbody></table></div>;
}

function DevicesTable({ devices }) {
  return <div className="table"><table><thead><tr><th>Equipo</th><th>Batería</th><th>GPS</th><th>Último Reporte</th><th>Acción Actual</th></tr></thead><tbody>{devices.map(d => <tr key={d.id}><td><b>{val(d.deviceNumber || d.number)}</b><br/><small>{d.id.substring(0,8)}</small></td><td>{val(d.battery || d.batteryPct)}%</td><td>{val(d.gps || d.locationStatus)}</td><td>{fmt(d.lastHeartbeatAt || d.heartbeatAt || d.timestamp)}</td><td>{val(d.activeTripId)}</td></tr>)}</tbody></table></div>;
}

function EventsTable({ events }) {
  return <div className="table"><table><thead><tr><th>#</th><th>Tipo</th><th>Hora</th><th>Suben</th><th>Bajan</th><th>Total H/M</th><th>GPS</th><th>Precisión</th><th>Notas</th></tr></thead><tbody>{events.map(e => <tr key={e.id}><td>{val(e.eventId || e.cloudEventId || e.id)}</td><td>{eventLabel(e)}</td><td>{fmt(e.timestamp || e.stopTime || e.startTime)}</td><td>{totalUp(e)}</td><td>{totalDown(e)}</td><td>{menUp(e)} / {womenUp(e)}</td><td>{val(eventGps(e))}</td><td>{val(eventAccuracy(e))}m</td><td>{val(e.notes || e.otherDelayDesc)}</td></tr>)}</tbody></table></div>;
}

createRoot(document.getElementById('root')).render(<App />);
