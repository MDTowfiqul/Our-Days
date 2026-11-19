// Shared helpers for the Our Days live games (GitHub Pages, Firebase RTDB).
// Every game page imports this module; it signs the player in anonymously
// and gives small helpers for the room the two phones share.
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import { getAuth, signInAnonymously, onAuthStateChanged } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { getDatabase, ref, set, get, onValue, serverTimestamp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-database.js";
import { FIREBASE_CONFIG } from "./firebase-config.js";

const app = initializeApp(FIREBASE_CONFIG);
const auth = getAuth(app);
const db = getDatabase(app);

/** Room code from the URL (?room=abc123). Empty when missing or malformed. */
export function roomCodeFromUrl() {
  const room = (new URLSearchParams(window.location.search).get("room") || "").trim();
  return /^[0-9a-f]{6}$/.test(room) ? room : "";
}

/** Promise that resolves with the anonymous UID once signed in. */
export function waitForAuth() {
  return new Promise((resolve, reject) => {
    const off = onAuthStateChanged(auth, (user) => {
      if (user) { off(); resolve(user.uid); }
    }, (err) => reject(err));
    signInAnonymously(auth).catch((err) => reject(err));
  });
}

export function roomRef(roomCode, path) {
  return ref(db, "rooms/" + roomCode + (path ? "/" + path : ""));
}

/** Listen to a value under the room; returns the unsubscribe function. */
export function onRoomValue(roomCode, path, cb) {
  return onValue(roomRef(roomCode, path), (snap) => cb(snap.val()));
}

/** Listen to the RTDB server-time offset (server ms minus local ms). */
export function onServerOffset(cb) {
  return onValue(ref(db, ".info/serverTimeOffset"), (snap) => cb(snap.val() || 0));
}

/**
 * Join a room as a player. The RTDB rules cannot count children, so the
 * 2-player cap is enforced here: a third UID is refused with "full".
 * (Two phones joining at the same instant can both see one player; both
 * are the intended couple, so that race is harmless.)
 */
export async function joinRoom(roomCode, uid) {
  const snap = await get(roomRef(roomCode, "players"));
  const players = snap.val() || {};
  if (players[uid]) return "joined";
  if (Object.keys(players).length >= 2) return "full";
  await set(roomRef(roomCode, "players/" + uid), { joinedAt: serverTimestamp() });
  return "joined";
}
