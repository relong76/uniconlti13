import { defineStore } from "pinia";
import { encodeCredentials, listPlatforms } from "../api/admin";

const WRONG_LOGIN = "That username and password didn't work. Check them and try again.";

// the admin login lives in memory only: reloading the page signs out
export const useAdminStore = defineStore("admin", {
  state: () => ({
    credentials: null,
    username: null,
    signingIn: false,
    error: null
  }),
  getters: {
    signedIn: (state) => Boolean(state.credentials)
  },
  actions: {
    async signIn(username, password) {
      this.signingIn = true;
      this.error = null;
      const credentials = encodeCredentials(username, password);

      try {
        await listPlatforms(credentials, { size: 1 });
        this.credentials = credentials;
        this.username = username;
        return true;
      } catch (e) {
        this.error = e.status === 401 ? WRONG_LOGIN : `Couldn't sign in: ${e.message}`;
        return false;
      } finally {
        this.signingIn = false;
      }
    },
    signOut(reason = null) {
      this.credentials = null;
      this.username = null;
      this.error = reason;
    },
    // a 401 after signing in means the password changed or the server restarted with a new one
    failed(e) {
      if (e?.status === 401) {
        this.signOut("Your admin login stopped working. Sign in again.");
        return true;
      }

      return false;
    }
  }
});
