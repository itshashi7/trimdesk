import { APP_SLUG } from './brand';
import { dateTimeIST } from './format';
import type { BillRow, PaymentPart } from './types';

/** Quotes a cell only when it needs it. */
export function csvCell(value: string | number): string {
  const s = String(value);
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** Downloads rows as a UTF-8 CSV (with BOM so Excel shows ₹ and Tamil names correctly). */
export function downloadCsv(fileName: string, rows: (string | number)[][]) {
  const csv = rows.map((r) => r.map(csvCell).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob(['﻿', csv], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}

const MODE_TEXT = { CASH: 'Cash', UPI: 'UPI', CARD: 'Card', MIXED: 'Split' } as const;

/** One row per bill plus a totals row. Plain numbers so Excel can sum them; the cash column is the drawer count. */
export function downloadBillsCsv(outletCode: string, date: string, bills: BillRow[]) {
  const n = (v: number) => v.toFixed(2);
  const rows: (string | number)[][] = [
    ['Bill no', 'Date and time', 'Customer', 'Phone', 'Services (barber)', 'Subtotal', 'Discount', 'Tax included',
      'Total', 'Paid by', 'Cash', 'UPI', 'Card', 'Cashier'],
  ];
  for (const b of bills) {
    rows.push([b.billNo, dateTimeIST(b.createdAt), b.customerName, b.customerPhone, b.items ?? '', n(b.subtotal),
      n(b.discount), n(b.taxAmount), n(b.total), MODE_TEXT[b.paymentMode], n(b.cash), n(b.upi), n(b.card), b.cashierName]);
  }
  const sum = (pick: (b: BillRow) => number) => n(bills.reduce((t, b) => t + pick(b), 0));
  rows.push([`Total (${bills.length} bills)`, '', '', '', '', sum((b) => b.subtotal), sum((b) => b.discount),
    sum((b) => b.taxAmount), sum((b) => b.total), '', sum((b) => b.cash), sum((b) => b.upi), sum((b) => b.card), '']);
  downloadCsv(`${APP_SLUG}-bills-${outletCode}-${date}.csv`, rows);
}

/** "Cash ₹100 + UPI ₹400" for a split, "UPI" for a single mode. */
export function paidByText(payments: PaymentPart[], money: (v: number) => string): string {
  if (payments.length <= 1) return payments[0] ? MODE_TEXT[payments[0].mode] : '';
  return payments.map((p) => `${MODE_TEXT[p.mode]} ${money(p.amount)}`).join(' + ');
}
