import type { RefObject } from 'react';
import { money } from '../../lib/format';
import type { PaymentMode, PaymentPart } from '../../lib/types';
import { Keycap } from '../Keycap';

export const SPLIT_MODES: { mode: PaymentMode; label: string }[] = [
  { mode: 'CASH', label: 'Cash' },
  { mode: 'UPI', label: 'UPI' },
  { mode: 'CARD', label: 'Card' },
];

export type SplitValues = Record<PaymentMode, string>;
export const EMPTY_SPLIT: SplitValues = { CASH: '', UPI: '', CARD: '' };

const AMOUNT = /^\d{1,7}(\.\d{1,2})?$/;

export interface SplitCheck {
  /** Non-zero parts in Cash, UPI, Card order. */
  parts: PaymentPart[];
  /** Total minus what has been entered, in paise; negative when over. */
  remainingPaise: number;
  /** Some field is not an amount like 100 or 99.50. */
  invalid: boolean;
}

/** Works in paise so 0.1 + 0.2 style float errors never block a bill. */
export function checkSplit(values: SplitValues, total: number): SplitCheck {
  let entered = 0;
  let invalid = false;
  const parts: PaymentPart[] = [];
  for (const { mode } of SPLIT_MODES) {
    const text = values[mode].trim();
    if (!text) continue;
    if (!AMOUNT.test(text)) {
      invalid = true;
      continue;
    }
    const paise = Math.round(Number(text) * 100);
    if (paise > 0) {
      entered += paise;
      parts.push({ mode, amount: paise / 100 });
    }
  }
  return { parts, remainingPaise: Math.round(total * 100) - entered, invalid };
}

export interface SplitProps {
  open: boolean;
  values: SplitValues;
  onChange: (mode: PaymentMode, value: string) => void;
  onOpen: () => void;
  onCancel: () => void;
  onSave: () => void;
  firstRef: RefObject<HTMLInputElement>;
}

/**
 * Cash / UPI / card amounts for one bill. After the first amount, moving into an empty field fills in
 * what is still due, so "S 100 Tab Enter" bills ₹100 cash and the rest by UPI.
 */
export function SplitPayment({ values, onChange, onCancel, onSave, firstRef, total, saving }: SplitProps & { total: number; saving: boolean }) {
  const check = checkSplit(values, total);
  const ready = !check.invalid && check.remainingPaise === 0 && check.parts.length > 0;
  const remaining = check.remainingPaise / 100;

  return (
    <div className="mt-4 rounded-[10px] border border-border bg-surface-muted p-3" aria-label="Split payment">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-[13px] font-semibold text-text">Split payment</span>
        <button type="button" onClick={onCancel} className="flex items-center gap-1.5 text-2xs text-text-muted hover:text-text">
          Cancel <Keycap>Esc</Keycap>
        </button>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {SPLIT_MODES.map(({ mode, label }, i) => (
          <label key={mode} className="block">
            <span className="mb-1 block text-2xs font-medium text-text-muted">{label}</span>
            <input
              ref={i === 0 ? firstRef : undefined}
              data-split-input
              inputMode="decimal"
              value={values[mode]}
              onChange={(e) => onChange(mode, e.target.value.replace(/[^\d.]/g, ''))}
              onFocus={(e) => {
                // Once something is entered elsewhere, an empty field offers what is still due
                // (selected, so typing replaces it). The first field stays empty for the cashier to type.
                const fill = !values[mode] && check.parts.length > 0 && check.remainingPaise > 0 && !check.invalid;
                if (fill) onChange(mode, String(remaining));
                const input = e.target;
                requestAnimationFrame(() => input.select());
              }}
              placeholder="₹0"
              aria-label={`${label} amount`}
              className="h-9 w-full rounded-[8px] border border-border bg-surface px-2 text-right text-[14px] text-text outline-none tabular focus:border-border-strong"
            />
          </label>
        ))}
      </div>
      <div className="mt-2.5 flex items-center justify-between gap-3">
        <span
          role="status"
          className={`text-[12.5px] tabular ${check.invalid || check.remainingPaise < 0 ? 'text-danger' : check.remainingPaise === 0 ? 'text-success' : 'text-text-muted'}`}
        >
          {check.invalid
            ? 'Enter amounts like 100 or 99.50'
            : check.remainingPaise > 0
              ? `${money(remaining)} still due`
              : check.remainingPaise < 0
                ? `Over by ${money(-remaining)}`
                : 'Adds up to the total'}
        </span>
        <button
          type="button"
          data-flash="pay-MIXED"
          onClick={onSave}
          disabled={!ready || saving}
          className="flex h-10 items-center gap-2 rounded-[10px] bg-accent px-4 text-[13.5px] font-semibold text-accent-contrast transition-opacity disabled:opacity-40"
        >
          {saving ? 'Saving…' : 'Save bill'}
          <Keycap tone="onAccent" className="bg-transparent">Enter</Keycap>
        </button>
      </div>
    </div>
  );
}
