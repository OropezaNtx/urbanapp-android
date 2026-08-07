export function webIntegrity(event, details = {}) {
  const payload = { event, ...details };
  console.log(`[WebFirestoreIntegrity] ${event}`, payload);
}

export function webIntegrityError(event, error, details = {}) {
  const payload = {
    event,
    code: error?.code || null,
    message: error?.message || String(error),
    ...details,
  };
  console.error(`[WebFirestoreIntegrity] ${event}`, payload);
}
