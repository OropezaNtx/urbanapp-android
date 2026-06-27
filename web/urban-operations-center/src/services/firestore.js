import {
  collection,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
} from "firebase/firestore";
import { db } from "../firebase";

const ORG_ID = import.meta.env.VITE_URBAN_ORG_ID || "demo_org";
const PROJECT_ID = import.meta.env.VITE_URBAN_PROJECT_ID || "demo_project";
const USE_LEGACY = import.meta.env.VITE_URBAN_USE_LEGACY === "true";

function tripsCollection() {
  if (USE_LEGACY) {
    return collection(db, "asd_trips");
  }

  return collection(
    db,
    "asd_organizations",
    ORG_ID,
    "projects",
    PROJECT_ID,
    "trips"
  );
}

function tripDoc(tripDocId) {
  if (USE_LEGACY) {
    return doc(db, "asd_trips", String(tripDocId));
  }

  return doc(
    db,
    "asd_organizations",
    ORG_ID,
    "projects",
    PROJECT_ID,
    "trips",
    String(tripDocId)
  );
}

function tripSubcollection(tripDocId, subcollectionName) {
  if (USE_LEGACY) {
    return collection(db, "asd_trips", String(tripDocId), subcollectionName);
  }

  return collection(
    db,
    "asd_organizations",
    ORG_ID,
    "projects",
    PROJECT_ID,
    "trips",
    String(tripDocId),
    subcollectionName
  );
}

function mapDocs(snapshot) {
  return snapshot.docs.map((d) => ({
    id: d.id,
    ...d.data(),
  }));
}

export async function fetchTrips(maxRows = 200) {
  console.log("Leyendo trips cloud:", {
    orgId: ORG_ID,
    projectId: PROJECT_ID,
    useLegacy: USE_LEGACY,
  });

  const snap = await getDocs(
    query(tripsCollection(), orderBy("startTime", "desc"), limit(maxRows))
  );

  const trips = mapDocs(snap);
  console.log("Trips encontrados:", trips.length, trips);

  return trips;
}

export async function fetchDevices() {
  console.log("Leyendo devices cloud...");

  const devicesRef = USE_LEGACY
    ? collection(db, "asd_devices")
    : collection(
        db,
        "asd_organizations",
        ORG_ID,
        "projects",
        PROJECT_ID,
        "installations"
      );

  const snap = await getDocs(devicesRef);
  const devices = mapDocs(snap);

  console.log("Devices encontrados:", devices.length, devices);

  return devices;
}

export async function fetchTripEvents(tripDocId) {
  const snap = await getDocs(
    query(tripSubcollection(tripDocId, "events"), orderBy("timestamp", "asc"))
  );

  return mapDocs(snap).map((d) => ({
    source: "cloud/events",
    ...d,
  }));
}

export async function fetchTripTrackChunks(tripDocId) {
  const snap = await getDocs(tripSubcollection(tripDocId, "track_chunks"));

  return mapDocs(snap).map((d) => ({
    source: "cloud/track_chunks",
    ...d,
  }));
}

export async function fetchTripTrackSummary(tripDocId) {
  try {
    const snap = await getDocs(tripSubcollection(tripDocId, "track_summary"));
    return mapDocs(snap);
  } catch {
    return [];
  }
}

export async function fetchBackupEventsByTripId(tripId) {
  const snap = await getDocs(collection(db, "urbanapp_asd_backups"));
  const prefix = `trip_${tripId}_event_`;

  return snap.docs
    .filter((d) => d.id.startsWith(prefix))
    .map((d) => ({
      id: d.id,
      source: "urbanapp_asd_backups",
      ...d.data(),
    }))
    .sort(
      (a, b) =>
        (a.timestamp ?? a.startTime ?? 0) - (b.timestamp ?? b.startTime ?? 0)
    );
}

export async function fetchTripDetail(tripDocId, tripId) {
  console.log("Leyendo detalle cloud:", tripDocId);

  const tripSnap = await getDoc(tripDoc(tripDocId));
  const trip = tripSnap.exists()
    ? {
        id: tripSnap.id,
        ...tripSnap.data(),
      }
    : null;

  const effectiveTripId = tripId ?? trip?.tripId ?? tripDocId;

  const [events, backupEvents, trackChunks, trackSummary] = await Promise.all([
    fetchTripEvents(tripDocId).catch(() => []),
    fetchBackupEventsByTripId(effectiveTripId).catch(() => []),
    fetchTripTrackChunks(tripDocId).catch(() => []),
    fetchTripTrackSummary(tripDocId).catch(() => []),
  ]);

  return {
    trip,
    events: events.length ? events : backupEvents,
    backupEvents,
    trackSummary: trackChunks.length ? trackChunks : trackSummary,
    trackChunks,
  };
}