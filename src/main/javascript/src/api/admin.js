import { request } from "./http";

// the fields an admin can set; the server fills in keyId, createdAt, updatedAt and version
export const PLATFORM_FIELDS = ["iss", "clientId", "deploymentId", "oidcEndpoint", "jwksEndpoint", "oAuth2TokenUrl", "oAuth2TokenAud"];

// "user:password" as base64 of its UTF-8 bytes, for HTTP Basic auth
export function encodeCredentials(username, password) {
  const bytes = new TextEncoder().encode(`${username}:${password}`);

  return btoa(String.fromCharCode(...bytes));
}

// a Spring Data page, serialized directly or as a PagedModel; GET /config/ answers 204 with no body when there's nothing
export function toPage(data) {
  const items = data?.content || [];
  const total = data?.page?.totalElements ?? data?.totalElements ?? items.length;

  return { items, total };
}

function query(page, size) {
  return new URLSearchParams({ page: String(page), size: String(size) }).toString();
}

function platformBody(platform) {
  return Object.fromEntries(PLATFORM_FIELDS.map((field) => {
    const value = typeof platform[field] === "string" ? platform[field].trim() : platform[field];

    return [field, value || null];
  }));
}

export async function listPlatforms(basic, { page = 0, size = 10 } = {}) {
  return toPage(await request(`config/?${query(page, size)}`, { basic }));
}

export function createPlatform(basic, platform) {
  return request("config/", { method: "POST", basic, body: platformBody(platform) });
}

export function updatePlatform(basic, id, platform) {
  return request(`config/${encodeURIComponent(id)}`, { method: "PUT", basic, body: platformBody(platform) });
}

export function deletePlatform(basic, id) {
  return request(`config/${encodeURIComponent(id)}`, { method: "DELETE", basic });
}

export async function listCourseCopies(basic, { page = 0, size = 20 } = {}) {
  return toPage(await request(`config/notices/context-copy?${query(page, size)}`, { basic }));
}
