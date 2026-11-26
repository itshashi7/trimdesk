import { useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { CalendarIcon, ChevronLeftIcon, ChevronRightIcon, DownloadIcon } from '../components/Icons';
import { downloadBillsCsv } from '../lib/csv';
import { DatePicker } from '../components/DatePicker';
import { MenuSelect } from '../components/MenuSelect';
import { api, errorMessage } from '../lib/api';
import { useAuth } from '../lib/auth';
import { dateIST, longDateLabel, money, moneyWhole, shiftDate, todayIST } from '../lib/format';
import type { OutletDayReport, PaymentMode } from '../lib/types';

const MODE_LABEL: Record<PaymentMode, string> = { UPI: 'UPI', CASH: 'Cash', CARD: 'Card' };
/** Categorical series colours from theme.css; each sits beside its label and amount, never colour alone. */
const MODE_TONE: Record<PaymentMode, string> = { UPI: 'bg-series-1', CASH: 'bg-series-2', CARD: 'bg-series-3' };

export function OutletReportPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const today = todayIST();

  const outletsQuery = useQuery({ queryKey: ['outlets'], queryFn: api.outlets });
  const outlets = outletsQuery.data ?? [];
  const outletId = Number(params.get('outlet')) || user?.outletId || outlets[0]?.id || null;
  const date = params.get('date') ?? today;

  const reportQuery = useQuery({
    queryKey: ['outlet-day', outletId, date],
    queryFn: () => api.outletDay(outletId as number, date),
    enabled: outletId !== null,
  });

  const update = (next: { outlet?: number; date?: string }) => {
    const p = new URLSearchParams(params);
    if (next.outlet !== undefined) p.set('outlet', String(next.outlet));
    if (next.date !== undefined) p.set('date', next.date);
    setParams(p, { replace: true });
  };

  const report = reportQuery.data;
  const outlet = outlets.find((o) => o.id === outletId);

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  /** Every bill of the shown outlet and day as a CSV (one row per bill, with the cash / UPI / card split). */
  async function exportBills() {
    if (!outlet) return;
    setExporting(true);
    setExportError(null);
    try {
      downloadBillsCsv(outlet.code, date, await api.outletDayBills(outlet.id, date));
    } catch (e) {
      setExportError(errorMessage(e, 'Could not export the bills.'));
    } finally {
      setExporting(false);
    }
  }

  return (
    <main className="mx-auto w-full max-w-[1120px] px-4 py-5 sm:px-6 sm:py-7">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-[22px] font-semibold tracking-[-0.02em] text-text">Outlet day report</h1>
          <p className="mt-0.5 text-[13.5px] text-text-muted">
            {outlet ? `${outlet.name}${outlet.area ? ` · ${outlet.area}` : ''} · ${outlet.franchiseName}` : ' '}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {outlets.length > 1 && (
            <MenuSelect
              variant="field"
              label="Outlet"
              options={outlets.map((o) => ({ value: o.id, label: o.name, detail: o.code }))}
              value={outletId}
              onChange={(id) => update({ outlet: id })}
              className="w-full sm:w-[220px]"
            />
          )}
          <div className="flex items-center gap-1 rounded-[10px] border border-border bg-surface p-1">
            <button
              type="button"
              onClick={() => update({ date: shiftDate(date, -1) })}
              aria-label="Previous day"
              className="flex h-8 w-8 items-center justify-center rounded-[7px] text-text-muted hover:bg-surface-muted hover:text-text"
            >
              <ChevronLeftIcon width={16} height={16} />
            </button>
            <DatePicker
              label="Report date"
              value={date}
              max={today}
              today={today}
              onChange={(d) => update({ date: d })}
              className="flex h-8 items-center gap-1.5 rounded-[7px] px-2 text-[13.5px] font-medium text-text hover:bg-surface-muted"
            >
              <CalendarIcon width={16} height={16} className="text-text-muted" />
              <span className="tabular">{dateIST(`${date}T12:00:00+05:30`)}</span>
            </DatePicker>
            <button
              type="button"
              onClick={() => update({ date: shiftDate(date, 1) })}
              disabled={date >= today}
              aria-label="Next day"
              className="flex h-8 w-8 items-center justify-center rounded-[7px] text-text-muted hover:bg-surface-muted hover:text-text disabled:opacity-30 disabled:hover:bg-transparent"
            >
              <ChevronRightIcon width={16} height={16} />
            </button>
            {date !== today && (
              <button
                type="button"
                onClick={() => update({ date: today })}
                className="h-8 rounded-[7px] px-2.5 text-[13px] font-medium text-text hover:bg-surface-muted"
              >
                Today
              </button>
            )}
          </div>
          <button
            type="button"
            onClick={() => void exportBills()}
            disabled={!outlet || !report || report.bills === 0 || exporting}
            className="flex h-10 items-center gap-2 rounded-[10px] px-3.5 text-[13.5px] font-medium transition bg-accent-soft text-accent-soft-text hover:brightness-[0.97] disabled:opacity-50"
          >
            <DownloadIcon width={16} height={16} />
            {exporting ? 'Exporting…' : 'Export bills'}
          </button>
        </div>
      </div>

      {exportError && (
        <div className="mt-4">
          <Notice tone="error">{exportError}</Notice>
        </div>
      )}
      <p className="mt-5 text-[13px] font-medium text-text-muted">{longDateLabel(date)}</p>

      <div className="mt-2">
        {reportQuery.isError ? (
          <Notice tone="error">
            {errorMessage(reportQuery.error, 'The report could not load.')}
          </Notice>
        ) : !report ? (
          <Skeleton />
        ) : report.bills === 0 ? (
          <div className="rounded-card border border-border bg-surface px-6 py-12 text-center">
            <p className="text-[15px] font-medium text-text">No bills on this day</p>
            <p className="mt-1 text-[13.5px] text-text-muted">
              {date === today ? 'Bills appear here as the counter saves them.' : 'The outlet recorded no sales.'}
            </p>
            <button
              type="button"
              onClick={() => update({ date: shiftDate(date, -1) })}
              className="mt-4 h-9 rounded-[10px] px-3.5 text-[13.5px] font-medium bg-accent-soft text-accent-soft-text hover:brightness-[0.97]"
            >
              Show the day before
            </button>
          </div>
        ) : (
          <ReportBody report={report} />
        )}
      </div>
    </main>
  );
}

function ReportBody({ report }: { report: OutletDayReport }) {
  const totalBarber = report.barbers.reduce(
    (t, b) => ({ services: t.services + b.services, revenue: t.revenue + b.revenue, commission: t.commission + b.commission }),
    { services: 0, revenue: 0, commission: 0 },
  );

  return (
    <div className="space-y-4">
      <section aria-label="Key figures" className="grid grid-cols-3 gap-2.5 sm:gap-4">
        <Kpi label="Revenue" value={moneyWhole(report.revenue)} note={`Tax included ${moneyWhole(report.taxIncluded)}`} />
        <Kpi label="Bills" value={String(report.bills)} note={report.discounts > 0 ? `Discounts ${moneyWhole(report.discounts)}` : 'No discounts'} />
        <Kpi label="Average bill" value={moneyWhole(report.averageBill)} note="Per customer visit" />
      </section>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.55fr)]">
        <section aria-labelledby="payments-heading" className="rounded-card border border-border bg-surface p-5">
          <h2 id="payments-heading" className="text-[14px] font-semibold text-text">Payments</h2>
          <div className="mt-4 flex h-2.5 gap-[2px] overflow-hidden rounded-full" aria-hidden>
            {report.payments.map((p) =>
              p.amount > 0 ? (
                <span key={p.mode} className={`basis-0 rounded-[2px] ${MODE_TONE[p.mode]}`} style={{ flexGrow: p.amount }} />
              ) : null,
            )}
          </div>
          <ul className="mt-4 divide-y divide-border">
            {report.payments.map((p) => (
              <li key={p.mode} className="flex items-center gap-3 py-2.5 text-[13.5px]">
                <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${MODE_TONE[p.mode]}`} aria-hidden />
                <span className="flex-1 font-medium text-text">{MODE_LABEL[p.mode]}</span>
                <span className="text-text-muted tabular">
                  {p.bills} {p.bills === 1 ? 'bill' : 'bills'}
                </span>
                <span className="w-12 text-right text-text-muted tabular">
                  {report.revenue > 0 ? Math.round((p.amount / report.revenue) * 100) : 0}%
                </span>
                <span className="w-24 text-right text-text tabular">{money(p.amount)}</span>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="barbers-heading" className="rounded-card border border-border bg-surface p-5">
          <div className="flex items-baseline justify-between gap-3">
            <h2 id="barbers-heading" className="text-[14px] font-semibold text-text">Barbers</h2>
            <span className="hidden text-2xs text-text-muted sm:inline">Service value before bill discounts</span>
          </div>
          <div className="-mx-5 mt-3 overflow-x-auto px-5">
            <table className="w-full text-[12.5px] sm:text-[13.5px]">
              <thead>
                <tr className="text-left text-2xs font-medium text-text-muted">
                  <th scope="col" className="pb-2 font-medium">Barber</th>
                  <th scope="col" className="pb-2 text-right font-medium">Services</th>
                  <th scope="col" className="pb-2 text-right font-medium">Revenue</th>
                  <th scope="col" className="pb-2 text-right font-medium">Commission</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border border-t border-border">
                {report.barbers.map((b) => (
                  <tr key={b.barberId}>
                    <td className="py-2.5 font-medium text-text">{b.name}</td>
                    <td className="py-2.5 text-right text-text-muted tabular">{b.services}</td>
                    <td className="py-2.5 text-right text-text tabular">{money(b.revenue)}</td>
                    <td className="py-2.5 text-right text-text tabular">{money(b.commission)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t border-border-strong font-semibold">
                  <td className="pt-2.5 text-text">Total</td>
                  <td className="pt-2.5 text-right text-text tabular">{totalBarber.services}</td>
                  <td className="pt-2.5 text-right text-text tabular">{money(totalBarber.revenue)}</td>
                  <td className="pt-2.5 text-right text-text tabular">{money(totalBarber.commission)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </section>
      </div>
    </div>
  );
}

function Kpi({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <div className="rounded-card border border-border bg-surface p-3 sm:p-5">
      <p className="text-2xs font-medium text-text-muted sm:text-[12.5px]">{label}</p>
      <p className="mt-1.5 text-[18px] font-semibold leading-none tracking-[-0.02em] text-text tabular sm:mt-2 sm:text-[28px]">
        {value}
      </p>
      <p className="mt-2 hidden text-[12px] text-text-muted sm:block">{note}</p>
    </div>
  );
}

function Notice({ tone, children }: { tone: 'error'; children: ReactNode }) {
  return (
    <p role="alert" className={`rounded-card px-4 py-3 text-[13.5px] ${tone === 'error' ? 'bg-danger-surface text-danger' : ''}`}>
      {children}
    </p>
  );
}

function Skeleton() {
  return (
    <div className="space-y-4" aria-busy="true" aria-label="Loading report">
      <div className="grid grid-cols-3 gap-2.5 sm:gap-4">
        {[0, 1, 2].map((i) => (
          <div key={i} className="h-[76px] animate-pulse rounded-card bg-surface sm:h-[118px]" />
        ))}
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.55fr)]">
        <div className="h-[220px] animate-pulse rounded-card bg-surface" />
        <div className="h-[220px] animate-pulse rounded-card bg-surface" />
      </div>
    </div>
  );
}
