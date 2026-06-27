const EPOCH_MIN_REASONABLE = 100_000_000_000;

function csvEscape(value) {
  if (value === null || value === undefined) return '';
  const s = String(value);
  return /[",\n\r]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
}

function num(value) {
  return Number(value ?? 0) || 0;
}

function first(...values) {
  return values.find((v) => v !== null && v !== undefined && v !== '') ?? '';
}

function validCoord(lat, lon) {
  const a = Number(lat);
  const b = Number(lon);
  return Number.isFinite(a) && Number.isFinite(b) && !(a === 0 && b === 0);
}

function coord(lat, lon) {
  return validCoord(lat, lon) ? `${lat},${lon}` : '';
}

function fmtDate(ms) {
  if (!ms) return '';
  const d = new Date(Number(ms));
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('es-MX');
}

function fmtTime(ms) {
  if (!ms) return '';
  const d = new Date(Number(ms));
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleTimeString('es-MX');
}

function fmtDateTime(ms) {
  if (!ms) return '';
  const d = new Date(Number(ms));
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('es-MX');
}

function fmtDurationMs(ms) {
  if (!Number.isFinite(Number(ms))) return '';
  const totalSec = Math.max(0, Math.floor(Number(ms) / 1000));
  const h = String(Math.floor(totalSec / 3600)).padStart(2, '0');
  const m = String(Math.floor((totalSec % 3600) / 60)).padStart(2, '0');
  const s = String(totalSec % 60).padStart(2, '0');
  return `${h}:${m}:${s}`;
}

function resolveStopEpoch(e) {
  if (num(e.stopTime) >= EPOCH_MIN_REASONABLE) return num(e.stopTime);
  if (num(e.timestamp) >= EPOCH_MIN_REASONABLE) return num(e.timestamp);
  return null;
}

function resolveStartEpoch(e, stopEpoch) {
  const st = num(e.startTime);
  if (st >= EPOCH_MIN_REASONABLE) return st;
  if (stopEpoch && st > 0 && st <= 6 * 60 * 60 * 1000) return stopEpoch + st;
  if (st === 0 && num(e.timestamp) >= EPOCH_MIN_REASONABLE) return num(e.timestamp);
  return null;
}

function menUp(e) { return num(first(e.menUp, e.paxMenUp)); }
function womenUp(e) { return num(first(e.womenUp, e.paxWomenUp)); }
function menDown(e) { return num(first(e.menDown, e.paxMenDown)); }
function womenDown(e) { return num(first(e.womenDown, e.paxWomenDown)); }

function tripValue(trip, ...keys) {
  return first(...keys.map((k) => trip?.[k]));
}

function eventValue(e, ...keys) {
  return first(...keys.map((k) => e?.[k]));
}

function normalizedEvent(e) {
  const stopLat = eventValue(e, 'stopLat', 'lat');
  const stopLon = eventValue(e, 'stopLon', 'lon');
  const startLat = eventValue(e, 'startLat', validCoord(stopLat, stopLon) ? null : 'lat');
  const startLon = eventValue(e, 'startLon', validCoord(stopLat, stopLon) ? null : 'lon');
  const stopEpoch = resolveStopEpoch(e);
  const startEpoch = resolveStartEpoch(e, stopEpoch);
  const upH = menUp(e);
  const upM = womenUp(e);
  const downH = menDown(e);
  const downM = womenDown(e);
  return {
    stopLat,
    stopLon,
    startLat,
    startLon,
    stopEpoch,
    startEpoch,
    menUp: upH,
    womenUp: upM,
    menDown: downH,
    womenDown: downM,
    totalUp: upH + upM,
    totalDown: downH + downM,
  };
}

export function downloadCsv(filename, rows, fields) {
  const csv = [
    fields.join(','),
    ...rows.map((r) => fields.map((f) => csvEscape(r[f])).join(',')),
  ].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function downloadTripEventsCsv(trip, events) {
  const endMs = tripValue(trip, 'endTime') || Date.now();
  const startMs = tripValue(trip, 'startTime');
  let onboard = 0;

  const fields = [
    'ID',
    'No. Recorrido',
    'Ruta / Derrotero',
    'Empresa',
    'Aforador',
    'Supervisor',
    'No. Dispositivo',
    'Fecha',
    'ES / FS',
    'Sentido',
    'Base de inicio',
    'Base final',
    'Hora de inicio',
    'Hora final',
    'Tiempo en recorrido',
    'No. Placa',
    'No. Económico',
    'Tipo de vehículo',
    'Capacidad de asientos',
    'Waypoint de parada',
    'Waypoint de arranque',
    'Coordenada de parada',
    'Coordenada de arranque',
    'Hora de parada',
    'Hora de arranque',
    'Tiempo en demora',
    'Pax. Hombres Suben',
    'Pax. Mujeres Suben',
    'Pax. Hombres bajan',
    'Pax. Mujeres bajan',
    'Total suben',
    'Total bajan',
    'Total a bordo',
    'Tipo de demora',
    'Porta maleta o bulto voluminoso',
    'Observaciones',
    'GPS Status',
    'GPS Accuracy m',
    'GPS Provider',
    'GPS Fix Time',
    'Start GPS Accuracy m',
    'Start GPS Provider',
    'Start GPS Fix Time',
    'Cloud Event ID',
    'Cloud Trip ID',
    'Source',
  ];

  const rows = [...events].sort((a, b) => num(a.timestamp) - num(b.timestamp)).map((e) => {
    const n = normalizedEvent(e);
    onboard = Math.max(0, onboard + n.totalUp - n.totalDown);
    const obsParts = [];
    const notes = eventValue(e, 'notes');
    const other = eventValue(e, 'otherDelayDesc');
    if (notes) obsParts.push(notes);
    if (other) obsParts.push(`Otro: ${other}`);

    return {
      'ID': tripValue(trip, 'localTripId', 'tripId', 'id'),
      'No. Recorrido': tripValue(trip, 'tripNumber', 'routeNumber'),
      'Ruta / Derrotero': tripValue(trip, 'routeName'),
      'Empresa': tripValue(trip, 'company'),
      'Aforador': tripValue(trip, 'aforador', 'observerName'),
      'Supervisor': tripValue(trip, 'supervisor', 'supervisorName'),
      'No. Dispositivo': tripValue(trip, 'deviceNumber', 'deviceInstallationId'),
      'Fecha': fmtDate(startMs),
      'ES / FS': tripValue(trip, 'esFs'),
      'Sentido': tripValue(trip, 'direction'),
      'Base de inicio': tripValue(trip, 'baseStart'),
      'Base final': tripValue(trip, 'baseEnd'),
      'Hora de inicio': fmtTime(startMs),
      'Hora final': tripValue(trip, 'endTime') ? fmtTime(endMs) : '',
      'Tiempo en recorrido': startMs ? fmtDurationMs(Number(endMs) - Number(startMs)) : '',
      'No. Placa': tripValue(trip, 'plateNumber'),
      'No. Económico': tripValue(trip, 'vehicleEco'),
      'Tipo de vehículo': tripValue(trip, 'vehicleType'),
      'Capacidad de asientos': tripValue(trip, 'seatCapacity'),
      'Waypoint de parada': eventValue(e, 'waypointStopId'),
      'Waypoint de arranque': eventValue(e, 'waypointStartId'),
      'Coordenada de parada': coord(n.stopLat, n.stopLon),
      'Coordenada de arranque': coord(n.startLat, n.startLon),
      'Hora de parada': fmtTime(n.stopEpoch),
      'Hora de arranque': fmtTime(n.startEpoch),
      'Tiempo en demora': n.stopEpoch && n.startEpoch ? fmtDurationMs(Number(n.startEpoch) - Number(n.stopEpoch)) : '',
      'Pax. Hombres Suben': n.menUp,
      'Pax. Mujeres Suben': n.womenUp,
      'Pax. Hombres bajan': n.menDown,
      'Pax. Mujeres bajan': n.womenDown,
      'Total suben': n.totalUp,
      'Total bajan': n.totalDown,
      'Total a bordo': onboard,
      'Tipo de demora': eventValue(e, 'delayCodes'),
      'Porta maleta o bulto voluminoso': eventValue(e, 'hasLuggage') ? 1 : 0,
      'Observaciones': obsParts.join(' | '),
      'GPS Status': eventValue(e, 'locationStatus'),
      'GPS Accuracy m': eventValue(e, 'stopAccM', 'accuracy'),
      'GPS Provider': eventValue(e, 'stopProvider', 'provider'),
      'GPS Fix Time': fmtDateTime(eventValue(e, 'stopFixTime', 'timestamp')),
      'Start GPS Accuracy m': eventValue(e, 'startAccM'),
      'Start GPS Provider': eventValue(e, 'startProvider'),
      'Start GPS Fix Time': fmtDateTime(eventValue(e, 'startFixTime', 'startTime')),
      'Cloud Event ID': eventValue(e, 'cloudEventId', 'eventId', 'id'),
      'Cloud Trip ID': tripValue(trip, 'cloudTripId', 'id'),
      'Source': eventValue(e, 'source'),
    };
  });

  downloadCsv(`urban_trip_${tripValue(trip, 'cloudTripId', 'id', 'tripId')}_events.csv`, rows, fields);
}
