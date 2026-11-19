/**
 * Customer names are always Title Case ("ajith RAJ" → "Ajith Raj"). The backend enforces the
 * same rule (PersonNames.java); this copy lets the cashier see the final name while typing.
 */
export function nameCase(value: string): string {
  let startOfWord = true;
  let out = '';
  for (const ch of value) {
    out += startOfWord ? ch.toUpperCase() : ch.toLowerCase();
    // A new word starts after a space, an initial's dot, a hyphen or an apostrophe (D'Souza).
    startOfWord = ch === ' ' || ch === '.' || ch === '-' || ch === "'";
  }
  return out;
}

/** Final form sent to the server: trimmed, single spaces, Title Case. */
export function normalizeName(value: string): string {
  return nameCase(value.trim().replace(/\s+/g, ' '));
}

/**
 * onChange helper for name inputs: rewrites the field in place and keeps the caret where it was,
 * so fixing a typo in the middle of a name doesn't jump the cursor to the end.
 */
export function applyNameCase(input: HTMLInputElement): string {
  const { selectionStart, selectionEnd } = input;
  const cased = nameCase(input.value);
  if (cased !== input.value) {
    input.value = cased;
    input.setSelectionRange(selectionStart, selectionEnd);
  }
  return cased;
}
