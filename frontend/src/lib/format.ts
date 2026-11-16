const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });

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
export const dateTimeIST = (iso: string) => istDateTime.format(new Date(iso));

/** 98765 43210 */
export const phoneDisplay = (phone: string) => (phone.length === 10 ? `${phone.slice(0, 5)} ${phone.slice(5)}` : phone);

export const roleLabel: Record<string, string> = {
  HQ_ADMIN: 'HQ',
  FRANCHISE_OWNER: 'Franchise owner',
  OUTLET_MANAGER: 'Outlet manager',
  CASHIER: 'Cashier',
};
