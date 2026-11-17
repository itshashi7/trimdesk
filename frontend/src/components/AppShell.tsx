import { NavLink, Outlet } from 'react-router-dom';
import { canBill, canViewReports, useAuth } from '../lib/auth';
import { roleLabel } from '../lib/format';
import { LogoutIcon, StoreIcon } from './Icons';

export function AppShell() {
  const { user, logout } = useAuth();
  if (!user) return null;

  const nav = [
    ...(canBill(user.role) ? [{ to: '/billing', label: 'Billing' }] : []),
    ...(canViewReports(user.role)
      ? [
          { to: '/reports/outlet', label: 'Outlet report' },
          { to: '/customers', label: 'Customers' },
        ]
      : []),
  ];

  const scope = user.outletName ?? user.franchiseName ?? 'All outlets';

  return (
    <div className="flex h-screen flex-col">
      <header className="sticky top-0 z-30 border-b border-border bg-surface">
        <div className="flex h-[52px] items-center gap-3 px-4 sm:gap-6 sm:px-5">
          <span className="shrink-0 text-[16px] font-semibold tracking-[-0.02em] text-text">GroomDesk</span>

          <nav aria-label="Main" className="-mx-1 flex min-w-0 items-center gap-1 overflow-x-auto px-1">
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `shrink-0 whitespace-nowrap rounded-lg px-3 py-1.5 text-[13.5px] font-medium transition-colors ${
                    isActive ? 'bg-accent text-accent-contrast' : 'text-text-muted hover:bg-surface-muted hover:text-text'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex shrink-0 items-center gap-3 sm:gap-4">
            <span className="hidden items-center gap-1.5 text-[13px] text-text-muted lg:flex">
              <StoreIcon width={16} height={16} />
              <span className="font-medium text-text">{scope}</span>
              {user.outletCode && <span className="tabular">· {user.outletCode}</span>}
            </span>
            <span className="hidden h-5 w-px bg-border lg:block" aria-hidden />
            <span className="hidden text-right leading-tight md:block">
              <span className="block text-[13px] font-medium text-text">{user.fullName}</span>
              <span className="block text-2xs text-text-muted">{roleLabel[user.role]}</span>
            </span>
            <button
              type="button"
              onClick={logout}
              className="rounded-lg p-1.5 text-text-muted transition-colors hover:bg-surface-muted hover:text-text"
              aria-label={`Sign out ${user.fullName}`}
              title="Sign out"
            >
              <LogoutIcon />
            </button>
          </div>
        </div>
      </header>
      <div className="flex min-h-0 flex-1 flex-col overflow-auto">
        <Outlet />
      </div>
    </div>
  );
}
