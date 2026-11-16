import type { ReactNode } from 'react';

interface KeycapProps {
  children: ReactNode;
  /** "onAccent" for keycaps sitting on an accent-filled button. */
  tone?: 'default' | 'onAccent';
  className?: string;
}

/** A small keyboard key. Every keyboard action in the app shows one. */
export function Keycap({ children, tone = 'default', className = '' }: KeycapProps) {
  const toneClass =
    tone === 'onAccent'
      ? 'border-[color-mix(in_srgb,var(--accent-contrast)_45%,transparent)] text-accent-contrast'
      : 'border-border-strong bg-surface text-text-muted shadow-[0_1px_0_var(--border-strong)]';
  return (
    <kbd
      className={`inline-flex h-[20px] min-w-[20px] items-center justify-center rounded-[5px] border px-[5px] font-sans text-2xs font-semibold tabular ${toneClass} ${className}`}
    >
      {children}
    </kbd>
  );
}
