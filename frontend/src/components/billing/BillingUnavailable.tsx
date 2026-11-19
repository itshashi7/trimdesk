import { Keyboard } from 'lucide-react';
import { Link } from 'react-router-dom';
import { canViewReports, useAuth } from '../../lib/auth';

/**
 * Shown instead of the billing counter on touch screens narrower than 1024px (phones, portrait
 * tablets). Laptops always get billing, even when zoomed in. Billing is built for a laptop
 * keyboard (number keys, F2-F4, Ctrl+P to an 80mm printer; CLAUDE.md §6), so phones get a clear
 * message rather than a cramped page. Managers get links to what does work on a phone.
 */
export function BillingUnavailable() {
  const { user } = useAuth();
  const branch = user?.outletName ? `${user.outletName} (${user.outletCode})` : null;

  return (
    <main className="flex flex-1 items-center justify-center px-5 py-12">
      <div className="w-full max-w-[380px] rounded-card border border-border bg-surface p-6 text-center">
        <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-[14px] bg-accent-soft text-accent-soft-text">
          <Keyboard size={24} strokeWidth={1.75} aria-hidden />
        </span>
        <h1 className="mt-4 text-[18px] font-semibold tracking-[-0.01em] text-text">Billing needs the counter laptop</h1>
        <p className="mt-2 text-[14px] leading-relaxed text-text-muted">
          The counter runs on keyboard shortcuts, F‑keys and an 80mm receipt printer. Open GroomDesk on a laptop or
          desktop{branch ? ` at ${branch}` : ''} to bill.
        </p>
        {user && canViewReports(user.role) && (
          <div className="mt-5 grid grid-cols-2 gap-2">
            <Link
              to="/reports/outlet"
              className="flex h-10 items-center justify-center rounded-[10px] bg-accent text-[13.5px] font-medium text-accent-contrast"
            >
              Outlet report
            </Link>
            <Link
              to="/customers"
              className="flex h-10 items-center justify-center rounded-[10px] bg-accent-soft text-[13.5px] font-medium text-accent-soft-text"
            >
              Customers
            </Link>
          </div>
        )}
      </div>
    </main>
  );
}
