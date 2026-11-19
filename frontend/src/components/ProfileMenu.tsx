import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { useAuth } from '../lib/auth';
import { roleLabel } from '../lib/format';
import type { UserProfile } from '../lib/types';
import { ChevronDownIcon, LogoutIcon } from './Icons';

type Variant = 'sidebar' | 'rail' | 'header';

const initialsOf = (name: string) =>
  name
    .split(/\s+/)
    .map((w) => w[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

/** Scope line for the full card: branch code, franchise name, or "All outlets". */
function scopeLine(u: UserProfile) {
  return u.outletCode ?? u.franchiseName ?? 'All outlets';
}

/**
 * User card that opens a profile panel (name, role, branch, address, franchise, username, sign out).
 * "sidebar": full card; "rail": avatar plus branch code; "header": avatar only (phones).
 */
export function ProfileMenu({ variant }: { variant: Variant }) {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<Record<string, number> | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    if (!open || !triggerRef.current) return;
    const r = triggerRef.current.getBoundingClientRect();
    if (variant === 'rail') setPos({ left: r.right + 10, bottom: window.innerHeight - r.bottom });
    else if (variant === 'sidebar') setPos({ left: r.left, bottom: window.innerHeight - r.top + 8, width: Math.max(r.width, 280) });
    else setPos({ right: window.innerWidth - r.right, top: r.bottom + 8 });
  }, [open, variant]);

  useEffect(() => {
    if (!open) return;
    panelRef.current?.focus();
    const onPointer = (e: PointerEvent) => {
      const t = e.target as Node;
      if (!panelRef.current?.contains(t) && !triggerRef.current?.contains(t)) setOpen(false);
    };
    const onResize = () => setOpen(false);
    document.addEventListener('pointerdown', onPointer);
    window.addEventListener('resize', onResize);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      window.removeEventListener('resize', onResize);
    };
  }, [open]);

  if (!user) return null;
  const initials = initialsOf(user.fullName);

  // Esc closes the panel only; it must not reach page shortcuts (Esc = new bill on billing).
  const onPanelKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      setOpen(false);
      triggerRef.current?.focus();
    }
  };

  const avatar = (size: number, text: number) => (
    <span
      className="flex shrink-0 items-center justify-center rounded-full bg-accent-soft font-semibold text-accent-soft-text"
      style={{ width: size, height: size, fontSize: text }}
    >
      {initials}
    </span>
  );

  const details: { label: string; value: string }[] = [];
  if (user.outletName) {
    details.push({ label: 'Branch', value: `${user.outletName} (${user.outletCode})` });
    const address = [user.outletArea, user.outletCity].filter(Boolean).join(', ');
    if (address) details.push({ label: 'Address', value: address });
    if (user.outletFranchiseName) details.push({ label: 'Franchise', value: user.outletFranchiseName });
  } else if (user.franchiseName) {
    details.push({ label: 'Franchise', value: user.franchiseName });
    details.push({ label: 'Access', value: 'Every outlet in this franchise' });
  } else {
    details.push({ label: 'Access', value: 'All outlets and franchises' });
  }
  details.push({ label: 'Username', value: user.username });

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`Profile: ${user.fullName}, ${roleLabel[user.role]}`}
        className={
          variant === 'sidebar'
            ? 'flex w-full items-center gap-2.5 rounded-[14px] border border-border bg-surface p-2 text-left transition-colors hover:border-border-strong'
            : variant === 'rail'
              ? 'rounded-full p-0.5 transition-shadow hover:ring-2 hover:ring-border-strong'
              : 'rounded-full p-0.5'
        }
      >
        {variant === 'sidebar' ? (
          <>
            {avatar(36, 12.5)}
            <span className="min-w-0 flex-1 leading-tight">
              <span className="block truncate text-[13px] font-medium text-text">{user.fullName}</span>
              <span className="block truncate text-2xs text-text-muted">
                {roleLabel[user.role]} · <span className="font-medium tabular">{scopeLine(user)}</span>
              </span>
            </span>
            <ChevronDownIcon size={16} className={`shrink-0 text-text-muted transition-transform ${open ? '' : 'rotate-180'}`} />
          </>
        ) : variant === 'rail' ? (
          avatar(38, 13)
        ) : (
          avatar(34, 12)
        )}
      </button>

      {open &&
        pos &&
        createPortal(
          <div
            ref={panelRef}
            role="dialog"
            aria-label="Your profile"
            tabIndex={-1}
            onKeyDown={onPanelKey}
            style={{ ...pos, width: pos.width ?? 280 }}
            className="menu-in fixed z-50 rounded-[16px] bg-surface p-4 shadow-pop outline-none"
          >
            <div className="flex items-center gap-3">
              {avatar(46, 15)}
              <div className="min-w-0">
                <p className="truncate text-[15px] font-semibold text-text">{user.fullName}</p>
                <span className="mt-0.5 inline-block rounded-full bg-accent-soft px-2 py-0.5 text-2xs font-semibold text-accent-soft-text">
                  {roleLabel[user.role]}
                </span>
              </div>
            </div>
            <dl className="mt-4 space-y-2.5 border-t border-border pt-3.5 text-[13px]">
              {details.map((d) => (
                <div key={d.label} className="flex gap-3">
                  <dt className="w-[72px] shrink-0 text-text-muted">{d.label}</dt>
                  <dd className="min-w-0 flex-1 font-medium text-text">{d.value}</dd>
                </div>
              ))}
            </dl>
            <button
              type="button"
              onClick={logout}
              className="mt-4 flex h-9 w-full items-center justify-center gap-2 rounded-[10px] bg-surface-muted text-[13px] font-medium text-text transition-colors hover:bg-border"
            >
              <LogoutIcon size={16} />
              Sign out
            </button>
          </div>,
          document.body,
        )}
    </>
  );
}
