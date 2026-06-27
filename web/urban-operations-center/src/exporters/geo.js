import { buildTrackMetrics } from '../components/TrackSummary';

const EPOCH_MIN_REASONABLE = 100_000_000_000;

function safe(value) {
  return value === null || value === undefined ? '' : String(value);
}

function xmlEscape(value) {
  return safe(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

function csvEscape(value) {
  const s = safe(value);
  return /[",\n\r]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
}

function num(value) {
  return Number(value ?? 0) || 0;
}

function validCoord(lat, lon) {
  const a = Number(lat);
  const b = Number(lon);
  return Number.isFinite(a) && Number.isFinite(b) && !(a === 0 && b === 0);
}

function eventLat(e) { return e?.stopLat ?? e?.lat ?? e?.startLat; }
function eventLon(e) { return e?.stopLon ?? e?.lon ?? e?.startLon; }
function eventType(e) { return e?.eventType ?? e?.stopType ?? 'EVENT'; }
function eventDelay(e) { return e?.delayCodes ?? ''; }
function eventTime(e) { return e?.timestamp ?? e?.stopTime ?? e?.startTime ?? e?.createdAt; }
function menUp(e) { return num(e?.menUp ?? e?.paxMenUp); }
function womenUp(e) { return num(e?.womenUp ?? e?.paxWomenUp); }
function menDown(e) { return num(e?.menDown ?? e?.paxMenDown); }
function womenDown(e) { return num(e?.womenDown ?? e?.paxWomenDown); }
function totalUp(e) { return menUp(e) + womenUp(e); }
function totalDown(e) { return menDown(e) + womenDown(e); }

function eventLabel(e) {
  const type = String(eventType(e) || '').trim();
  const delay = String(eventDelay(e) || '').trim();
  if (type && delay && !type.includes(delay)) return `${type} + ${delay}`;
  return type || delay || 'EVENT';
}

function iso(ms) {
  const n = Number(ms);
  if (!Number.isFinite(n) || n < EPOCH_MIN_REASONABLE) return '';
  const d = new Date(n);
  return Number.isNaN(d.getTime()) ? '' : d.toISOString();
}

function fmtLocal(ms) {
  const n = Number(ms);
  if (!Number.isFinite(n) || n < EPOCH_MIN_REASONABLE) return '';
  const d = new Date(n);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('es-MX');
}

function downloadText(filename, content, mime) {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function tripId(trip) {
  return safe(trip?.cloudTripId || trip?.localTripId || trip?.tripId || trip?.id || 'trip');
}

function tripName(trip) {
  return safe(trip?.routeName || `Urban trip ${tripId(trip)}`);
}

function collectTrackPoints(chunks = []) {
  return buildTrackMetrics(Array.isArray(chunks) ? chunks : []).points;
}

function collectEventPoints(events = []) {
  return (Array.isArray(events) ? events : [])
    .filter((e) => validCoord(eventLat(e), eventLon(e)))
    .sort((a, b) => num(eventTime(a)) - num(eventTime(b)));
}

function gpxWaypoint(e, idx) {
  const lat = eventLat(e);
  const lon = eventLon(e);
  const time = iso(eventTime(e));
  const name = `${idx + 1}. ${eventLabel(e)}`;
  const desc = [
    `WP ${safe(e.waypointStartId)} → ${safe(e.waypointStopId)}`,
    `Suben ${totalUp(e)} (${menUp(e)}/${womenUp(e)})`,
    `Bajan ${totalDown(e)} (${menDown(e)}/${womenDown(e)})`,
    `GPS ${safe(e.locationStatus || e.stopProvider || e.provider || e.startProvider)}`,
    `Precisión ${safe(e.stopAccM ?? e.accuracy ?? e.startAccM)} m`,
    e.notes ? `Notas: ${e.notes}` : '',
    e.otherDelayDesc ? `Otro: ${e.otherDelayDesc}` : '',
  ].filter(Boolean).join(' | ');

  return [
    `  <wpt lat="${xmlEscape(lat)}" lon="${xmlEscape(lon)}">`,
    `    <name>${xmlEscape(name)}</name>`,
    time ? `    <time>${time}</time>` : '',
    `    <desc>${xmlEscape(desc)}</desc>`,
    `    <type>${xmlEscape(eventType(e))}</type>`,
    '  </wpt>',
  ].filter(Boolean).join('\n');
}

export function buildGpx(trip, events = [], chunks = []) {
  const points = collectTrackPoints(chunks);
  const eventPoints = collectEventPoints(events);
  const trkpts = points.map((p) => [
    `      <trkpt lat="${xmlEscape(p.lat)}" lon="${xmlEscape(p.lon)}">`,
    p.alt !== undefined && p.alt !== null ? `        <ele>${xmlEscape(p.alt)}</ele>` : '',
    iso(p.time) ? `        <time>${iso(p.time)}</time>` : '',
    p.provider ? `        <desc>${xmlEscape(`${p.provider} · ${safe(p.accuracy)} m`)}</desc>` : '',
    '      </trkpt>',
  ].filter(Boolean).join('\n')).join('\n');

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<gpx version="1.1" creator="Urban Operations Center" xmlns="http://www.topografix.com/GPX/1/1">',
    `  <metadata><name>${xmlEscape(tripName(trip))}</name><desc>${xmlEscape(`UrbanApp ASD trip ${tripId(trip)}`)}</desc></metadata>`,
    ...eventPoints.map(gpxWaypoint),
    '  <trk>',
    `    <name>${xmlEscape(tripName(trip))}</name>`,
    '    <trkseg>',
    trkpts,
    '    </trkseg>',
    '  </trk>',
    '</gpx>',
  ].join('\n');
}

function kmlPointPlacemark(e, idx) {
  const name = `${idx + 1}. ${eventLabel(e)}`;
  const desc = [
    `Hora: ${fmtLocal(eventTime(e))}`,
    `WP: ${safe(e.waypointStartId)} → ${safe(e.waypointStopId)}`,
    `Suben: ${totalUp(e)} (${menUp(e)}/${womenUp(e)})`,
    `Bajan: ${totalDown(e)} (${menDown(e)}/${womenDown(e)})`,
    `GPS: ${safe(e.locationStatus || e.stopProvider || e.provider || e.startProvider)}`,
    `Precisión: ${safe(e.stopAccM ?? e.accuracy ?? e.startAccM)} m`,
    e.notes ? `Notas: ${e.notes}` : '',
    e.otherDelayDesc ? `Otro: ${e.otherDelayDesc}` : '',
  ].filter(Boolean).join('\n');
  const type = String(eventType(e)).toUpperCase();
  const delay = String(eventDelay(e)).trim();
  const style = type.includes('BANDERA') ? '#flagStyle' : (type.includes('DEMORA') || delay ? '#delayStyle' : '#asdStyle');

  return [
    '    <Placemark>',
    `      <name>${xmlEscape(name)}</name>`,
    `      <styleUrl>${style}</styleUrl>`,
    `      <description><![CDATA[${desc.replaceAll(']]>', ']]&gt;')}]]></description>`,
    `      <Point><coordinates>${xmlEscape(eventLon(e))},${xmlEscape(eventLat(e))},0</coordinates></Point>`,
    '    </Placemark>',
  ].join('\n');
}

export function buildKml(trip, events = [], chunks = []) {
  const points = collectTrackPoints(chunks);
  const eventPoints = collectEventPoints(events);
  const coordinates = points.map((p) => `${p.lon},${p.lat},${p.alt ?? 0}`).join(' ');

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<kml xmlns="http://www.opengis.net/kml/2.2">',
    '  <Document>',
    `    <name>${xmlEscape(tripName(trip))}</name>`,
    '    <Style id="trackStyle"><LineStyle><color>ffff6325</color><width>5</width></LineStyle></Style>',
    '    <Style id="asdStyle"><IconStyle><color>ffff6325</color><scale>1.1</scale></IconStyle></Style>',
    '    <Style id="delayStyle"><IconStyle><color>ff1697f9</color><scale>1.1</scale></IconStyle></Style>',
    '    <Style id="flagStyle"><IconStyle><color>ff4aa316</color><scale>1.1</scale></IconStyle></Style>',
    '    <Placemark>',
    `      <name>${xmlEscape(`Recorrido · ${tripName(trip)}`)}</name>`,
    '      <styleUrl>#trackStyle</styleUrl>',
    '      <LineString>',
    '        <tessellate>1</tessellate>',
    `        <coordinates>${coordinates}</coordinates>`,
    '      </LineString>',
    '    </Placemark>',
    ...eventPoints.map(kmlPointPlacemark),
    '  </Document>',
    '</kml>',
  ].join('\n');
}

export function buildGeoJson(trip, events = [], chunks = []) {
  const points = collectTrackPoints(chunks);
  const eventPoints = collectEventPoints(events);
  const features = [];

  if (points.length) {
    features.push({
      type: 'Feature',
      properties: {
        kind: 'track',
        name: tripName(trip),
        tripId: tripId(trip),
        pointCount: points.length,
      },
      geometry: {
        type: 'LineString',
        coordinates: points.map((p) => [Number(p.lon), Number(p.lat), Number(p.alt ?? 0)]),
      },
    });
  }

  eventPoints.forEach((e, idx) => {
    features.push({
      type: 'Feature',
      properties: {
        kind: 'event',
        index: idx + 1,
        label: eventLabel(e),
        eventType: eventType(e),
        delayCodes: eventDelay(e),
        time: iso(eventTime(e)),
        waypointStartId: e.waypointStartId ?? null,
        waypointStopId: e.waypointStopId ?? null,
        menUp: menUp(e),
        womenUp: womenUp(e),
        menDown: menDown(e),
        womenDown: womenDown(e),
        totalUp: totalUp(e),
        totalDown: totalDown(e),
        gpsStatus: e.locationStatus ?? null,
        accuracy: e.stopAccM ?? e.accuracy ?? e.startAccM ?? null,
        notes: e.notes ?? null,
      },
      geometry: {
        type: 'Point',
        coordinates: [Number(eventLon(e)), Number(eventLat(e)), 0],
      },
    });
  });

  return JSON.stringify({
    type: 'FeatureCollection',
    name: tripName(trip),
    features,
  }, null, 2);
}

export function buildTrackCsv(trip, events = [], chunks = []) {
  const fields = ['tripId', 'routeName', 'idx', 'time', 'lat', 'lon', 'alt', 'accuracy', 'provider', 'chunkId', 'chunkIndex', 'pointIndex'];
  const points = collectTrackPoints(chunks);
  const rows = points.map((p, idx) => ({
    tripId: tripId(trip),
    routeName: tripName(trip),
    idx: idx + 1,
    time: fmtLocal(p.time),
    lat: p.lat,
    lon: p.lon,
    alt: p.alt,
    accuracy: p.accuracy,
    provider: p.provider,
    chunkId: p.chunkId,
    chunkIndex: p.chunkIndex,
    pointIndex: p.pointIndex,
  }));

  return [
    fields.join(','),
    ...rows.map((r) => fields.map((f) => csvEscape(r[f])).join(',')),
  ].join('\n');
}

export function downloadGpx(trip, events, chunks) {
  downloadText(`urban_trip_${tripId(trip)}.gpx`, buildGpx(trip, events, chunks), 'application/gpx+xml');
}

export function downloadKml(trip, events, chunks) {
  downloadText(`urban_trip_${tripId(trip)}.kml`, buildKml(trip, events, chunks), 'application/vnd.google-earth.kml+xml');
}

export function downloadGeoJson(trip, events, chunks) {
  downloadText(`urban_trip_${tripId(trip)}.geojson`, buildGeoJson(trip, events, chunks), 'application/geo+json');
}

export function downloadTrackCsv(trip, events, chunks) {
  downloadText(`urban_trip_${tripId(trip)}_track_points.csv`, buildTrackCsv(trip, events, chunks), 'text/csv');
}

export function buildExportStats(events = [], chunks = []) {
  const track = buildTrackMetrics(Array.isArray(chunks) ? chunks : []);
  const eventPoints = collectEventPoints(events);
  return {
    pointCount: track.totalPoints,
    eventPointCount: eventPoints.length,
    distanceKm: track.distance / 1000,
    durationMs: track.durationMs,
    avgAccuracy: track.avgAccuracy,
  };
}
