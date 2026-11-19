import { useState, type KeyboardEvent } from 'react';
import { ApiError } from '../../lib/api';
import { applyNameCase, normalizeName } from '../../lib/names';
import type { Customer } from '../../lib/types';
import { Keycap } from '../Keycap';

export type CustomerChanges = { phone?: string; name?: string; notes?: string };

interface Props {
  customer: Customer;
  onSave: (changes: CustomerChanges) => Promise<void>;
  onCancel: () => void;
}

const PHONE_PATTERN = /^[6-9]\d{9}$/;

/** Inline correction of a customer's name, phone or notes. Enter saves, Esc cancels. */
export function CustomerEditForm({ customer, onSave, onCancel }: Props) {
  const [name, setName] = useState(customer.name);
  const [phone, setPhone] = useState(customer.phone);
  const [notes, setNotes] = useState(customer.notes ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function save() {
    const errors: Record<string, string> = {};
    if (!name.trim()) errors.name = 'Enter the customer name.';
    if (!PHONE_PATTERN.test(phone)) errors.phone = '10 digits, starting with 6, 7, 8 or 9.';
    setFieldErrors(errors);
    if (Object.keys(errors).length) return;

    // Send only what changed.
    const changes: CustomerChanges = {};
    if (normalizeName(name) !== customer.name) changes.name = normalizeName(name);
    if (phone !== customer.phone) changes.phone = phone;
    if (notes.trim() !== (customer.notes ?? '')) changes.notes = notes.trim();
    if (Object.keys(changes).length === 0) {
      onCancel();
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await onSave(changes);
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setFieldErrors(e.fieldErrors);
      setError(e instanceof ApiError ? e.message : 'Could not save the changes.');
      setSaving(false);
    }
  }

  // Enter and Esc belong to this form while it is open, not to the bill.
  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      e.stopPropagation();
      void save();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      onCancel();
    }
  };

  const input =
    'h-9 w-full rounded-[8px] border bg-surface px-2.5 text-[14px] text-text outline-none focus:border-border-strong';

  return (
    <div onKeyDown={onKeyDown} role="group" aria-label="Edit customer">
      <div className="grid grid-cols-[1.3fr_1fr] gap-2.5">
        <label className="block">
          <span className="mb-1 block text-2xs font-medium text-text-muted">Name</span>
          <input
            autoFocus
            value={name}
            maxLength={120}
            onChange={(e) => setName(applyNameCase(e.target))}
            autoCapitalize="words"
            aria-invalid={!!fieldErrors.name}
            className={`${input} ${fieldErrors.name ? 'border-danger' : 'border-border'}`}
          />
          {fieldErrors.name && <span className="mt-0.5 block text-2xs text-danger">{fieldErrors.name}</span>}
        </label>
        <label className="block">
          <span className="mb-1 block text-2xs font-medium text-text-muted">Phone</span>
          <input
            value={phone}
            inputMode="numeric"
            maxLength={10}
            onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
            aria-invalid={!!fieldErrors.phone}
            className={`${input} tabular ${fieldErrors.phone ? 'border-danger' : 'border-border'}`}
          />
          {fieldErrors.phone && <span className="mt-0.5 block text-2xs text-danger">{fieldErrors.phone}</span>}
        </label>
      </div>
      <label className="mt-2.5 block">
        <span className="mb-1 block text-2xs font-medium text-text-muted">Notes</span>
        <input
          value={notes}
          maxLength={500}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Preferences, allergies, usual style"
          className={`${input} border-border`}
        />
      </label>

      {error && (
        <p role="alert" className="mt-2 rounded-[8px] bg-danger-surface px-3 py-1.5 text-[12.5px] text-danger">
          {error}
        </p>
      )}

      <div className="mt-3 flex items-center gap-2">
        <button
          type="button"
          onClick={() => void save()}
          disabled={saving}
          className="flex h-8 items-center gap-2 rounded-[8px] bg-accent px-3 text-[13px] font-semibold text-accent-contrast disabled:opacity-60"
        >
          {saving ? 'Saving…' : 'Save'}
          <Keycap tone="onAccent">Enter</Keycap>
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="flex h-8 items-center gap-2 rounded-[8px] px-3 text-[13px] font-medium bg-accent-soft text-accent-soft-text hover:brightness-[0.97]"
        >
          Cancel
          <Keycap>Esc</Keycap>
        </button>
        <span className="ml-auto text-2xs text-text-muted">Changes apply at every outlet.</span>
      </div>
    </div>
  );
}
