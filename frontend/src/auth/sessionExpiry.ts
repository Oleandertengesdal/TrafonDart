/**
 * Remembers, for this tab only, that the user was sent to the login page because their session ran out, so the login
 * page can say so instead of looking as if nothing happened. The login page reads it, then clears it once shown.
 */
const KEY = "trafondart.sessionExpired";

export function markSessionExpired() {
  try {
    sessionStorage.setItem(KEY, "1");
  } catch {
    // storage blocked: the login page just won't explain
  }
}

export function wasSessionExpired(): boolean {
  try {
    return sessionStorage.getItem(KEY) === "1";
  } catch {
    return false;
  }
}

export function clearSessionExpired() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // nothing to clear
  }
}
