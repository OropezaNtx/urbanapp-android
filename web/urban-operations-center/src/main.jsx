import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { RefreshCw, Download, ArrowLeft, Bus, Users, MapPin, Activity } from 'lucide-react';
import { fetchDevices, fetchTripDetail, fetchTrips } from './services/firestore';
import { downloadTripEventsCsv } from './exporters/csv';
import TrackSummary, { buildTrackMetrics } from './components/TrackSummary';
import TripMap from './components/TripMap';
import TripInsights from './components/TripInsights';
import './styles.css';

const fmt = (v) => {
  if (!v) return '—';
  const d = new Date(Number(v));
  return isNaN(d.getTime()) ? String(v) : d.toLocaleString('es-MX');
};

const val = (v) => v === null || v === undefined || v === '' ? '—' : String(v);
const num = (v) => Number(v ?? 0) || 0;
const closed = (t) => Boolean(t.endTime) || String(t.esFs || '').toUpperCase().includes('FS');

// Compatibilidad entre esquema Room/backup y esquema Cloud DTO.
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

function eventStartLat(e) { return e.startLat ?? e.lat ?? e.stopLat; }
function eventStartLon(e) { return e.startLon ?? e.lon ?? e.stopLon; }
function eventStopLat(e) { return e.stopLat ?? e.lat ?? e.startLat; }
function eventStopLon(e) { return e.stopLon ?? e.lon ?? e.startLon; }
function eventAccuracy(e) { return e.stopAccM ?? e.accuracy ?? e.startAccM; }
function eventGps(e) { return e.locationStatus ?? e.stopProvider ?? e.provider ?? e.startProvider; }

function Stat({ label, value }) {
  return <div className="stat"><b>{value}</b><span>{label}</span></div>;
}

function Field({ label, value }) {
  return <div className="field"><span>{label}</span><b>{val(value)}</b></div>;
}

function App() {
  const [view, setView] = useState('dashboard');
  const [trips, setTrips] = useState([]);
  const [devices, setDevices] = useState([]);
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
  }), [trips, devices]);

  const allEvents = detail?.events || [];
  const trackChunks = detail?.trackChunks || detail?.trackSummary || [];
  const track = useMemo(() => buildTrackMetrics(trackChunks), [trackChunks]);

  const pax = allEvents.reduce((a, e) => ({
    up: a.up + totalUp(e),
    down: a.down + totalDown(e),
  }), { up: 0, down: 0 });

  return <div className="app">
    <header>
      <div>
        <h1>Urban Operations Center</h1>
        <p>Firestore: cloud trips · events · track_chunks · installations</p>
      </div>
      <button onClick={load}><RefreshCw size={16} />Actualizar</button>
    </header>

    {err && <div className="error">{err}</div>}

    <nav>
      <button onClick={() => setView('dashboard')}>Dashboard</button>
      <button onClick={() => setView('trips')}>Trips</button>
      <button onClick={() => setView('devices')}>Live Devices</button>
    </nav>

    {view === 'dashboard' && <main>
      <section className="grid stats">
        <Stat label="Viajes" value={stats.total} />
        <Stat label="Activos" value={stats.active} />
        <Stat label="Cerrados" value={stats.closed} />
        <Stat label="Dispositivos" value={stats.devices} />
      </section>
      <h2>Últimos viajes</h2>
      <TripsTable trips={trips.slice(0, 10)} onOpen={openTrip} />
    </main>}

    {view === 'trips' && <main>
      <h2>Trips</h2>
      <TripsTable trips={trips} onOpen={openTrip} />
    </main>}

    {view === 'devices' && <main>
      <h2>Live Devices</h2>
      <DevicesTable devices={devices} />
    </main>}

    {view === 'detail' && <main>
      <button className="ghost" onClick={() => setView('trips')}><ArrowLeft size={16} />Regresar</button>
      <h2>Trip Detail #{val(selected?.localTripId || selected?.tripId || selected?.id)}</h2>

      <section className="grid stats">
        <Stat label="Eventos" value={allEvents.length} />
        <Stat label="Subidas" value={pax.up} />
        <Stat label="Bajadas" value={pax.down} />
        <Stat label="Puntos GPS" value={track.totalPoints} />
      </section>

      <section className="card">
        <h3><Bus size={18} /> Encabezado</h3>
        <div className="fields">
          <Field label="Ruta" value={selected?.routeName} />
          <Field label="Ruta núm." value={selected?.routeNumber ?? selected?.tripNumber} />
          <Field label="Planning" value={selected?.planningRouteId ?? selected?.routeId} />
          <Field label="Dirección" value={selected?.direction} />
          <Field label="Empresa" value={selected?.company} />
          <Field label="Aforador" value={selected?.aforador ?? selected?.observerName} />
          <Field label="Supervisor" value={selected?.supervisor ?? selected?.supervisorName} />
          <Field label="Sexo obs." value={selected?.observerSex} />
          <Field label="Dispositivo" value={selected?.deviceNumber ?? selected?.deviceInstallationId} />
          <Field label="Eco" value={selected?.vehicleEco} />
          <Field label="Placas" value={selected?.plateNumber} />
          <Field label="Tipo" value={selected?.vehicleType} />
          <Field label="Capacidad" value={selected?.seatCapacity} />
          <Field label="Base inicio" value={selected?.baseStart} />
          <Field label="Base fin" value={selected?.baseEnd} />
          <Field label="Estado ES/FS" value={selected?.esFs} />
          <Field label="Inicio" value={fmt(selected?.startTime)} />
          <Field label="Fin" value={fmt(selected?.endTime)} />
          <Field label="Next WP" value={selected?.nextWaypointId} />
          <Field label="Notas" value={selected?.notes} />
        </div>
      </section>

      <section className="card">
        <h3><MapPin size={18} /> Mapa del recorrido</h3>
        <TripMap chunks={trackChunks} events={allEvents} />
      </section>

      <section className="card">
        <h3><Activity size={18} /> Inteligencia operacional</h3>
        <TripInsights trip={selected} events={allEvents} chunks={trackChunks} />
      </section>

      <section className="card">
        <div className="row">
          <h3><Users size={18} /> Eventos</h3>
          <button onClick={() => downloadTripEventsCsv(selected, allEvents)}><Download size={16} />CSV eventos</button>
        </div>
        <EventsTable events={allEvents} />
      </section>

      <section className="card">
        <h3><MapPin size={18} /> Track summary</h3>
        <TrackSummary chunks={trackChunks} />
      </section>
    </main>}

    {loading && <div className="loading">Cargando...</div>}
  </div>;
}

function TripsTable({ trips, onOpen }) {
  return <div className="table"><table>
    <thead><tr><th>Trip</th><th>Ruta</th><th>Dir</th><th>Aforador</th><th>Unidad</th><th>Inicio</th><th>Fin</th><th>Estado</th></tr></thead>
    <tbody>{trips.map(t => <tr key={t.id} onClick={() => onOpen(t)}>
      <td>{val(t.localTripId || t.tripId || t.id)}</td>
      <td>{val(t.routeName)}</td>
      <td>{val(t.direction)}</td>
      <td>{val(t.aforador ?? t.observerName)}</td>
      <td>{val(t.vehicleEco)} / {val(t.plateNumber)}</td>
      <td>{fmt(t.startTime)}</td>
      <td>{fmt(t.endTime)}</td>
      <td>{closed(t) ? 'Cerrado' : 'Activo'}</td>
    </tr>)}</tbody>
  </table></div>;
}

function DevicesTable({ devices }) {
  return <div className="table"><table>
    <thead><tr><th>ID</th><th>Número</th><th>Última ubicación</th><th>Batería</th><th>GPS</th><th>Heartbeat</th><th>Trip activo</th></tr></thead>
    <tbody>{devices.map(d => <tr key={d.id}>
      <td>{d.id}</td>
      <td>{val(d.deviceNumber || d.number)}</td>
      <td>{val(d.lat)}, {val(d.lon)}</td>
      <td>{val(d.battery || d.batteryPct)}</td>
      <td>{val(d.gps || d.locationStatus)}</td>
      <td>{fmt(d.lastHeartbeatAt || d.heartbeatAt || d.timestamp)}</td>
      <td>{val(d.activeTripId)}</td>
    </tr>)}</tbody>
  </table></div>;
}

function EventsTable({ events }) {
  return <div className="table"><table>
    <thead><tr>
      <th>#</th>
      <th>Tipo</th>
      <th>Hora</th>
      <th>WP Start</th>
      <th>WP Stop</th>
      <th>Start lat/lon</th>
      <th>Stop lat/lon</th>
      <th>Suben</th>
      <th>Bajan</th>
      <th>H/M Suben</th>
      <th>H/M Bajan</th>
      <th>GPS</th>
      <th>Precisión</th>
      <th>Notas</th>
      <th>Fuente</th>
    </tr></thead>
    <tbody>{events.map(e => <tr key={e.id}>
      <td>{val(e.eventId || e.cloudEventId || e.id)}</td>
      <td>{eventLabel(e)}</td>
      <td>{fmt(e.timestamp || e.stopTime || e.startTime)}</td>
      <td>{val(e.waypointStartId)}</td>
      <td>{val(e.waypointStopId)}</td>
      <td>{val(eventStartLat(e))}, {val(eventStartLon(e))}</td>
      <td>{val(eventStopLat(e))}, {val(eventStopLon(e))}</td>
      <td>{totalUp(e)}</td>
      <td>{totalDown(e)}</td>
      <td>{menUp(e)} / {womenUp(e)}</td>
      <td>{menDown(e)} / {womenDown(e)}</td>
      <td>{val(eventGps(e))}</td>
      <td>{val(eventAccuracy(e))}</td>
      <td>{val(e.notes || e.otherDelayDesc)}</td>
      <td>{val(e.source)}</td>
    </tr>)}</tbody>
  </table></div>;
}

createRoot(document.getElementById('root')).render(<App />);
