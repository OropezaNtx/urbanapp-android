import { collection, onSnapshot } from "firebase/firestore";
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

function sortByLastUpdateDesc(a, b) {
  const aMs = toMillis(a.lastUpdateClient || a.lastUpdateServer);
  const bMs = toMillis(b.lastUpdateClient || b.lastUpdateServer);
  return bMs - aMs;
}

export function subscribeLiveDevices(onDevices, onError) {
  const ref = collection(db, "live_devices");

  return onSnapshot(
    ref,
    (snapshot) => {
      const devices = mapDocs(snapshot).sort(sortByLastUpdateDesc);
      onDevices(devices);
    },
    (error) => {
      console.error("Error escuchando live_devices", error);
      if (onError) onError(error);
    }
  );
}
