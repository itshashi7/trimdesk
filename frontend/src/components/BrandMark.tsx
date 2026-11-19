import { Scissors } from 'lucide-react';

/**
 * TrimDesk logo: gradient tile with a bold white scissors and a soft top highlight.
 * Deliberately unlike the flat nav buttons so it never reads as something to click.
 */
export function BrandMark({ size = 36 }: { size?: number }) {
  return (
    <span
      aria-hidden
      className="relative flex shrink-0 items-center justify-center overflow-hidden text-white shadow-[0_2px_6px_rgb(37_99_235/0.35)]"
      style={{
        width: size,
        height: size,
        borderRadius: size * 0.3,
        background: 'linear-gradient(145deg, var(--brand-from), var(--brand-to))',
      }}
    >
      <span className="absolute inset-x-0 top-0 h-1/2 bg-white/15" />
      <Scissors size={size * 0.52} strokeWidth={2.3} className="relative" />
    </span>
  );
}
