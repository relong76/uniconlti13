import { defineStore } from "pinia";
import { getLaunch, tradeToken } from "../api/lti";

const STORAGE_KEY = "lti13demo.token";

// sessionStorage can be unavailable in an LMS iframe (third-party storage blocked)
function stored() {
  try {
    return window.sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function store(token) {
  try {
    if (token) {
      window.sessionStorage.setItem(STORAGE_KEY, token);
    } else {
      window.sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // keep it in memory only
  }
}

// the one-time token the launch put in the URL, as ?token= or inside the hash
export function tokenFromUrl(location = window.location) {
  const fromSearch = new URLSearchParams(location.search).get("token");

  if (fromSearch) {
    return fromSearch;
  }

  const hash = location.hash || "";
  const query = hash.includes("?") ? hash.slice(hash.indexOf("?") + 1) : "";

  return new URLSearchParams(query).get("token");
}

// the URL without the one-time token, so it isn't left in history or reused on reload
export function withoutToken(location = window.location) {
  const url = new URL(location.href);
  url.searchParams.delete("token");

  return url.toString();
}

export const useSessionStore = defineStore("session", {
  state: () => ({
    token: null,
    launch: null,
    loading: false,
    error: null
  }),
  getters: {
    isDeepLinking: (state) => state.launch?.messageType === "LtiDeepLinkingRequest",
    hasGrades: (state) => Boolean(state.launch?.services?.assignmentsAndGrades),
    hasRoster: (state) => Boolean(state.launch?.services?.namesAndRoles)
  },
  actions: {
    async start(location = window.location, history = window.history) {
      this.loading = true;
      this.error = null;

      try {
        const oneTimeToken = tokenFromUrl(location);

        if (oneTimeToken) {
          this.token = await tradeToken(oneTimeToken);
          store(this.token);
          history.replaceState(history.state, "", withoutToken(location));
        } else {
          this.token = stored();
        }

        if (!this.token) {
          this.error = "Launch the tool from your course to start.";
          return;
        }

        this.launch = await getLaunch(this.token);
      } catch (e) {
        this.error = e.status === 401 || e.status === 404
          ? "This launch has expired. Launch the tool again from your course."
          : e.message;
        this.launch = null;
      } finally {
        this.loading = false;
      }
    },
    end() {
      this.token = null;
      this.launch = null;
      store(null);
    }
  }
});
