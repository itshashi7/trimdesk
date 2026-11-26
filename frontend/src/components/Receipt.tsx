import type { CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import { dateTimeIST, money, phoneDisplay } from '../lib/format';
import type { Bill } from '../lib/types';

const modeLabel = { CASH: 'Cash', UPI: 'UPI', CARD: 'Card' } as const;

/**
 * 80mm thermal receipt (72mm printable). Rendered outside #root so print CSS can show it alone.
 * Plain black on white with real point sizes; thermal printers render nothing else well.
 */
export function Receipt({ bill }: { bill: Bill }) {
  return createPortal(
    <div className="receipt-print" style={receiptStyle}>
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: '13pt', fontWeight: 700 }}>{bill.outletName}</div>
        {bill.outletArea && <div style={small}>{bill.outletArea}</div>}
      </div>

      <div style={{ ...rule, marginTop: '3mm' }} />
      <Line left="Bill" right={bill.billNo} />
      <Line left="Date" right={dateTimeIST(bill.createdAt)} />
      <Line left="Customer" right={bill.customerName} />
      <Line left="Phone" right={phoneDisplay(bill.customerPhone)} />
      <div style={rule} />

      {bill.items.map((i) => (
        <div key={i.id} style={{ marginBottom: '1.5mm' }}>
          <Line left={i.serviceName} right={money(i.price)} bold />
          <div style={small}>by {i.barberName}</div>
        </div>
      ))}

      <div style={rule} />
      <Line left="Subtotal" right={money(bill.subtotal)} />
      {bill.discount > 0 && <Line left="Discount" right={`-${money(bill.discount)}`} />}
      <div style={{ ...rule, borderTopStyle: 'solid' }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13pt', fontWeight: 700 }}>
        <span>Total</span>
        <span>{money(bill.total)}</span>
      </div>
      {bill.paymentMode === 'MIXED' ? (
        <>
          <Line left="Paid by" right="Split" />
          {bill.payments.map((p) => (
            <Line key={p.mode} left={`· ${modeLabel[p.mode]}`} right={money(p.amount)} />
          ))}
        </>
      ) : (
        <Line left="Paid by" right={modeLabel[bill.paymentMode]} />
      )}
      <div style={{ ...small, marginTop: '1.5mm' }}>
        <Line left="Taxable value" right={money(bill.taxableAmount)} />
        <Line left={`Tax included (${bill.taxRate}%)`} right={money(bill.taxAmount)} />
      </div>
      <div style={rule} />

      <div style={{ textAlign: 'center', marginTop: '2mm', fontWeight: 600 }}>Thank you, visit again</div>
      <div style={{ ...small, textAlign: 'center' }}>Served by {bill.cashierName}</div>
    </div>,
    document.body,
  );
}

function Line({ left, right, bold = false }: { left: string; right: string; bold?: boolean }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: '2mm', fontWeight: bold ? 600 : 400 }}>
      <span>{left}</span>
      <span style={{ textAlign: 'right' }}>{right}</span>
    </div>
  );
}

// Print-only styles are inline: the receipt must not depend on the screen theme.
const receiptStyle: CSSProperties = {
  width: '72mm',
  padding: '4mm',
  color: 'black',
  background: 'white',
  fontFamily: 'var(--font-sans)',
  fontSize: '9.5pt',
  lineHeight: 1.35,
  fontVariantNumeric: 'tabular-nums',
};
const small: CSSProperties = { fontSize: '8pt' };
const rule: CSSProperties = { borderTop: '1px dashed black', margin: '2mm 0' };
