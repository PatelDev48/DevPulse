import { apiRequest } from "./api";

export async function login({ email, password }) {
  const session = await apiRequest("/auth/login", { method: "POST", body: { email, password } });
  const identity = await apiRequest("/auth/me", { token: session.token });
  if (identity.id !== session.user?.id) {
    throw new Error("Unable to verify your session.");
  }
  return session;
}

export async function signup({ name, email, password }) {
  return apiRequest("/auth/signup", { method: "POST", body: { name, email, password } });
}
