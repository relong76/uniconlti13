function parse(value) {
  if (!value) {
    return null;
  }

  const date = new Date(value);

  return Number.isNaN(date.getTime()) ? null : date;
}

// an ISO date from the platform, for display
export function formatDateTime(value) {
  const date = parse(value);

  if (!date) {
    return value || "";
  }

  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

// an ISO date as the value of a datetime-local input
export function toLocalInput(value) {
  const date = parse(value);

  if (!date) {
    return "";
  }

  const pad = (number) => String(number).padStart(2, "0");

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// a datetime-local input value as an ISO date, or null when empty
export function fromLocalInput(value) {
  const date = parse(value);

  return date ? date.toISOString() : null;
}
