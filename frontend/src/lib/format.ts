const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const inrCompact = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', notation: 'compact', maximumFractionDigits: 1 });

/** ₹1.6L, ₹1.6Cr: Indian compact units for chart axes. */
export const moneyCompact = (value: number) => inrCompact.format(value);

/** ₹1,23,456.00 (Indian digit grouping). */
export const money = (value: number) => inr.format(value);

/** ₹1,23,456 for summaries where paise are noise. */
export const moneyWhole = (value: number) => inrWhole.format(value);

const istDate = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', year: 'numeric' });
const istDateTime = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
});

export const dateIST = (iso: string) => istDate.format(new Date(iso));

const istIsoDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' });

/** Today's business date in IST as YYYY-MM-DD. */
export const todayIST = () => istIsoDate.format(new Date());

/** Shift a YYYY-MM-DD date by whole days (calendar arithmetic, no time zone involved). */
export function shiftDate(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

const longDate = new Intl.DateTimeFormat('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });

/** "Monday, 16 November 2026" for a YYYY-MM-DD date. */
export const longDateLabel = (isoDate: string) => longDate.format(new Date(`${isoDate}T00:00:00Z`));

const istTime = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', hour: 'numeric', minute: '2-digit', hour12: true });
export const timeIST = (iso: string) => istTime.format(new Date(iso));
export const dateTimeIST = (iso: string) => istDateTime.format(new Date(iso));

/** 98765 43210 */
export const phoneDisplay = (phone: string) => (phone.length === 10 ? `${phone.slice(0, 5)} ${phone.slice(5)}` : phone);

export const roleLabel: Record<string, string> = {
  HQ_ADMIN: 'HQ',
  FRANCHISE_OWNER: 'Franchise owner',
  OUTLET_MANAGER: 'Outlet manager',
  CASHIER: 'Cashier',
};
