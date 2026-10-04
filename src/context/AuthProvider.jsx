import { useEffect, useState } from "react";
import { AuthContext } from "./AuthContext";
import { apiRequest } from "../services/api";
import { supabase } from "../supabaseClient";

const EMPTY_AUTH = { user: null, token: null, expiresAt: null, isLoading: false };

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState({ ...EMPTY_AUTH, isLoading: true });

  useEffect(() => {
    let active = true;
    let requestId = 0;

    const applySession = async (session) => {
      const currentRequest = ++requestId;

      if (!session?.access_token || !session.user?.email) {
        if (active) setAuth(EMPTY_AUTH);
        return;
      }

      setAuth((current) => ({ ...current, isLoading: true }));

      try {
        const profile = await apiRequest("/auth/me", { token: session.access_token });
        if (!active || currentRequest !== requestId) return;

        const expiresAt = session.expires_at
          ?? Math.floor(Date.now() / 1000) + session.expires_in;

        setAuth({
          user: {
            id: profile.id,
            name: session.user.user_metadata?.name || session.user.email,
            email: session.user.email,
          },
          token: session.access_token,
          expiresAt: new Date(expiresAt * 1000).toISOString(),
          isLoading: false,
        });
      } catch (failure) {
        if (!active || currentRequest !== requestId) return;
        setAuth(EMPTY_AUTH);
        if (failure.status === 401) {
          await supabase.auth.signOut({ scope: "local" });
        }
      }
    };

    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      setTimeout(() => { void applySession(session); }, 0);
    });

    supabase.auth.getSession()
      .then(({ data, error }) => {
        if (error) throw error;
        return applySession(data.session);
      })
      .catch(() => {
        if (active) setAuth(EMPTY_AUTH);
      });

    return () => {
      active = false;
      subscription.unsubscribe();
    };
  }, []);

  const login = ({ user, token, expiresAt }) => {
    if (!user?.id || !token || !(Date.parse(expiresAt) > Date.now())) {
      throw new Error("Invalid or expired login session.");
    }
    setAuth({ user, token, expiresAt, isLoading: false });
  };

  const logout = () => {
    setAuth(EMPTY_AUTH);
    void supabase.auth.signOut();
  };

  return (
    <AuthContext.Provider value={{
      user: auth.user,
      token: auth.token,
      isAuthenticated: Boolean(auth.token),
      isLoading: auth.isLoading,
      login,
      logout,
    }}>
      {children}
    </AuthContext.Provider>
  );
}
