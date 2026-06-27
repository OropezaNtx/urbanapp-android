const EVENT_FIELDS = [
  'tripId', 'eventId', 'uid', 'eventType', 'delayCodes', 'stopType', 'eventLabel',
  'timestamp', 'startTime', 'stopTime',
  'waypointStartId', 'waypointStopId',
  'lat', 'lon', 'alt', 'accuracy',
  'startLat', 'startLon', 'startAltM', 'startAccM', 'startProvider',
  'stopLat', 'stopLon', 'stopAltM', 'stopAccM', 'stopProvider',
  'normalizedStartLat', 'normalizedStartLon', 'normalizedStopLat', 'normalizedStopLon', 'normalizedAccuracy',
  'stopName', 'locationStatus', 'provider',
  'menUp', 'womenUp', 'menDown', 'womenDown',
  'paxMenUp', 'paxWomenUp', 'paxMenDown', 'paxWomenDown',
  'normalizedMenUp', 'normalizedWomenUp', 'normalizedMenDown', 'normalizedWomenDown',
  'totalUp', 'totalDown',
  'onboardMen', 'onboardWomen', 'hasLuggage',
  'notes', 'otherDelayDesc', 'backupCreatedAt', 'backupSource', 'source'
];

const TRIP_FIELDS = [
  'tripId', 'routeName', 'routeNumber', 'planningRouteId', 'direction', 'company', 'aforador', 'supervisor',
  'deviceNumber', 'vehicleEco', 'plateNumber', 'vehicleType', 'seatCapacity', 'baseStart', 'baseEnd',
  'startTime', 'endTime', 'esFs', 'nextWaypointId', 'observerSex', 'notes'
];

function csvEscape(value) {
  if (value === null || value === undefined) return '';
  const s = String(value);
  return /[",\n\r]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
}

function num(value) {
  return Number(value ?? 0) || 0;
}

function menUp(e) { return num(e.menUp ?? e.paxMenUp); }
function womenUp(e) { return num(e.womenUp ?? e.paxWomenUp); }
function menDown(e) { return num(e.menDown ?? e.paxMenDown); }
function womenDown(e) { return num(e.womenDown ?? e.paxWomenDown); }

function eventLabel(e) {
  const type = String(e.eventType ?? e.stopType ?? '').trim();
  const delay = String(e.delayCodes ?? '').trim();
  if (type && delay && !type.includes(delay)) return `${type} + ${delay}`;
  return type || delay || '';
}

function normalizeEvent(e) {
  const normalizedMenUp = menUp(e);
  const normalizedWomenUp = womenUp(e);
  const normalizedMenDown = menDown(e);
  const normalizedWomenDown = womenDown(e);

  return {
    ...e,
    eventLabel: eventLabel(e),
    normalizedStartLat: e.startLat ?? e.lat ?? e.stopLat ?? '',
    normalizedStartLon: e.startLon ?? e.lon ?? e.stopLon ?? '',
    normalizedStopLat: e.stopLat ?? e.lat ?? e.startLat ?? '',
    normalizedStopLon: e.stopLon ?? e.lon ?? e.startLon ?? '',
    normalizedAccuracy: e.stopAccM ?? e.accuracy ?? e.startAccM ?? '',
    normalizedMenUp,
    normalizedWomenUp,
    normalizedMenDown,
    normalizedWomenDown,
    totalUp: normalizedMenUp + normalizedWomenUp,
    totalDown: normalizedMenDown + normalizedWomenDown,
  };
}

export function downloadCsv(filename, rows, fields) {
  const csv = [
    fields.join(','),
    ...rows.map(r => fields.map(f => csvEscape(r[f])).join(','))
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
  const tripPrefix = Object.fromEntries(TRIP_FIELDS.map(f => [`trip_${f}`, trip?.[f] ?? '']));
  const rows = events.map(e => ({ ...tripPrefix, ...normalizeEvent(e) }));
  downloadCsv(`urban_trip_${trip?.tripId ?? trip?.id}_events.csv`, rows, [...Object.keys(tripPrefix), ...EVENT_FIELDS]);
}
