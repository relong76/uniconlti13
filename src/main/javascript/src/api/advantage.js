import { request } from "./http";

// line items are addressed by their "key" (see GradesApiController), encoded into the path
function lineItemPath(key) {
  return `api/grades/lineitems/${encodeURIComponent(key)}`;
}

export function listLineItems(token) {
  return request("api/grades/lineitems", { token });
}

export function createLineItem(token, lineItem) {
  return request("api/grades/lineitems", { method: "POST", token, body: lineItem });
}

export function getLineItem(token, key) {
  return request(lineItemPath(key), { token });
}

export function updateLineItem(token, key, lineItem) {
  return request(lineItemPath(key), { method: "PUT", token, body: lineItem });
}

export function deleteLineItem(token, key) {
  return request(lineItemPath(key), { method: "DELETE", token });
}

export function postScore(token, key, score) {
  return request(`${lineItemPath(key)}/scores`, { method: "POST", token, body: score });
}

export function getRoster(token) {
  return request("api/roster", { token });
}

export const ACTIVITY_PROGRESS = ["Initialized", "Started", "InProgress", "Submitted", "Completed"];
export const GRADING_PROGRESS = ["FullyGraded", "Pending", "PendingManual", "Failed", "NotReady"];
