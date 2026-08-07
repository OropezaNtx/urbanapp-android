import { collection, onSnapshot, doc, writeBatch } from "firebase/firestore";
import { authReady, db } from "../firebase";
import { webIntegrity, webIntegrityError } from "./webIntegrity";

const ORG_ID = import.meta.env.VITE_URBAN_ORG_ID || "afora";
const PROJECT_ID = import.meta.env.VITE_URBAN_PROJECT_ID || "urban_operations";

function mapDocs(snapshot) {
  return snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
}

function toMillis(value) {
  if (!value) return 0;
  if (typeof value === "number") return value;
  if (value.toMillis) return value.toMillis();
  if (value.seconds) return value.seconds * 1000;
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function sortByLastSeenDesc(a, b) {
  const aMs = toMillis(a.lastSeenAt || a.lastSeenClient || a.lastSeen);
  const bMs = toMillis(b.lastSeenAt || b.lastSeenClient || b.lastSeen);
  return bMs - aMs;
}

export function subscribeInstallationsHealth(onInstallations, onError) {
  const path = `asd_organizations/${ORG_ID}/projects/${PROJECT_ID}/installations`;
  webIntegrity("WEB_COLLECTION_PATH", { operation: "SUBSCRIBE_INSTALLATIONS", path });
  webIntegrity("WEB_FIRESTORE_READ", {
    operation: "SUBSCRIBE_INSTALLATIONS",
    path,
    outcome: "WAITING_FOR_AUTH",
  });

  let unsubscribe = null;
  let cancelled = false;

  authReady
    .then((user) => {
      if (cancelled) return;

      webIntegrity("WEB_FIRESTORE_READ", {
        operation: "SUBSCRIBE_INSTALLATIONS",
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
        "installations"
      );

      unsubscribe = onSnapshot(
        ref,
        (snapshot) => {
          const installations = mapDocs(snapshot).sort(sortByLastSeenDesc);
          webIntegrity("WEB_QUERY_RESULT", {
            operation: "SUBSCRIBE_INSTALLATIONS",
            path,
            outcome: "SUCCESS",
            count: installations.length,
          });
          onInstallations(installations);
        },
        (error) => {
          if (error?.code === "permission-denied") {
            webIntegrityError("WEB_FIRESTORE_PERMISSION_DENIED", error, {
              operation: "SUBSCRIBE_INSTALLATIONS",
              path,
            });
          } else {
            webIntegrityError("WEB_FIRESTORE_READ_FAILED", error, {
              operation: "SUBSCRIBE_INSTALLATIONS",
              path,
            });
          }
          if (onError) onError(error);
        }
      );
    })
    .catch((error) => {
      webIntegrityError("WEB_FIRESTORE_READ_FAILED", error, {
        operation: "SUBSCRIBE_INSTALLATIONS",
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

export async function updateInstallationStatus(installationId, status, extraFields = {}, ownerUid = null) {
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
    updatedAt: Date.now()
  });

  const accessRef = doc(db, "installation_access", ownerUid);
  batch.set(accessRef, {
    installationId,
    ownerUid,
    status,
    workspaceId: extraFields.workspaceId || null,
    licenseId: extraFields.licenseId || null,
    updatedAt: Date.now()
  });

  await batch.commit();
}
