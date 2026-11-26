import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { BrandMark } from '../components/BrandMark';
import { Keycap } from '../components/Keycap';
import { errorMessage } from '../lib/api';
import { homePath, useAuth } from '../lib/auth';
import { APP_NAME } from '../lib/brand';

const DEMO_PASSWORD = 'demo123';

/** Seeded demo users (docs/SPEC.md §7). T. Nagar is the busiest outlet; owner1's franchise runs it. */
const DEMO_ROLES = [
  { key: '1', role: 'Cashier', username: 'cashier_tnr', scope: 'Billing at the T. Nagar counter' },
  { key: '2', role: 'Outlet manager', username: 'manager_tnr', scope: 'Billing and reports for T. Nagar' },
  { key: '3', role: 'Franchise owner', username: 'owner1', scope: 'Reports for Coromandel Grooming outlets' },
  { key: '4', role: 'HQ', username: 'hq', scope: 'Every outlet, every franchise' },
] as const;

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function signIn(user: string, pass: string) {
    setBusy(user);
    setError(null);
    try {
      const profile = await login(user, pass);
      navigate(homePath(profile.role), { replace: true });
    } catch (e) {
      setError(errorMessage(e, 'Sign-in failed. Try again.'));
      setBusy(null);
    }
  }

  // Keys 1-4 sign in as a demo role, unless the user is typing in the form.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (target.tagName === 'INPUT' || e.ctrlKey || e.metaKey || e.altKey || busy) return;
      const demo = DEMO_ROLES.find((r) => r.key === e.key);
      if (demo) {
        e.preventDefault();
        void signIn(demo.username, DEMO_PASSWORD);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (username.trim() && password) void signIn(username.trim(), password);
  };

  return (
    <main className="app-min-height flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-[420px]">
        <div className="mb-8">
          <div className="flex items-center gap-3">
            <BrandMark size={44} />
            <span className="text-[26px] font-semibold tracking-[-0.02em] text-text">{APP_NAME}</span>
          </div>
          <p className="mt-1.5 text-[14px] text-text-muted">Counter billing and reporting for every outlet in the chain.</p>
        </div>

        <section aria-labelledby="demo-heading" className="rounded-card border border-border bg-surface">
          <div className="flex items-baseline justify-between px-5 pb-2 pt-4">
            <h1 id="demo-heading" className="text-[15px] font-semibold text-text">Sign in to the demo</h1>
            <span className="text-2xs text-text-muted">
              Press <span className="tabular">1–4</span>
            </span>
          </div>
          <ul className="px-2 pb-2">
            {DEMO_ROLES.map((r) => (
              <li key={r.key}>
                <button
                  type="button"
                  onClick={() => void signIn(r.username, DEMO_PASSWORD)}
                  disabled={busy !== null}
                  className="group flex w-full items-center gap-3.5 rounded-[10px] px-3 py-2.5 text-left transition-colors hover:bg-surface-muted disabled:cursor-wait disabled:opacity-60"
                >
                  <Keycap className="h-[24px] min-w-[24px] text-[12px]">{r.key}</Keycap>
                  <span className="min-w-0 flex-1">
                    <span className="block text-[14px] font-medium text-text">
                      {busy === r.username ? 'Signing in…' : `Login as ${r.role}`}
                    </span>
                    <span className="block truncate text-[12.5px] text-text-muted">{r.scope}</span>
                  </span>
                  <span className="text-2xs text-text-muted">{r.username}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>

        <form onSubmit={onSubmit} className="mt-6" aria-label="Sign in with a username">
          <div className="mb-2 text-[12.5px] font-medium text-text-muted">Or sign in with a username</div>
          <div className="flex gap-2">
            <label className="sr-only" htmlFor="username">Username</label>
            <input
              id="username"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Username"
              className="h-10 min-w-0 flex-1 rounded-[10px] border border-border bg-surface px-3 text-[14px] outline-none focus:border-border-strong"
            />
            <label className="sr-only" htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Password"
              className="h-10 min-w-0 flex-1 rounded-[10px] border border-border bg-surface px-3 text-[14px] outline-none focus:border-border-strong"
            />
            <button
              type="submit"
              disabled={busy !== null || !username.trim() || !password}
              className="h-10 rounded-[10px] bg-accent px-4 text-[14px] font-medium text-accent-contrast transition-opacity disabled:opacity-40"
            >
              Sign in
            </button>
          </div>
          <p className="mt-2 text-2xs text-text-muted">
            Demo accounts use the password <span className="font-semibold text-text">{DEMO_PASSWORD}</span>.
          </p>
        </form>

        {error && (
          <p role="alert" className="mt-4 rounded-[10px] bg-danger-surface px-3 py-2 text-[13px] text-danger">
            {error}
          </p>
        )}

        <p className="mt-10 text-center text-2xs text-text-muted">Prototype · demo data only</p>
      </div>
    </main>
  );
}
