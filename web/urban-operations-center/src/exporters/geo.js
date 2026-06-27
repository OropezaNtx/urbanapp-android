import { buildTrackMetrics } from '../components/TrackSummary';

const EPOCH_MIN_REASONABLE = 100_000_000_000;
const GPX_NS = 'http://www.topografix.com/GPX/1/1';
const GPXX_NS = 'http://www.garmin.com/xmlschemas/GpxExtensions/v3';
const WPTX_NS = 'http://www.garmin.com/xmlschemas/WaypointExtension/v1';
const GPXTPX_NS = 'http://www.garmin.com/xmlschemas/TrackPointExtension/v1';
const GPXTRKX_NS = 'http://www.garmin.com/xmlschemas/TrackStatsExtension/v1';

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

function formatNum(value, decimals = 12) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '';
  return Number(n.toFixed(decimals)).toString();
}

function eventLat(e) { return e?.stopLat ?? e?.lat ?? e?.startLat; }
function eventLon(e) { return e?.stopLon ?? e?.lon ?? e?.startLon; }
function eventAlt(e) { return e?.stopAltM ?? e?.alt ?? e?.startAltM ?? 0; }
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

function nowIso() {
  return new Date().toISOString();
}

function fmtLocal(ms) {
  const n = Number(ms);
  if (!Number.isFinite(n) || n < EPOCH_MIN_REASONABLE) return '';
  const d = new Date(n);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('es-MX');
}

function downloadText(filename, content, mime) {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` });
  downloadBlob(filename, blob);
}

function downloadBlob(filename, blob) {
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

function directionKey(trip) {
  const dir = String(trip?.direction || '').trim().toUpperCase();
  if (dir.includes('REGRESO')) return 'REGRESO';
  return 'IDA';
}

function directionTitle(trip) {
  return directionKey(trip) === 'REGRESO' ? 'Regreso' : 'Ida';
}

function trackName(trip) {
  return directionKey(trip);
}

function garminTrackColor(trip) {
  return directionKey(trip) === 'REGRESO' ? 'Cyan' : 'Red';
}

function kmlTrackColor(trip) {
  // KML usa AABBGGRR. IDA rojo: ff0000ff. REGRESO cyan: ffffff00.
  return directionKey(trip) === 'REGRESO' ? 'ffffff00' : 'ff0000ff';
}

function cleanFilePart(value, fallback = 'NA') {
  const s = safe(value || fallback)
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[\\/:*?"<>|]+/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  return s || fallback;
}

function routeNumberForName(trip) {
  const raw = safe(trip?.tripNumber ?? trip?.routeNumber ?? trip?.routeId ?? '1');
  const match = raw.match(/\d+/);
  return match ? match[0] : raw;
}

function idForName(trip) {
  const raw = safe(trip?.localTripId ?? trip?.tripId ?? trip?.id ?? tripId(trip));
  const cloudMatch = raw.match(/_(\d+)$/);
  if (cloudMatch) return cloudMatch[1];
  const match = raw.match(/\d+/);
  return match ? match[0] : raw;
}

function exportBaseName(trip) {
  const start = cleanFilePart(trip?.baseStart || trip?.startBase || trip?.base_inicio || 'Base inicio');
  const end = cleanFilePart(trip?.baseEnd || trip?.endBase || trip?.base_fin || 'Base final');
  return `ID_${idForName(trip)}_${start} - ${end}_R${routeNumberForName(trip)}_${directionTitle(trip)}`;
}

function fileBase(trip) {
  return exportBaseName(trip).replace(/[^a-zA-Z0-9 _.-]+/g, '').slice(0, 120);
}

function collectTrackPoints(chunks = []) {
  return buildTrackMetrics(Array.isArray(chunks) ? chunks : []).points
    .filter((p) => validCoord(p.lat, p.lon));
}

function collectEventPoints(events = []) {
  return (Array.isArray(events) ? events : [])
    .filter((e) => validCoord(eventLat(e), eventLon(e)))
    .sort((a, b) => num(eventTime(a)) - num(eventTime(b)));
}

function allCoords(events = [], points = []) {
  return [
    ...points.map((p) => ({ lat: Number(p.lat), lon: Number(p.lon) })),
    ...collectEventPoints(events).map((e) => ({ lat: Number(eventLat(e)), lon: Number(eventLon(e)) })),
  ];
}

function boundsXml(events = [], points = []) {
  const coords = allCoords(events, points);
  if (!coords.length) return '';
  const maxlat = Math.max(...coords.map((c) => c.lat));
  const minlat = Math.min(...coords.map((c) => c.lat));
  const maxlon = Math.max(...coords.map((c) => c.lon));
  const minlon = Math.min(...coords.map((c) => c.lon));
  return `<bounds maxlat="${formatNum(maxlat, 15)}" maxlon="${formatNum(maxlon, 15)}" minlat="${formatNum(minlat, 15)}" minlon="${formatNum(minlon, 15)}"/>`;
}

function garminHeader({ creator = 'MapSource 6.16.3', fullGarminNamespaces = false } = {}) {
  if (fullGarminNamespaces) {
    return `<?xml version="1.0" encoding="UTF-8" standalone="no" ?>\n<gpx xmlns="${GPX_NS}" xmlns:gpxx="${GPXX_NS}" xmlns:gpxtrkx="${GPXTRKX_NS}" xmlns:wptx1="${WPTX_NS}" xmlns:gpxtpx="${GPXTPX_NS}" creator="${creator}" version="1.1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="${GPX_NS} ${GPX_NS}/gpx.xsd ${GPXX_NS} http://www8.garmin.com/xmlschemas/GpxExtensionsv3.xsd ${GPXTRKX_NS} http://www8.garmin.com/xmlschemas/TrackStatsExtension.xsd ${WPTX_NS} http://www8.garmin.com/xmlschemas/WaypointExtensionv1.xsd ${GPXTPX_NS} http://www8.garmin.com/xmlschemas/TrackPointExtensionv1.xsd">`;
  }
  return `<?xml version="1.0" encoding="UTF-8" standalone="no" ?>\n<gpx xmlns="${GPX_NS}" creator="${creator}" version="1.1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="${GPX_NS} ${GPX_NS}/gpx.xsd">`;
}

function garminMetadata(time, bounds = '') {
  return [
    '  <metadata>',
    '    <link href="http://www.garmin.com">',
    '      <text>Garmin International</text>',
    '    </link>',
    `    <time>${xmlEscape(time || nowIso())}</time>`,
    bounds ? `    ${bounds}` : '',
    '  </metadata>',
  ].filter(Boolean).join('\n');
}

function waypointName(idx) {
  return String(idx + 1).padStart(3, '0');
}

function garminWaypoint(e, idx, withDisplayMode = false) {
  const time = iso(eventTime(e)) || nowIso();
  const ele = formatNum(eventAlt(e), 6) || '0';
  return [
    `  <wpt lat="${formatNum(eventLat(e), 15)}" lon="${formatNum(eventLon(e), 15)}">`,
    `    <ele>${ele}</ele>`,
    `    <time>${xmlEscape(time)}</time>`,
    `    <name>${waypointName(idx)}</name>`,
    '    <sym>Flag, Blue</sym>',
    withDisplayMode ? '    <extensions>' : '',
    withDisplayMode ? `      <gpxx:WaypointExtension xmlns:gpxx="${GPXX_NS}">` : '',
    withDisplayMode ? '        <gpxx:DisplayMode>SymbolAndName</gpxx:DisplayMode>' : '',
    withDisplayMode ? '      </gpxx:WaypointExtension>' : '',
    withDisplayMode ? '    </extensions>' : '',
    '  </wpt>',
  ].filter(Boolean).join('\n');
}

function garminTrackPoint(p) {
  const time = iso(p.time);
  const ele = formatNum(p.alt ?? 0, 6) || '0';
  return [
    `      <trkpt lat="${formatNum(p.lat, 15)}" lon="${formatNum(p.lon, 15)}">`,
    `        <ele>${ele}</ele>`,
    time ? `        <time>${xmlEscape(time)}</time>` : '',
    '      </trkpt>',
  ].filter(Boolean).join('\n');
}

function trackDistanceMeters(points) {
  return Math.round(buildTrackMetrics([{ points }]).distance || 0);
}

function garminTrack(trip, points, { includeStats = false, color = garminTrackColor(trip) } = {}) {
  const stats = includeStats ? [
    '    <extensions>',
    `      <gpxx:TrackExtension xmlns:gpxx="${GPXX_NS}">`,
    `        <gpxx:DisplayColor>${xmlEscape(color)}</gpxx:DisplayColor>`,
    '      </gpxx:TrackExtension>',
    `      <gpxtrkx:TrackStatsExtension xmlns:gpxtrkx="${GPXTRKX_NS}">`,
    `        <gpxtrkx:Distance>${trackDistanceMeters(points)}</gpxtrkx:Distance>`,
    '      </gpxtrkx:TrackStatsExtension>',
    '    </extensions>',
  ] : [
    '    <extensions>',
    `      <gpxx:TrackExtension xmlns:gpxx="${GPXX_NS}">`,
    `        <gpxx:DisplayColor>${xmlEscape(color)}</gpxx:DisplayColor>`,
    '      </gpxx:TrackExtension>',
    '    </extensions>',
  ];

  return [
    '  <trk>',
    `    <name>${xmlEscape(trackName(trip))}</name>`,
    ...stats,
    '    <trkseg>',
    points.map(garminTrackPoint).join('\n'),
    '    </trkseg>',
    '  </trk>',
  ].join('\n');
}

export function buildGarminWaypointsGpx(trip, events = []) {
  const eventPoints = collectEventPoints(events);
  const metadataTime = iso(eventTime(eventPoints[0])) || nowIso();
  return [
    garminHeader({ creator: 'eTrex 20', fullGarminNamespaces: true }),
    garminMetadata(metadataTime),
    ...eventPoints.map((e, idx) => garminWaypoint(e, idx, false)),
    '</gpx>',
  ].join('\n');
}

export function buildGarminTrackGpx(trip, events = [], chunks = []) {
  const points = collectTrackPoints(chunks);
  const metadataTime = iso(points[0]?.time) || nowIso();
  return [
    garminHeader({ creator: 'eTrex 20', fullGarminNamespaces: true }),
    garminMetadata(metadataTime),
    garminTrack(trip, points, { includeStats: true, color: garminTrackColor(trip) }),
    '</gpx>',
  ].join('\n');
}

export function buildMapSourceCombinedGpx(trip, events = [], chunks = []) {
  const points = collectTrackPoints(chunks);
  const eventPoints = collectEventPoints(events);
  return [
    garminHeader({ creator: 'MapSource 6.16.3', fullGarminNamespaces: false }),
    garminMetadata(nowIso(), boundsXml(events, points)),
    ...eventPoints.map((e, idx) => garminWaypoint(e, idx, true)),
    garminTrack(trip, points, { includeStats: false, color: garminTrackColor(trip) }),
    '</gpx>',
  ].join('\n');
}

export function buildGpx(trip, events = [], chunks = []) {
  return buildMapSourceCombinedGpx(trip, events, chunks);
}

function kmlWaypointPlacemark(e, idx) {
  return [
    '      <Placemark>',
    `        <name>${waypointName(idx)}</name>`,
    '        <styleUrl>#blueFlag</styleUrl>',
    `        <Point><coordinates>${formatNum(eventLon(e), 15)},${formatNum(eventLat(e), 15)},${formatNum(eventAlt(e), 6) || '0'}</coordinates></Point>`,
    '      </Placemark>',
  ].join('\n');
}

export function buildKml(trip, events = [], chunks = []) {
  const points = collectTrackPoints(chunks);
  const eventPoints = collectEventPoints(events);
  const coordinates = points.map((p) => `${formatNum(p.lon, 15)},${formatNum(p.lat, 15)},${formatNum(p.alt ?? 0, 6) || '0'}`).join(' ');

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<kml xmlns="http://www.opengis.net/kml/2.2">',
    '  <Document>',
    `    <name>${xmlEscape(exportBaseName(trip))}</name>`,
    '    <Style id="blueFlag">',
    '      <IconStyle>',
    '        <scale>1.0</scale>',
    '        <Icon><href>http://maps.google.com/mapfiles/kml/paddle/blu-blank.png</href></Icon>',
    '      </IconStyle>',
    '    </Style>',
    `    <Style id="trackStyle"><LineStyle><color>${kmlTrackColor(trip)}</color><width>4</width></LineStyle></Style>`,
    '    <Folder>',
    '      <name>Waypoints</name>',
    ...eventPoints.map(kmlWaypointPlacemark),
    '    </Folder>',
    '    <Folder>',
    '      <name>Tracks</name>',
    '      <Placemark>',
    `        <name>${xmlEscape(trackName(trip))}</name>`,
    '        <styleUrl>#trackStyle</styleUrl>',
    '        <LineString>',
    '          <tessellate>1</tessellate>',
    `          <coordinates>${coordinates}</coordinates>`,
    '        </LineString>',
    '      </Placemark>',
    '    </Folder>',
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
      properties: { kind: 'track', name: trackName(trip), tripId: tripId(trip), pointCount: points.length },
      geometry: { type: 'LineString', coordinates: points.map((p) => [Number(p.lon), Number(p.lat), Number(p.alt ?? 0)]) },
    });
  }

  eventPoints.forEach((e, idx) => {
    features.push({
      type: 'Feature',
      properties: {
        kind: 'event',
        index: idx + 1,
        name: waypointName(idx),
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
      geometry: { type: 'Point', coordinates: [Number(eventLon(e)), Number(eventLat(e)), Number(eventAlt(e) || 0)] },
    });
  });

  return JSON.stringify({ type: 'FeatureCollection', name: exportBaseName(trip), features }, null, 2);
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

  return [fields.join(','), ...rows.map((r) => fields.map((f) => csvEscape(r[f])).join(','))].join('\n');
}

const crcTable = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i += 1) {
    let c = i;
    for (let k = 0; k < 8; k += 1) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32(bytes) {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i += 1) c = crcTable[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function dosDateTime(date = new Date()) {
  const year = Math.max(1980, date.getFullYear());
  const dosTime = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2);
  const dosDate = ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
  return { dosTime, dosDate };
}

function writeU16(out, value) { out.push(value & 0xff, (value >>> 8) & 0xff); }
function writeU32(out, value) { out.push(value & 0xff, (value >>> 8) & 0xff, (value >>> 16) & 0xff, (value >>> 24) & 0xff); }
function writeBytes(out, bytes) { bytes.forEach((b) => out.push(b)); }

function buildZipSingleFile(filename, content) {
  const encoder = new TextEncoder();
  const nameBytes = encoder.encode(filename);
  const data = encoder.encode(content);
  const crc = crc32(data);
  const { dosTime, dosDate } = dosDateTime();
  const local = [];
  const central = [];

  writeU32(local, 0x04034b50); writeU16(local, 20); writeU16(local, 0); writeU16(local, 0); writeU16(local, dosTime); writeU16(local, dosDate);
  writeU32(local, crc); writeU32(local, data.length); writeU32(local, data.length); writeU16(local, nameBytes.length); writeU16(local, 0);
  writeBytes(local, nameBytes); writeBytes(local, data);

  writeU32(central, 0x02014b50); writeU16(central, 20); writeU16(central, 20); writeU16(central, 0); writeU16(central, 0); writeU16(central, dosTime); writeU16(central, dosDate);
  writeU32(central, crc); writeU32(central, data.length); writeU32(central, data.length); writeU16(central, nameBytes.length); writeU16(central, 0); writeU16(central, 0); writeU16(central, 0); writeU16(central, 0);
  writeU32(central, 0); writeU32(central, 0); writeBytes(central, nameBytes);

  const end = [];
  writeU32(end, 0x06054b50); writeU16(end, 0); writeU16(end, 0); writeU16(end, 1); writeU16(end, 1); writeU32(end, central.length); writeU32(end, local.length); writeU16(end, 0);

  return new Blob([new Uint8Array(local), new Uint8Array(central), new Uint8Array(end)], { type: 'application/vnd.google-earth.kmz' });
}

export function downloadGarminWaypointsGpx(trip, events) {
  downloadText(`Waypoints_${fileBase(trip)}.gpx`, buildGarminWaypointsGpx(trip, events), 'application/gpx+xml');
}

export function downloadGarminTrackGpx(trip, events, chunks) {
  downloadText(`Track_${fileBase(trip)}.gpx`, buildGarminTrackGpx(trip, events, chunks), 'application/gpx+xml');
}

export function downloadMapSourceCombinedGpx(trip, events, chunks) {
  downloadText(`${fileBase(trip)}_Juntos.gpx`, buildMapSourceCombinedGpx(trip, events, chunks), 'application/gpx+xml');
}

export function downloadGpx(trip, events, chunks) {
  downloadMapSourceCombinedGpx(trip, events, chunks);
}

export function downloadKml(trip, events, chunks) {
  downloadText(`${fileBase(trip)}.kml`, buildKml(trip, events, chunks), 'application/vnd.google-earth.kml+xml');
}

export function downloadKmz(trip, events, chunks) {
  const blob = buildZipSingleFile('doc.kml', buildKml(trip, events, chunks));
  downloadBlob(`${fileBase(trip)}.kmz`, blob);
}

export function downloadGeoJson(trip, events, chunks) {
  downloadText(`${fileBase(trip)}.geojson`, buildGeoJson(trip, events, chunks), 'application/geo+json');
}

export function downloadTrackCsv(trip, events, chunks) {
  downloadText(`${fileBase(trip)}_track_points.csv`, buildTrackCsv(trip, events, chunks), 'text/csv');
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
