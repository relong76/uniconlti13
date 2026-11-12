// The tool's own server: the app is served at {server}/app/, so the API is one level up.
export function apiUrl(path, location = window.location) {
  const page = location.href.split("#")[0];
  return new URL(`../${path.replace(/^\//, "")}`, page).toString();
}

export class ApiError extends Error {
  constructor(status, message, body) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

function parse(text) {
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    // some endpoints answer with plain text, such as the token trade
    return text;
  }
}

function messageFor(response, body) {
  // {message} from the app's own error handlers, {detail} from Spring's ProblemDetail
  if (body && typeof body === "object" && (body.message || body.detail)) {
    return body.message || body.detail;
  }

  if (typeof body === "string" && body) {
    return body;
  }

  return response.statusText || `Request failed with status ${response.status}`;
}

/**
 * fetch with JSON in and out. `token` sends a Bearer API token; `basic` sends admin credentials.
 */
export async function request(path, { method = "GET", body, token, basic } = {}) {
  const headers = { Accept: "application/json" };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  } else if (basic) {
    headers.Authorization = `Basic ${basic}`;
    // without it the server adds "WWW-Authenticate: Basic" to a 401 and the browser shows its own login popup
    headers["X-Requested-With"] = "XMLHttpRequest";
  }

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(apiUrl(path), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const data = parse(await response.text());

  if (!response.ok) {
    throw new ApiError(response.status, messageFor(response, data), data);
  }

  return data;
}
