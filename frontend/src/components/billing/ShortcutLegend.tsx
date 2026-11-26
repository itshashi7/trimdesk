import { DownloadIcon } from '../Icons';
import { Keycap } from '../Keycap';

export const SHORTCUTS: { keys: string[]; label: string; flashId: string }[] = [
  { keys: ['/'], label: 'Phone', flashId: 'key-search' },
  { keys: ['E'], label: 'Edit', flashId: 'key-e' },
  { keys: ['1–9'], label: 'Add service', flashId: 'key-digit' },
  { keys: ['B'], label: 'Barber', flashId: 'key-b' },
  { keys: ['D'], label: 'Discount', flashId: 'key-d' },
  { keys: ['Del'], label: 'Remove last', flashId: 'key-del' },
  { keys: ['F2'], label: 'Cash', flashId: 'key-f2' },
  { keys: ['F3'], label: 'UPI', flashId: 'key-f3' },
  { keys: ['F4'], label: 'Card', flashId: 'key-f4' },
  { keys: ['S'], label: 'Split pay', flashId: 'key-s' },
  { keys: ['Ctrl', 'P'], label: 'Print', flashId: 'key-print' },
  { keys: ['W'], label: 'WhatsApp', flashId: 'key-w' },
  { keys: ['Esc'], label: 'New bill', flashId: 'key-esc' },
];

export function ShortcutLegend({ onExport, exporting = false }: { onExport?: () => void; exporting?: boolean }) {
  return (
    <footer aria-label="Keyboard shortcuts" className="flex items-center border-t border-border bg-surface">
      {/* Wraps onto a second line on narrower screens, so no shortcut is ever hidden. */}
      <ul className="flex min-h-[36px] min-w-0 flex-1 flex-wrap items-center gap-x-3 gap-y-1 px-4 py-1.5">
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
      {onExport && (
        <button
          type="button"
          onClick={onExport}
          disabled={exporting}
          className="mr-3 flex h-7 shrink-0 items-center gap-1.5 rounded-[7px] px-2.5 text-[12px] font-medium text-text-muted transition-colors hover:bg-surface-muted hover:text-text disabled:cursor-wait disabled:opacity-60"
        >
          <DownloadIcon width={14} height={14} />
          {exporting ? 'Exporting…' : "Today's bills CSV"}
        </button>
      )}
    </footer>
  );
}
