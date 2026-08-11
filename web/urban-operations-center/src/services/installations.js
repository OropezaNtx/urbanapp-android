import { collection, onSnapshot, doc, writeBatch } from "firebase/firestore";
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

function sortByLastSeenDesc(a, b) {
  const aMs = toMillis(a.lastHeartbeatAt || a.lastSeenAt || a.lastSeenClient || a.lastSeen || a.updatedAt);
  const bMs = toMillis(b.lastHeartbeatAt || b.lastSeenAt || b.lastSeenClient || b.lastSeen || b.updatedAt);
  return bMs - aMs;
}

function normalizeLive(doc) {
  const raw = { id: doc.id, ...doc.data() };
  return {
    ...raw,
    installationId: raw.installationId || doc.id,
    lastSeenClient: raw.lastSeenClient ?? raw.updatedAt ?? raw.lastFixTime,
    lastHeartbeatAt: raw.lastHeartbeatAt ?? raw.updatedAt,
    activeTripId: raw.activeTripId ?? raw.tripId ?? null,
    battery: raw.battery ?? (raw.batteryLevel != null ? { level: raw.batteryLevel } : undefined),
    device: raw.device,
  };
}

function mergeFleet(installations, liveDevices) {
  const installationsById = new Map(installations.map((item) => [item.id, item]));
  const liveById = new Map(liveDevices.map((item) => [item.installationId || item.id, item]));
  const ids = new Set([...installationsById.keys(), ...liveById.keys()]);

  return [...ids].map((id) => {
    const installation = installationsById.get(id) || { id, installationId: id, status: "PENDING" };
    const live = liveById.get(id);
    if (!live) return installation;

    const fallbackDevice = {
      manufacturer: installation.manufacturer,
      model: installation.model,
      androidVersion: installation.androidVersion,
      appVersionName: installation.appVersionName,
      appVersionCode: installation.appVersionCode,
    };

    return {
      ...installation,
      ...live,
      id,
      installationId: id,
      // Administrative fields always come from the durable installation record.
      status: installation.status || live.status || "PENDING",
      workspaceId: installation.workspaceId || live.workspaceId,
      projectId: installation.projectId || live.projectId,
      licenseId: installation.licenseId,
      ownerUid: installation.ownerUid,
      manufacturer: installation.manufacturer || live.device?.manufacturer,
      model: installation.model || live.device?.model,
      androidVersion: installation.androidVersion || live.device?.androidVersion,
      appVersionName: installation.appVersionName || live.appVersionName || live.device?.appVersionName,
      device: live.device || fallbackDevice,
      lastSeenAt: live.lastSeenClient || installation.lastSeenAt,
      lastHeartbeatAt: live.lastHeartbeatAt || live.lastSeenClient || installation.lastHeartbeatAt,
      lastSyncAt: installation.lastSyncAt,
    };
  }).sort(sortByLastSeenDesc);
}

export function subscribeInstallationsHealth(onInstallations, onError) {
  const installationsPath = `asd_organizations/${ORG_ID}/projects/${PROJECT_ID}/installations`;
  const livePath = `asd_organizations/${ORG_ID}/projects/${PROJECT_ID}/live_status`;
  webIntegrity("WEB_COLLECTION_PATH", { operation: "SUBSCRIBE_INSTALLATIONS", path: installationsPath });
  webIntegrity("WEB_COLLECTION_PATH", { operation: "SUBSCRIBE_FLEET_LIVE", path: livePath });

  let unsubscribeInstallations = null;
  let unsubscribeLive = null;
  let cancelled = false;
  let installationRows = [];
  let liveRows = [];

  const emit = () => onInstallations(mergeFleet(installationRows, liveRows));
  const reportError = (operation, path, error) => {
    if (error?.code === "permission-denied") {
      webIntegrityError("WEB_FIRESTORE_PERMISSION_DENIED", error, { operation, path });
    } else {
      webIntegrityError("WEB_FIRESTORE_READ_FAILED", error, { operation, path });
    }
    if (onError) onError(error);
  };

  authReady
    .then((user) => {
      if (cancelled) return;

      webIntegrity("WEB_FIRESTORE_READ", {
        operation: "SUBSCRIBE_INSTALLATIONS",
        path: installationsPath,
        outcome: "SUBSCRIBE",
        uid: user?.uid ?? null,
        anonymous: user?.isAnonymous ?? null,
      });

      const installationsRef = collection(db, "asd_organizations", ORG_ID, "projects", PROJECT_ID, "installations");
      const liveRef = collection(db, "asd_organizations", ORG_ID, "projects", PROJECT_ID, "live_status");

      unsubscribeInstallations = onSnapshot(
        installationsRef,
        (snapshot) => {
          installationRows = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
          webIntegrity("WEB_QUERY_RESULT", {
            operation: "SUBSCRIBE_INSTALLATIONS",
            path: installationsPath,
            outcome: "SUCCESS",
            count: installationRows.length,
          });
          emit();
        },
        (error) => reportError("SUBSCRIBE_INSTALLATIONS", installationsPath, error)
      );

      unsubscribeLive = onSnapshot(
        liveRef,
        (snapshot) => {
          liveRows = snapshot.docs.map(normalizeLive);
          webIntegrity("WEB_QUERY_RESULT", {
            operation: "SUBSCRIBE_FLEET_LIVE",
            path: livePath,
            outcome: "SUCCESS",
            count: liveRows.length,
          });
          emit();
        },
        (error) => reportError("SUBSCRIBE_FLEET_LIVE", livePath, error)
      );
    })
    .catch((error) => reportError("SUBSCRIBE_INSTALLATIONS", installationsPath, error));

  return () => {
    cancelled = true;
    if (unsubscribeInstallations) unsubscribeInstallations();
    if (unsubscribeLive) unsubscribeLive();
  };
}

export async function updateInstallationStatus(installationId, status, extraFields = {}) {
  await authReady;
  const batch = writeBatch(db);

  const instRef = doc(
    db,
    "asd_organizations",
    ORG_ID,
    "projects",
    PROJECT_ID,
    "installations",
    installationId
  );
  batch.update(instRef, {
    status,
    ...extraFields,
    projectId: extraFields.projectId || PROJECT_ID,
    updatedAt: Date.now()
  });

  await batch.commit();
}
