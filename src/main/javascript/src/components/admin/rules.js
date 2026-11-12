export function required(message) {
  return (value) => Boolean(value && String(value).trim()) || message;
}

// empty passes, so optional URL fields can use it on its own
export function httpUrl(value) {
  if (!value || !String(value).trim()) {
    return true;
  }

  try {
    const url = new URL(String(value).trim());

    if (url.protocol === "https:" || url.protocol === "http:") {
      return true;
    }
  } catch {
    // not a URL at all
  }

  return "Enter a full URL that starts with https://";
}
