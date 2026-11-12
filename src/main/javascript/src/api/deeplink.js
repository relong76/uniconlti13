import { request } from "./http";

// the content an instructor can add to the course
export function getItems(token) {
  return request("api/deeplink/items", { token });
}

// signs the chosen items; answers with { returnUrl, jwt }
export function createResponse(token, selectedIds) {
  return request("api/deeplink/response", { method: "POST", token, body: { selectedIds } });
}

// a deep linking response goes back to the platform as a form POST of the JWT to its return URL
// the platform's deep link return URL must be https (or http on this machine, for a local platform), so a
// bad return URL can't turn the form into a javascript: or data: navigation
export function isSafeReturnUrl(returnUrl) {
  let url;

  try {
    url = new URL(returnUrl);
  } catch {
    return false;
  }

  const local = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);

  return url.protocol === "https:" || (url.protocol === "http:" && local);
}

export function postToPlatform(returnUrl, jwt, doc = document) {
  if (!isSafeReturnUrl(returnUrl)) {
    throw new Error("The platform's deep link return URL isn't an https URL, so the content wasn't sent.");
  }

  const form = doc.createElement("form");
  form.method = "POST";
  form.action = returnUrl;
  form.hidden = true;

  const input = doc.createElement("input");
  input.type = "hidden";
  input.name = "JWT";
  input.value = jwt;

  form.appendChild(input);
  doc.body.appendChild(form);
  form.submit();

  return form;
}
