// "http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor" -> "Instructor"
export function roleLabel(role) {
  if (!role) {
    return "";
  }

  const name = role.split(/[#/]/).pop();

  return name || role;
}

// a role as it appears in a claim, with its scope: "membership", "institution", "system"
export function roleScope(role) {
  const match = /\/vocab\/lis\/v2\/([a-z]+)/.exec(role || "");

  return match ? match[1] : null;
}

export function prettyJson(value) {
  return JSON.stringify(value ?? null, null, 2);
}

// a claim's object as label/value rows, for simple tables
export function entries(object) {
  if (!object || typeof object !== "object") {
    return [];
  }

  return Object.entries(object).map(([key, value]) => ({
    key,
    value: value !== null && typeof value === "object" ? JSON.stringify(value) : String(value)
  }));
}
