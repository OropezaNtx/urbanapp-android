function serialize(details = {}) {
  return Object.entries(details)
    .map(([key, value]) => `${key}=${formatValue(value)}`)
    .join(" ");
}

function formatValue(value) {
  if (value === null || value === undefined) return "null";
  if (typeof value === "string") return value.replace(/\s+/g, "_");
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

export function webIntegrity(event, details = {}) {
  const suffix = serialize(details);
  console.log(`[WebFirestoreIntegrity] ${event}${suffix ? ` ${suffix}` : ""}`);
}

export function webIntegrityError(event, error, details = {}) {
  const payload = {
    code: error?.code || null,
    message: error?.message || String(error),
    ...details,
  };
  const suffix = serialize(payload);
  console.error(`[WebFirestoreIntegrity] ${event}${suffix ? ` ${suffix}` : ""}`);
}
