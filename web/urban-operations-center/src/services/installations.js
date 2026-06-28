import { collection, onSnapshot, doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";

function mapDocs(snapshot) {
  return snapshot.docs.map((doc) => ({
    id: doc.id,
    ...doc.data(),
  }));
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
  const ref = collection(db, "installations");

  return onSnapshot(
    ref,
    (snapshot) => {
      const installations = mapDocs(snapshot).sort(sortByLastSeenDesc);
      onInstallations(installations);
    },
    (error) => {
      console.error("Error escuchando installations", error);
      if (onError) onError(error);
    }
  );
}

export async function updateInstallationStatus(installationId, status, workspaceId, licenseId) {
  const ref = doc(db, "installations", installationId);
  await updateDoc(ref, {
    status,
    workspaceId,
    licenseId,
    updatedAt: Date.now()
  });
}
