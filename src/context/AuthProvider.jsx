import { useEffect, useState } from "react";
import { AuthContext } from "./AuthContext";

const EMPTY_AUTH = { user: null, token: null, expiresAt: null };

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(EMPTY_AUTH);

  useEffect(() => {
    try {
      localStorage.removeItem("devpulse.auth");
    } catch {
      return;
    }
  }, []);

  useEffect(() => {
    if (!auth.token) return;
    const remaining = Math.max(0, Date.parse(auth.expiresAt) - Date.now());
    const timer = setTimeout(() => setAuth(EMPTY_AUTH), remaining);
    return () => clearTimeout(timer);
  }, [auth.token, auth.expiresAt]);

  const login = ({ user, token, expiresAt }) => {
    if (!user?.id || !token || !(Date.parse(expiresAt) > Date.now())) {
      throw new Error("Invalid or expired login session.");
    }
    setAuth({ user, token, expiresAt });
  };
  const logout = () => setAuth(EMPTY_AUTH);

  const value = {
    user: auth.user,
    token: auth.token,
    isAuthenticated: Boolean(auth.token),
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
