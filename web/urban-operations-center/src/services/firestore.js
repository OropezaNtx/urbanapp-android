import {
  collection,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
} from "firebase/firestore";
import { authReady, db } from "../firebase";
import { webIntegrity, webIntegrityError } from "./webIntegrity";
import { logCloudCompleteness } from "./cloudCompleteness";
import { logWebCompleteness } from "./webCompleteness";
import { logTripQuality } from "./qualityEngine";

const ORG_ID = import.meta.env.VITE_URBAN_ORG_ID || "afora";
const PROJECT_ID = import.meta.env.VITE_URBAN_PROJECT_ID || "urban_operations";
const USE_LEGACY = import.meta.env.VITE_URBAN_USE_LEGACY === "true";

function tripsPath() {
  return USE_LEGACY
    ? "asd_trips"
    : `asd_organizations/${ORG_ID}/projects/${PROJECT_ID}/trips`;
}

function tripPath(tripDocId) {
  return `${tripsPath()}/${String(tripDocId)}`;
}

function subcollectionPath(tripDocId, subcollectionName) {
  return `${tripPath(tripDocId)}/${subcollectionName}`;
}

function installationsPath() {
  return USE_LEGACY
    ? "asd_devices"
    : `asd_organizations/${ORG_ID}/projects/${PROJECT_ID}/installations`;
}

function logPath(operation, path) {
  webIntegrity("WEB_COLLECTION_PATH", {
    operation,
    path,
    orgId: ORG_ID,
    projectId: PROJECT_ID,
    useLegacy: USE_LEGACY,
  });
}

async function tracedRead(operation, path, readFn) {
  logPath(operation, path);
  webIntegrity("WEB_FIRESTORE_READ", { operation, path, outcome: "WAITING_FOR_AUTH" });

  try {
    const user = await authReady;
    webIntegrity("WEB_FIRESTORE_READ", {
      operation,
      path,
      outcome: "ATTEMPT",
      uid: user?.uid ?? null,
      anonymous: user?.isAnonymous ?? null,
    });

    const result = await readFn();
    const count = result?.docs?.length ?? (result?.exists?.() ? 1 : 0);
    webIntegrity("WEB_QUERY_RESULT", { operation, path, outcome: "SUCCESS", count });
    return result;
  } catch (error) {
    if (error?.code === "permission-denied") {
      webIntegrityError("WEB_FIRESTORE_PERMISSION_DENIED", error, { operation, path });
    } else {
      webIntegrityError("WEB_FIRESTORE_READ_FAILED", error, { operation, path });
    }
    throw error;
  }
}

function tripsCollection() {
  if (USE_LEGACY) return collection(db, "asd_trips");
  return collection(db, "asd_organizations", ORG_ID, "projects", PROJECT_ID, "trips");
}

function tripDoc(tripDocId) {
  if (USE_LEGACY) return doc(db, "asd_trips", String(tripDocId));
  return doc(db, "asd_organizations", ORG_ID, "projects", PROJECT_ID, "trips", String(tripDocId));
}

function tripSubcollection(tripDocId, subcollectionName) {
  if (USE_LEGACY) return collection(db, "asd_trips", String(tripDocId), subcollectionName);
  return collection(db, "asd_organizations", ORG_ID, "projects", PROJECT_ID, "trips", String(tripDocId), subcollectionName);
}

function mapDocs(snapshot) {
  return snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
}

export async function fetchTrips(maxRows = 200) {
  const path = tripsPath();
  const snap = await tracedRead(
    "FETCH_TRIPS",
    path,
    () => getDocs(query(tripsCollection(), orderBy("startTime", "desc"), limit(maxRows)))
  );
  return mapDocs(snap);
}

export async function fetchDevices() {
  const path = installationsPath();
  const devicesRef = USE_LEGACY
    ? collection(db, "asd_devices")
    : collection(db, "asd_organizations", ORG_ID, "projects", PROJECT_ID, "installations");

  const snap = await tracedRead("FETCH_DEVICES", path, () => getDocs(devicesRef));
  return mapDocs(snap);
}

export async function fetchTripEvents(tripDocId) {
  const path = subcollectionPath(tripDocId, "events");
  const snap = await tracedRead(
    "FETCH_TRIP_EVENTS",
    path,
    () => getDocs(query(tripSubcollection(tripDocId, "events"), orderBy("timestamp", "asc")))
  );
  return mapDocs(snap).map((d) => ({ source: "cloud/events", ...d }));
}

export async function fetchTripTrackChunks(tripDocId) {
  const path = subcollectionPath(tripDocId, "track_chunks");
  const snap = await tracedRead(
    "FETCH_TRACK_CHUNKS",
    path,
    () => getDocs(tripSubcollection(tripDocId, "track_chunks"))
  );
  return mapDocs(snap).map((d) => ({ source: "cloud/track_chunks", ...d }));
}

export async function fetchTripTrackSummary(tripDocId) {
  if (!USE_LEGACY) return [];
  const path = subcollectionPath(tripDocId, "track_summary");
  try {
    const snap = await tracedRead(
      "FETCH_TRACK_SUMMARY",
      path,
      () => getDocs(tripSubcollection(tripDocId, "track_summary"))
    );
    return mapDocs(snap);
  } catch {
    return [];
  }
}

export async function fetchBackupEventsByTripId(tripId) {
  if (!USE_LEGACY) return [];
  const path = "urbanapp_asd_backups";
  const snap = await tracedRead(
    "FETCH_BACKUP_EVENTS",
    path,
    () => getDocs(collection(db, "urbanapp_asd_backups"))
  );
  const prefix = `trip_${tripId}_event_`;
  return snap.docs
    .filter((d) => d.id.startsWith(prefix))
    .map((d) => ({ id: d.id, source: "urbanapp_asd_backups", ...d.data() }))
    .sort((a, b) => (a.timestamp ?? a.startTime ?? 0) - (b.timestamp ?? b.startTime ?? 0));
}

export async function fetchTripDetail(tripDocId, tripId) {
  const path = tripPath(tripDocId);
  const tripSnap = await tracedRead("FETCH_TRIP_DETAIL", path, () => getDoc(tripDoc(tripDocId)));
  const trip = tripSnap.exists() ? { id: tripSnap.id, ...tripSnap.data() } : null;
  const effectiveTripId = tripId ?? trip?.tripId ?? tripDocId;

  const primaryReads = [
    fetchTripEvents(tripDocId).catch(() => []),
    fetchTripTrackChunks(tripDocId).catch(() => []),
  ];

  const legacyReads = USE_LEGACY
    ? [
        fetchBackupEventsByTripId(effectiveTripId).catch(() => []),
        fetchTripTrackSummary(tripDocId).catch(() => []),
      ]
    : [Promise.resolve([]), Promise.resolve([])];

  const [events, trackChunks, backupEvents, trackSummary] = await Promise.all([
    ...primaryReads,
    ...legacyReads,
  ]);

  const resolvedEvents = events.length ? events : backupEvents;
  const resolvedTrack = trackChunks.length ? trackChunks : trackSummary;
  const completeness = logCloudCompleteness({
    tripDocId,
    trip,
    events: resolvedEvents,
    trackChunks: resolvedTrack,
  });
  const webCompleteness = logWebCompleteness({
    tripDocId,
    trip,
    events: resolvedEvents,
    trackChunks: resolvedTrack,
  });
  const quality = logTripQuality({
    tripDocId,
    trip,
    events: resolvedEvents,
    trackChunks: resolvedTrack,
  });

  return {
    trip,
    events: resolvedEvents,
    backupEvents,
    trackSummary: resolvedTrack,
    trackChunks,
    completeness,
    webCompleteness,
    quality,
  };
}
