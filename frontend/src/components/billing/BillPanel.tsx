import type { RefObject } from 'react';
import type { BillPreview } from '../../lib/billMath';
import { dateTimeIST, money } from '../../lib/format';
import { paidByText } from '../../lib/csv';
import type { Barber, Bill, BillPaymentMode, PaymentMode, ServiceItem } from '../../lib/types';
import { ChatIcon, CheckIcon, CloseIcon, PrintIcon } from '../Icons';
import { Keycap } from '../Keycap';
import { BarberSelect } from './BarberSelect';
import { SplitPayment, type SplitProps } from './SplitPayment';

export interface DraftItem {
  uid: number;
  service: ServiceItem;
  barberId: number;
}

interface Props {
  items: DraftItem[];
  barbers: Barber[];
  onBarberChange: (uid: number, barberId: number) => void;
  onRemove: (uid: number) => void;
  discountText: string;
  onDiscountChange: (value: string) => void;
  discountRef: RefObject<HTMLInputElement>;
  discountInvalid: boolean;
  preview: BillPreview;
  taxRate: number;
  customerName: string | null;
  /** Branch being billed for, e.g. "T. Nagar (TNR)". */
  branch: string | null;
  saving: BillPaymentMode | null;
  error: string | null;
  onPay: (mode: PaymentMode) => void;
  /** Split payment: open while the cashier enters cash / UPI / card amounts. */
  split: SplitProps;
  saved: Bill | null;
  onPrint: () => void;
  onWhatsApp: () => void;
  onNewBill: () => void;
}

const PAY_BUTTONS: { mode: PaymentMode; key: string; label: string; primary: boolean }[] = [
  { mode: 'CASH', key: 'F2', label: 'Cash', primary: false },
  { mode: 'UPI', key: 'F3', label: 'UPI', primary: true },
  { mode: 'CARD', key: 'F4', label: 'Card', primary: false },
];

const modeLabel: Record<PaymentMode, string> = { CASH: 'cash', UPI: 'UPI', CARD: 'card' };

export function BillPanel(props: Props) {
  return (
    <section aria-label="Bill" className="flex min-h-0 flex-col rounded-card border border-border bg-surface">
      {props.saved ? <SavedBill {...props} saved={props.saved} /> : <DraftBill {...props} />}
    </section>
  );
}

function DraftBill(p: Props) {
  const { items, barbers, preview } = p;
  return (
    <>
      <header className="flex items-baseline justify-between border-b border-border px-5 py-3">
        <h2 className="min-w-0 truncate text-[15px] font-semibold text-text">
          Bill
          {p.branch && <span className="font-normal text-text-muted"> · {p.branch}</span>}
        </h2>
        <span className={`truncate pl-4 text-[13px] ${p.customerName ? 'font-medium text-text' : 'text-text-muted'}`}>
          {p.customerName ?? 'No customer yet'}
        </span>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 py-1.5">
        {items.length === 0 ? (
          <div className="flex h-full min-h-[120px] flex-col items-center justify-center text-center">
            <p className="text-[14px] font-medium text-text">No services yet</p>
            <p className="mt-1 text-[13px] text-text-muted">
              Press <span className="tabular">1–9</span> or pick a service on the left.
            </p>
          </div>
        ) : (
          <ul>
            {items.map((item, i) => {
              const last = i === items.length - 1;
              return (
                <li
                  key={item.uid}
                  data-flash={`item-${item.uid}`}
                  className="flex items-center gap-3 rounded-[10px] px-3 py-2"
                >
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[14px] font-medium text-text">{item.service.name}</span>
                    <span className="mt-0.5 flex items-center gap-1.5">
                      <BarberSelect
                        barbers={barbers}
                        value={item.barberId}
                        onChange={(barberId) => p.onBarberChange(item.uid, barberId)}
                        label={`Barber for ${item.service.name}`}
                        flashId={last ? 'barber-last' : undefined}
                      />
                      {last && barbers.length > 1 && <Keycap>B</Keycap>}
                    </span>
                  </span>
                  <span className="text-[14px] text-text tabular">{money(item.service.price)}</span>
                  <button
                    type="button"
                    onClick={() => p.onRemove(item.uid)}
                    aria-label={`Remove ${item.service.name}`}
                    className="rounded-[6px] p-1 text-text-muted transition-colors hover:bg-surface-muted hover:text-text"
                  >
                    <CloseIcon width={16} height={16} />
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <div className="border-t border-border px-5 pb-4 pt-3">
        <dl className="space-y-1.5 text-[13.5px]">
          <Row label="Subtotal" value={money(preview.subtotal)} />
          <div className="flex items-center justify-between gap-3">
            <dt>
              <label htmlFor="discount" className="flex items-center gap-1.5 text-text-muted">
                Discount <Keycap>D</Keycap>
              </label>
            </dt>
            <dd className="flex items-center gap-3">
              <input
                id="discount"
                ref={p.discountRef}
                value={p.discountText}
                onChange={(e) => p.onDiscountChange(e.target.value)}
                placeholder="₹ or %"
                aria-invalid={p.discountInvalid}
                aria-describedby="discount-help"
                className={`h-7 w-24 rounded-[7px] border bg-surface-muted px-2 text-right text-[13px] text-text outline-none tabular focus:bg-surface ${
                  p.discountInvalid ? 'border-danger' : 'border-border focus:border-border-strong'
                }`}
              />
              <span className="w-24 text-right text-text tabular">
                {preview.discount > 0 ? `−${money(preview.discount)}` : money(0)}
              </span>
            </dd>
          </div>
          <p id="discount-help" className={`text-right text-2xs ${p.discountInvalid ? 'text-danger' : 'sr-only'}`}>
            Enter an amount like 50 or a percent like 10%
          </p>
        </dl>

        <div className="mt-3 flex items-end justify-between border-t border-border pt-3">
          <span className="pb-1.5 text-[13px] font-medium text-text-muted">Total</span>
          <span className="text-[38px] font-semibold leading-none tracking-[-0.03em] text-accent tabular">
            {money(preview.total)}
          </span>
        </div>
        <p className="mt-1.5 text-right text-[12.5px] text-text-muted tabular">
          Includes tax {money(preview.tax)} ({p.taxRate}%)
        </p>

        {p.error && (
          <p role="alert" className="mt-3 rounded-[8px] bg-danger-surface px-3 py-2 text-[13px] text-danger">
            {p.error}
          </p>
        )}

        {p.split.open ? (
          <SplitPayment {...p.split} total={preview.total} saving={p.saving === 'MIXED'} />
        ) : (
        <>
        <div className="mt-4 grid grid-cols-3 gap-2">
          {PAY_BUTTONS.map((b) => (
            <button
              key={b.mode}
              type="button"
              data-flash={`pay-${b.mode}`}
              onClick={() => p.onPay(b.mode)}
              disabled={p.saving !== null}
              className={`flex h-12 items-center justify-center gap-2 rounded-[10px] text-[14px] font-semibold transition-opacity disabled:cursor-wait disabled:opacity-60 ${
                b.primary ? 'bg-accent text-accent-contrast' : 'bg-accent-soft text-accent-soft-text hover:brightness-[0.97]'
              }`}
            >
              <Keycap tone={b.primary ? 'onAccent' : 'default'} className={b.primary ? 'bg-transparent' : ''}>
                {b.key}
              </Keycap>
              {p.saving === b.mode ? 'Saving…' : b.label}
            </button>
          ))}
        </div>
        <button
          type="button"
          data-flash="pay-split"
          onClick={p.split.onOpen}
          disabled={p.saving !== null}
          className="mt-2 flex h-9 w-full items-center justify-center gap-2 rounded-[10px] text-[13px] font-medium text-text-muted transition-colors hover:bg-surface-muted hover:text-text disabled:opacity-60"
        >
          Split payment: part cash, part UPI or card
          <Keycap>S</Keycap>
        </button>
        </>
        )}
      </div>
    </>
  );
}

function SavedBill({ saved, onPrint, onWhatsApp, onNewBill }: Props & { saved: Bill }) {
  return (
    <>
      <header className="flex items-center gap-3 border-b border-border px-5 py-3">
        <span className="flex h-7 w-7 items-center justify-center rounded-full bg-success-surface text-success">
          <CheckIcon width={16} height={16} />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-[15px] font-semibold text-text">
            {saved.paymentMode === 'MIXED'
              ? `Paid ${paidByText(saved.payments, money)}`
              : `Paid by ${modeLabel[saved.paymentMode]}`}
          </h2>
          <p className="text-[12.5px] text-text-muted tabular">
            {saved.billNo} · {dateTimeIST(saved.createdAt)}
          </p>
        </div>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-2">
        <p className="py-1.5 text-[13px] font-medium text-text">{saved.customerName}</p>
        <ul>
          {saved.items.map((i) => (
            <li key={i.id} className="flex items-baseline justify-between gap-3 border-t border-border py-2">
              <span className="min-w-0">
                <span className="block truncate text-[14px] text-text">{i.serviceName}</span>
                <span className="block text-[12.5px] text-text-muted">{i.barberName}</span>
              </span>
              <span className="text-[14px] text-text tabular">{money(i.price)}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="border-t border-border px-5 pb-4 pt-3">
        <dl className="space-y-1.5 text-[13.5px]">
          <Row label="Subtotal" value={money(saved.subtotal)} />
          {saved.discount > 0 && <Row label="Discount" value={`−${money(saved.discount)}`} />}
        </dl>
        <div className="mt-3 flex items-end justify-between border-t border-border pt-3">
          <span className="pb-1.5 text-[13px] font-medium text-text-muted">Total paid</span>
          <span className="text-[38px] font-semibold leading-none tracking-[-0.03em] text-accent tabular">
            {money(saved.total)}
          </span>
        </div>
        <p className="mt-1.5 text-right text-[12.5px] text-text-muted tabular">
          Includes tax {money(saved.taxAmount)} ({saved.taxRate}%)
        </p>

        <div className="mt-4 grid grid-cols-3 gap-2">
          <ActionButton onClick={onPrint} flashId="act-print" icon={<PrintIcon />} label="Print" keys={['Ctrl', 'P']} primary />
          <ActionButton onClick={onWhatsApp} flashId="act-w" icon={<ChatIcon />} label="WhatsApp" keys={['W']} />
          <ActionButton onClick={onNewBill} flashId="act-esc" label="New bill" keys={['Esc']} />
        </div>
      </div>
    </>
  );
}

function ActionButton({
  onClick,
  flashId,
  icon,
  label,
  keys,
  primary = false,
}: {
  onClick: () => void;
  flashId: string;
  icon?: JSX.Element;
  label: string;
  keys: string[];
  primary?: boolean;
}) {
  return (
    <button
      type="button"
      data-flash={flashId}
      onClick={onClick}
      className={`flex h-12 items-center justify-center gap-2 rounded-[10px] text-[14px] font-semibold ${
        primary ? 'bg-accent text-accent-contrast' : 'bg-accent-soft text-accent-soft-text hover:brightness-[0.97]'
      }`}
    >
      {icon}
      {label}
      <span className="flex gap-0.5">
        {keys.map((k) => (
          <Keycap key={k} tone={primary ? 'onAccent' : 'default'} className={primary ? 'bg-transparent' : ''}>
            {k}
          </Keycap>
        ))}
      </span>
    </button>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-text-muted">{label}</dt>
      <dd className="text-text tabular">{value}</dd>
    </div>
  );
}
