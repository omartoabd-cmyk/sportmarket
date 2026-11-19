import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Navigate, useLocation } from "react-router";
import { api, ApiError } from "../api/client";
import type { LoginInput, User } from "../types";

interface Session {
  token: string;
  user: User;
  expiresAt: number;
}
interface AuthState {
  user: User | null;
  notice: string;
  dismissNotice: () => void;
  login: (input: LoginInput) => Promise<void>;
  logout: () => void;
  authorized: <T>(operation: (token: string) => Promise<T>) => Promise<T>;
}
const AuthContext = createContext<AuthState | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [notice, setNotice] = useState("");
  const current = useRef<Session | null>(null);
  const generation = useRef(0);
  const clearSession = useCallback((message = "") => {
    generation.current += 1;
    current.current = null;
    setSession(null);
    setNotice(message);
  }, []);
  const login = useCallback(async (input: LoginInput) => {
    const attempt = ++generation.current;
    const startedAt = Date.now();
    const result = await api.login(input);
    const user = await api.me(result.accessToken);
    if (attempt !== generation.current)
      throw new Error("Sign-in was canceled. Please try again.");
    const next = {
      token: result.accessToken,
      user,
      expiresAt: startedAt + result.expiresIn * 1000,
    };
    current.current = next;
    setSession(next);
    setNotice("");
  }, []);
  const logout = useCallback(
    () => clearSession("You have signed out."),
    [clearSession],
  );
  const authorized = useCallback(
    async <T,>(operation: (token: string) => Promise<T>): Promise<T> => {
      const active = current.current;
      if (!active || active.expiresAt <= Date.now()) {
        clearSession("Your session has expired. Please log in again.");
        throw new ApiError(401, { detail: "Please log in to continue." });
      }
      try {
        return await operation(active.token);
      } catch (error) {
        if (
          error instanceof ApiError &&
          error.status === 401 &&
          current.current === active
        ) {
          clearSession(
            "Your session has expired or is no longer valid. Please log in again.",
          );
        }
        throw error;
      }
    },
    [clearSession],
  );
  useEffect(() => {
    if (!session) return;
    const expire = () => {
      if (current.current === session && session.expiresAt <= Date.now())
        clearSession("Your session has expired. Please log in again.");
    };
    const timeout = window.setTimeout(
      expire,
      Math.max(0, session.expiresAt - Date.now()),
    );
    window.addEventListener("focus", expire);
    document.addEventListener("visibilitychange", expire);
    return () => {
      window.clearTimeout(timeout);
      window.removeEventListener("focus", expire);
      document.removeEventListener("visibilitychange", expire);
    };
  }, [session, clearSession]);
  return (
    <AuthContext.Provider
      value={{
        user: session?.user || null,
        notice,
        dismissNotice: () => setNotice(""),
        login,
        logout,
        authorized,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth requires AuthProvider.");
  return context;
}
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  return user ? (
    children
  ) : (
    <Navigate
      to="/login"
      replace
      state={{ from: location.pathname + location.search }}
    />
  );
}
