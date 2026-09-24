const BASE_URL = import.meta.env.VITE_API_URL ?? "/api";

export async function apiRequest(path, { method = "GET", body, token } = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  if (!response.ok) {
    let message = "Request failed";
    try {
      const data = await response.json();
      message = data.detail ?? data.message ?? message;
    } catch {
      // Response had no JSON body; keep the default message.
    }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return response.status === 204 ? null : response.json();
}

export { BASE_URL };
