import { apiRequest } from "./api";
import { supabase } from "../supabaseClient";

export async function login({ email, password }) {
  const { data, error } = await supabase.auth.signInWithPassword({ email, password });
  if (error) throw error;

  const { session, user } = data;
  if (!session?.access_token || !session.expires_at || !user?.email) {
    throw new Error("Supabase did not return a complete login session.");
  }

  try {
    const identity = await apiRequest("/auth/me", { token: session.access_token });
    if (!identity?.id) throw new Error("Unable to load your DevPulse profile.");

    return {
      user: {
        id: identity.id,
        name: user.user_metadata?.name || user.email,
        email: user.email,
      },
      token: session.access_token,
      expiresAt: new Date(session.expires_at * 1000).toISOString(),
    };
  } catch (failure) {
    if (failure.status === 401) {
      await supabase.auth.signOut({ scope: "local" });
    }
    throw failure;
  }
}
