import { collection, onSnapshot } from "firebase/firestore";
import { authReady, db } from "../firebase";
import { webIntegrity, webIntegrityError } from "./webIntegrity";

const ORG_ID = import.meta.env.VITE_URBAN_ORG_ID || "afora";
const PROJECT_ID = import.meta.env.VITE_URBAN_PROJECT_ID || "urban_operations";

function toMillis(value) {
  if (!value) return 0;
  if (typeof value === "number") return value;
  if (value.toMillis) return value.toMillis();
  if (value.seconds) return value.seconds * 1000;
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function normalizeLiveDoc(doc) {
  const raw = { id: doc.id, ...doc.data() };
  const lat = raw.position?.lat ?? raw.lat ?? raw.lastLat;
  const lon = raw.position?.lon ?? raw.lon ?? raw.lastLon;
  const activeTripId = raw.tripId ?? raw.activeTripId ?? null;
  const updatedAt = raw.lastUpdateClient ?? raw.lastUpdateServer ?? raw.updatedAt ?? raw.createdAt ?? raw.lastFixTime;

  return {
    ...raw,
    installationId: raw.installationId || doc.id,
    tripId: activeTripId,
    activeTripId,
    tripStatus: raw.tripStatus || (activeTripId ? "ACTIVE" : "IDLE"),
    lastUpdateClient: updatedAt,
    lastUpdateServer: raw.lastUpdateServer ?? updatedAt,
    position: raw.position || (lat != null && lon != null ? {
      lat,
      lon,
      accuracy: raw.accuracy ?? raw.lastAccuracy ?? null,
    } : undefined),
    lat,
    lon,
    battery: raw.battery ?? (raw.batteryLevel != null ? { level: raw.batteryLevel } : undefined),
    gpsStatus: raw.gpsStatus || raw.locationStatus || (lat != null && lon != null ? "OK" : "PENDING"),
    syncVersion: raw.syncVersion || raw.appVersionName,
  };
}

function mapDocs(snapshot) {
  return snapshot.docs.map(normalizeLiveDoc);
}

function sortByLastUpdateDesc(a, b) {
  const aMs = toMillis(a.lastUpdateClient || a.lastUpdateServer);
  const bMs = toMillis(b.lastUpdateClient || b.lastUpdateServer);
  return bMs - aMs;
}

export function subscribeLiveDevices(onDevices, onError) {
  const path = `asd_organizations/${ORG_ID}/projects/${PROJECT_ID}/live_status`;
  webIntegrity("WEB_COLLECTION_PATH", { operation: "SUBSCRIBE_LIVE_DEVICES", path });
  webIntegrity("WEB_FIRESTORE_READ", {
    operation: "SUBSCRIBE_LIVE_DEVICES",
    path,
    outcome: "WAITING_FOR_AUTH",
  });

  let unsubscribe = null;
  let cancelled = false;

  authReady
    .then((user) => {
      if (cancelled) return;

      webIntegrity("WEB_FIRESTORE_READ", {
        operation: "SUBSCRIBE_LIVE_DEVICES",
        path,
        outcome: "SUBSCRIBE",
        uid: user?.uid ?? null,
        anonymous: user?.isAnonymous ?? null,
      });

      const ref = collection(
        db,
        "asd_organizations",
        ORG_ID,
        "projects",
        PROJECT_ID,
        "live_status"
      );

      unsubscribe = onSnapshot(
        ref,
        (snapshot) => {
          const devices = mapDocs(snapshot).sort(sortByLastUpdateDesc);
          webIntegrity("WEB_QUERY_RESULT", {
            operation: "SUBSCRIBE_LIVE_DEVICES",
            path,
            outcome: "SUCCESS",
            count: devices.length,
          });
          onDevices(devices);
        },
        (error) => {
          if (error?.code === "permission-denied") {
            webIntegrityError("WEB_FIRESTORE_PERMISSION_DENIED", error, {
              operation: "SUBSCRIBE_LIVE_DEVICES",
              path,
            });
          } else {
            webIntegrityError("WEB_FIRESTORE_READ_FAILED", error, {
              operation: "SUBSCRIBE_LIVE_DEVICES",
              path,
            });
          }
          if (onError) onError(error);
        }
      );
    })
    .catch((error) => {
      webIntegrityError("WEB_FIRESTORE_READ_FAILED", error, {
        operation: "SUBSCRIBE_LIVE_DEVICES",
        path,
        phase: "AUTH",
      });
      if (onError) onError(error);
    });

  return () => {
    cancelled = true;
    if (unsubscribe) unsubscribe();
  };
}
