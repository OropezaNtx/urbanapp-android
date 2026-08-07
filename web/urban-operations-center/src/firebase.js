import { initializeApp } from "firebase/app";
import { getAuth, onAuthStateChanged } from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import { webIntegrity } from "./services/webIntegrity";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID,
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);

webIntegrity("WEB_FIREBASE_PROJECT", {
  projectId: firebaseConfig.projectId || null,
  authDomain: firebaseConfig.authDomain || null,
  appId: firebaseConfig.appId || null,
});

webIntegrity("WEB_AUTH_STATE", {
  state: auth.currentUser ? "AUTHENTICATED" : "NO_CURRENT_USER_AT_INIT",
});

onAuthStateChanged(auth, (user) => {
  webIntegrity("WEB_AUTH_STATE", {
    state: user ? "AUTHENTICATED" : "SIGNED_OUT",
    anonymous: user?.isAnonymous ?? null,
  });

  webIntegrity("WEB_AUTH_UID", {
    uid: user?.uid ?? null,
    anonymous: user?.isAnonymous ?? null,
  });
});
