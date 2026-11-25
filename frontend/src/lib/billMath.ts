/**
 * Live preview of the bill (tax-inclusive prices), mirroring the backend's BillCalculator (docs/SPEC.md §3) in integer paise
 * so it never drifts by a paisa. The saved bill always uses the server's numbers.
 */

export type Discount = { type: 'FLAT' | 'PERCENT'; value: number };

export interface BillPreview {
  subtotal: number;
  discount: number;
  taxable: number;
  tax: number;
  total: number;
}

const toPaise = (rupees: number) => Math.round(rupees * 100);
const toRupees = (paise: number) => paise / 100;

/** amount × percent / 100, rounded half-up to the paisa (inputs are non-negative). */
function percentOfPaise(amountPaise: number, percent: number): number {
  const basisPoints = Math.round(percent * 100); // 18.00% -> 1800
  return Math.floor((amountPaise * basisPoints + 5000) / 10000);
}

/** The pre-tax part of a tax-inclusive amount: amount × 100 / (100 + rate), rounded half-up. */
function taxablePartPaise(totalPaise: number, ratePercent: number): number {
  const divisor = 10000 + Math.round(ratePercent * 100); // 118.00% -> 11800
  return Math.floor((2 * totalPaise * 10000 + divisor) / (2 * divisor));
}

/** "" -> none, "50" -> ₹50 flat, "10%" -> 10 percent. Returns undefined when the text is not a valid discount. */
export function parseDiscount(text: string): Discount | null | undefined {
  const t = text.trim();
  if (t === '') return null;
  const percent = t.endsWith('%');
  const n = Number(percent ? t.slice(0, -1).trim() : t.replace(/^₹/, ''));
  if (!Number.isFinite(n) || n < 0) return undefined;
  if (percent && n > 100) return undefined;
  return { type: percent ? 'PERCENT' : 'FLAT', value: Math.round(n * 100) / 100 };
}

export function previewBill(prices: number[], discount: Discount | null, taxRatePercent: number): BillPreview {
  const subtotal = prices.reduce((sum, p) => sum + toPaise(p), 0);
  let disc = 0;
  if (discount) {
    disc = discount.type === 'FLAT' ? toPaise(discount.value) : percentOfPaise(subtotal, discount.value);
  }
  disc = Math.min(disc, subtotal);
  // Prices include tax: the customer pays subtotal − discount, and tax is backed out of that.
  const total = subtotal - disc;
  const taxable = taxablePartPaise(total, taxRatePercent);
  return {
    subtotal: toRupees(subtotal),
    discount: toRupees(disc),
    taxable: toRupees(taxable),
    tax: toRupees(total - taxable),
    total: toRupees(total),
  };
}
