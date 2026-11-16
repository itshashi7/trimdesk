import { Keycap } from '../Keycap';

export const SHORTCUTS: { keys: string[]; label: string; flashId: string }[] = [
  { keys: ['/'], label: 'Phone', flashId: 'key-search' },
  { keys: ['E'], label: 'Edit customer', flashId: 'key-e' },
  { keys: ['1–9'], label: 'Add service', flashId: 'key-digit' },
  { keys: ['B'], label: 'Barber', flashId: 'key-b' },
  { keys: ['D'], label: 'Discount', flashId: 'key-d' },
  { keys: ['Del'], label: 'Remove last', flashId: 'key-del' },
  { keys: ['F2'], label: 'Cash', flashId: 'key-f2' },
  { keys: ['F3'], label: 'UPI', flashId: 'key-f3' },
  { keys: ['F4'], label: 'Card', flashId: 'key-f4' },
  { keys: ['Ctrl', 'P'], label: 'Print', flashId: 'key-print' },
  { keys: ['W'], label: 'WhatsApp', flashId: 'key-w' },
  { keys: ['Esc'], label: 'New bill', flashId: 'key-esc' },
];

export function ShortcutLegend() {
  return (
    <footer aria-label="Keyboard shortcuts" className="border-t border-border bg-surface">
      <ul className="flex h-9 items-center gap-x-4 overflow-x-auto px-5">
        {SHORTCUTS.map((s) => (
          <li key={s.flashId} className="flex shrink-0 items-center gap-1.5 text-[12px] text-text-muted">
            <span data-flash={s.flashId} className="flex items-center gap-0.5 rounded-[6px]">
              {s.keys.map((k) => (
                <Keycap key={k}>{k}</Keycap>
              ))}
            </span>
            {s.label}
          </li>
        ))}
      </ul>
    </footer>
  );
}
