import { request } from "./http";

// trades the one-time token a launch hands the app for an API token
export function tradeToken(oneTimeToken) {
  return request("api/oauth/trade", { method: "POST", token: oneTimeToken });
}

export function getLaunch(token) {
  return request("api/launch", { token });
}
