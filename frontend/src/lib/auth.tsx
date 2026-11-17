import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, setApiSession } from './api';
import type { Role, UserProfile } from './types';

const STORAGE_KEY = 'groomdesk.session';

interface Session {
  token: string;
  expiresAt: string;
  user: UserProfile;
}

interface AuthContextValue {
  user: UserProfile | null;
  login: (username: string, password: string) => Promise<UserProfile>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    return new Date(session.expiresAt).getTime() > Date.now() ? session : null;
  } catch {
    return null;
  }
}

function saveSession(session: Session | null) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // storage unavailable (private mode); the session just won't survive a reload
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session | null>(() => {
    const s = loadSession();
    setApiSession(s?.token ?? null);
    return s;
  });

  const logout = useCallback(() => {
    saveSession(null);
    setApiSession(null);
    setSession(null);
    queryClient.clear();
  }, [queryClient]);

  // Any 401 from the API (expired or invalid token) signs the user out.
  setApiSession(session?.token ?? null, logout);

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.login(username, password);
    const next = { token: res.token, expiresAt: res.expiresAt, user: res.user };
    saveSession(next);
    setApiSession(next.token, logout);
    queryClient.clear();
    setSession(next);
    return res.user;
  }, [logout, queryClient]);

  const value = useMemo(() => ({ user: session?.user ?? null, login, logout }), [session, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

export const canBill = (role: Role) => role === 'CASHIER' || role === 'OUTLET_MANAGER';

/** Outlet reports and customer profiles: everyone except the cashier (CLAUDE.md §4). */
export const canViewReports = (role: Role) => role !== 'CASHIER';

/** Chain dashboard and royalty: HQ (all outlets) and franchise owners (their outlets). */
export const canViewDashboard = (role: Role) => role === 'HQ_ADMIN' || role === 'FRANCHISE_OWNER';

/** Where each role lands after signing in. */
export const homePath = (role: Role) => (canBill(role) ? '/billing' : canViewDashboard(role) ? '/dashboard' : '/reports/outlet');
