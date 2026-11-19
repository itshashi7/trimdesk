import type { LucideProps } from 'lucide-react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { canBill, canViewDashboard, canViewReports, useAuth } from '../lib/auth';
import { BrandMark } from './BrandMark';
import { BillingIcon, CustomersIcon, DashboardIcon, RoyaltyIcon, StoreIcon } from './Icons';
import { ProfileMenu } from './ProfileMenu';

interface NavItem {
  to: string;
  label: string;
  /** Shorter label for the phone tab bar. */
  short: string;
  Icon: (p: LucideProps) => JSX.Element;
}

/**
 * Firecorp-style shell. Desktop: left sidebar (brand tile, menu, user card). On the billing screen the
 * sidebar shrinks to an icon rail so the counter keeps its width at 1366px. Phones: top bar plus an
 * iOS-style bottom tab bar.
 */
export function AppShell() {
  const { user } = useAuth();
  const { pathname } = useLocation();
  if (!user) return null;

  const nav: NavItem[] = [
    ...(canBill(user.role) ? [{ to: '/billing', label: 'Billing', short: 'Billing', Icon: BillingIcon }] : []),
    ...(canViewDashboard(user.role)
      ? [
          { to: '/dashboard', label: 'Dashboard', short: 'Dashboard', Icon: DashboardIcon },
          { to: '/royalty', label: 'Royalty', short: 'Royalty', Icon: RoyaltyIcon },
        ]
      : []),
    ...(canViewReports(user.role)
      ? [
          { to: '/reports/outlet', label: 'Outlet report', short: 'Outlets', Icon: StoreIcon },
          { to: '/customers', label: 'Customers', short: 'Customers', Icon: CustomersIcon },
        ]
      : []),
  ];

  const rail = pathname.startsWith('/billing');
  const scope = user.outletName ?? user.franchiseName ?? 'All outlets';

  return (
    <div className="flex h-screen bg-bg">
      <aside
        aria-label="Sidebar"
        className={`hidden shrink-0 flex-col py-4 transition-[width] duration-200 md:flex ${rail ? 'w-[72px] px-3' : 'w-[236px] px-4'}`}
      >
        {rail ? (
          <div className="flex flex-col items-center">
            <BrandMark size={38} />
            <span className="mt-4 h-px w-8 bg-border-strong" aria-hidden />
          </div>
        ) : (
          <div className="flex items-center gap-3 rounded-[14px] border border-border bg-surface p-2">
            <BrandMark size={36} />
            <span className="min-w-0 leading-tight">
              <span className="block text-[14px] font-semibold tracking-[-0.01em] text-text">GroomDesk</span>
              <span className="block truncate text-2xs text-text-muted">{scope}</span>
            </span>
          </div>
        )}

        {!rail && <p className="mb-1.5 mt-6 px-3 text-2xs font-medium text-text-muted">Menu</p>}
        <nav aria-label="Main" className={`flex flex-col gap-1 ${rail ? 'mt-4 items-center' : ''}`}>
          {nav.map(({ to, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              title={rail ? label : undefined}
              aria-label={rail ? label : undefined}
              className={({ isActive }) =>
                `flex h-10 items-center gap-3 rounded-[10px] text-[13.5px] font-medium transition-colors ${
                  rail ? 'w-11 justify-center' : 'px-3'
                } ${isActive ? 'bg-surface text-text shadow-raised' : 'text-text-muted hover:bg-surface-muted hover:text-text'}`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon size={18} className={isActive ? 'text-accent' : ''} />
                  {!rail && label}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="flex-1" />

        <div className={rail ? 'flex justify-center' : ''}>
          <ProfileMenu variant={rail ? 'rail' : 'sidebar'} />
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center gap-3 border-b border-border bg-surface px-4 md:hidden">
          <BrandMark size={32} />
          <span className="min-w-0 flex-1 leading-tight">
            <span className="block text-[14px] font-semibold text-text">GroomDesk</span>
            <span className="block truncate text-2xs text-text-muted">{scope}</span>
          </span>
          <ProfileMenu variant="header" />
        </header>

        <div className="flex min-h-0 flex-1 flex-col overflow-auto">
          <Outlet />
        </div>

        {nav.length > 1 && (
          <nav
            aria-label="Main"
            className="grid shrink-0 border-t border-border bg-surface pb-[env(safe-area-inset-bottom)] md:hidden"
            style={{ gridTemplateColumns: `repeat(${nav.length}, minmax(0, 1fr))` }}
          >
            {nav.map(({ to, short, Icon }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `flex flex-col items-center gap-0.5 pb-1.5 pt-2 text-[10.5px] font-medium ${isActive ? 'text-accent' : 'text-text-muted'}`
                }
              >
                <Icon size={21} />
                {short}
              </NavLink>
            ))}
          </nav>
        )}
      </div>
    </div>
  );
}
