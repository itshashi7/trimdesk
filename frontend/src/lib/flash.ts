/**
 * Signature move: briefly ring the control behind a pressed key (and its legend keycap),
 * so keyboard-only billing is visible to someone watching the screen.
 */
export function flash(...ids: string[]) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim();
  for (const id of ids) {
    const el = document.querySelector<HTMLElement>(`[data-flash="${CSS.escape(id)}"]`);
    el?.animate(
      [
        { boxShadow: `0 0 0 2px ${accent}`, transform: 'translateY(1px)' },
        { boxShadow: '0 0 0 0 transparent', transform: 'translateY(0)' },
      ],
      { duration: 560, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' },
    );
  }
}
