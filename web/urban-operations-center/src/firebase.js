import { initializeApp } from "firebase/app";
import {
  getAuth,
  onAuthStateChanged,
  signInAnonymously,
} from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import { webIntegrity, webIntegrityError } from "./services/webIntegrity";

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

export const authReady = (async () => {
  if (auth.currentUser) {
    webIntegrity("WEB_AUTH_READY", {
      outcome: "EXISTING_SESSION",
      uid: auth.currentUser.uid,
      anonymous: auth.currentUser.isAnonymous,
    });
    return auth.currentUser;
  }

  webIntegrity("WEB_AUTH_SIGN_IN", {
    method: "ANONYMOUS",
    outcome: "ATTEMPT",
  });

  try {
    const credential = await signInAnonymously(auth);
    const user = credential.user;
    webIntegrity("WEB_AUTH_SIGN_IN", {
      method: "ANONYMOUS",
      outcome: "SUCCESS",
      uid: user.uid,
      anonymous: user.isAnonymous,
    });
    webIntegrity("WEB_AUTH_READY", {
      outcome: "AUTHENTICATED",
      uid: user.uid,
      anonymous: user.isAnonymous,
    });
    return user;
  } catch (error) {
    webIntegrityError("WEB_AUTH_SIGN_IN_FAILED", error, {
      method: "ANONYMOUS",
    });
    throw error;
  }
})();
