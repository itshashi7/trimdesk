import { Fragment } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { DownloadIcon } from '../components/Icons';
import { MenuSelect } from '../components/MenuSelect';
import { ApiError, api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { money, todayIST } from '../lib/format';
import type { RoyaltyReport } from '../lib/types';

const monthLabel = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric', timeZone: 'UTC' });
const labelOf = (ym: string) => monthLabel.format(new Date(`${ym}-01T00:00:00Z`));

/** The current month and the 11 before it, newest first, as YYYY-MM. */
function recentMonths(): string[] {
  const [y, m] = todayIST().split('-').map(Number);
  return Array.from({ length: 12 }, (_, i) => {
    const d = new Date(Date.UTC(y, m - 1 - i, 1));
    return d.toISOString().slice(0, 7);
  });
}

export function RoyaltyPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const months = recentMonths();
  // Royalty is settled for a completed month, so default to last month.
  const month = params.get('month') ?? months[1];

  const report = useQuery({
    queryKey: ['royalty', month],
    queryFn: () => api.royalty(month),
    placeholderData: keepPreviousData,
  });
  const data = report.data;

  return (
    <main className="mx-auto w-full max-w-[1080px] px-4 py-5 sm:px-6 sm:py-7">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-[22px] font-semibold tracking-[-0.02em] text-text">Royalty</h1>
          <p className="mt-0.5 text-[13.5px] text-text-muted">
            {user?.role === 'FRANCHISE_OWNER' ? `Royalty due from ${user.franchiseName ?? 'your franchise'}` : 'Royalty due from every franchise'}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <MenuSelect
            variant="field"
            label="Month"
            options={months.map((m, i) => ({ value: i, label: labelOf(m), detail: i === 0 ? 'In progress' : undefined }))}
            value={months.indexOf(month)}
            onChange={(i) => setParams({ month: months[i] }, { replace: true })}
            className="w-full sm:w-[220px]"
          />
          <button
            type="button"
            onClick={() => data && downloadCsv(data)}
            disabled={!data}
            className="flex h-10 items-center gap-2 rounded-[10px] border border-border-strong bg-surface px-3.5 text-[13.5px] font-medium text-text transition-colors hover:bg-surface-muted disabled:opacity-50"
          >
            <DownloadIcon width={16} height={16} />
            Export CSV
          </button>
        </div>
      </div>

      <div className="mt-5">
        {report.isError && !data ? (
          <p role="alert" className="rounded-card bg-danger-surface px-4 py-3 text-[13.5px] text-danger">
            {report.error instanceof ApiError ? report.error.message : 'The royalty report could not load.'}
          </p>
        ) : !data ? (
          <div className="space-y-4" aria-busy="true" aria-label="Loading royalty report">
            <div className="h-[104px] animate-pulse rounded-card bg-surface" />
            <div className="h-[420px] animate-pulse rounded-card bg-surface" />
          </div>
        ) : (
          <RoyaltyBody data={data} inProgress={month === months[0]} />
        )}
      </div>
    </main>
  );
}

function RoyaltyBody({ data, inProgress }: { data: RoyaltyReport; inProgress: boolean }) {
  return (
    <div className="space-y-4">
      <section aria-label="Totals" className="grid grid-cols-1 gap-2.5 rounded-card border border-border bg-surface p-4 sm:grid-cols-3 sm:gap-0 sm:p-0">
        <Total label={`Royalty due · ${labelOf(data.month)}`} value={money(data.totalRoyalty)} strong />
        <Total label="Revenue (tax included)" value={money(data.totalRevenue)} />
        <Total label="Taxable revenue" value={money(data.totalTaxable)} />
      </section>

      {inProgress && (
        <p className="rounded-[10px] bg-surface-muted px-3.5 py-2.5 text-[12.5px] text-text-muted">
          This month is still in progress. Figures will change until it ends.
        </p>
      )}

      <section aria-labelledby="royalty-table-heading" className="rounded-card border border-border bg-surface p-4 sm:p-5">
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <h2 id="royalty-table-heading" className="text-[14px] font-semibold text-text">By franchise</h2>
          <p className="text-2xs text-text-muted">Royalty = taxable revenue × royalty %, rounded on each franchise's monthly total</p>
        </div>
        <div className="-mx-4 mt-3 overflow-x-auto px-4 sm:-mx-5 sm:px-5">
          <table className="w-full text-[12.5px] sm:min-w-[560px] sm:text-[13.5px]">
            <thead>
              <tr className="text-left text-2xs font-medium text-text-muted">
                <th scope="col" className="pb-2 font-medium">Franchise / outlet</th>
                <th scope="col" className="hidden pb-2 text-right font-medium sm:table-cell">Bills</th>
                <th scope="col" className="hidden pb-2 text-right font-medium sm:table-cell">Revenue</th>
                <th scope="col" className="pb-2 text-right font-medium">Taxable</th>
                <th scope="col" className="pb-2 text-right font-medium">Rate</th>
                <th scope="col" className="pb-2 text-right font-medium">Royalty due</th>
              </tr>
            </thead>
            <tbody>
              {data.franchises.map((f) => (
                <Fragment key={f.franchiseId}>
                  <tr className="border-t border-border-strong">
                    <th scope="row" className="pb-1.5 pt-3 text-left font-semibold text-text">
                      {f.name}
                      <span className="block text-2xs font-normal text-text-muted">
                        {f.ownerName} · {f.outlets.length} {f.outlets.length === 1 ? 'outlet' : 'outlets'}
                      </span>
                    </th>
                    <td className="hidden pb-1.5 pt-3 text-right font-medium text-text tabular sm:table-cell">{f.bills.toLocaleString('en-IN')}</td>
                    <td className="hidden pb-1.5 pt-3 text-right font-medium text-text tabular sm:table-cell">{money(f.revenue)}</td>
                    <td className="pb-1.5 pt-3 text-right font-medium text-text tabular">{money(f.taxable)}</td>
                    <td className="pb-1.5 pt-3 text-right text-text tabular">{f.royaltyPercent}%</td>
                    <td className="pb-1.5 pt-3 text-right font-semibold text-text tabular">{money(f.royaltyDue)}</td>
                  </tr>
                  {f.outlets.map((o) => (
                    <tr key={o.outletId} className="text-text-muted">
                      <td className="py-1 pl-4">
                        {o.name} <span className="text-2xs tabular">{o.code}</span>
                      </td>
                      <td className="hidden py-1 text-right tabular sm:table-cell">{o.bills.toLocaleString('en-IN')}</td>
                      <td className="hidden py-1 text-right tabular sm:table-cell">{money(o.revenue)}</td>
                      <td className="py-1 text-right tabular">{money(o.taxable)}</td>
                      <td />
                      <td />
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t-2 border-text">
                <th scope="row" className="pt-3 text-left font-semibold text-text">Grand total</th>
                <td className="hidden pt-3 text-right font-semibold text-text tabular sm:table-cell">
                  {data.franchises.reduce((n, f) => n + f.bills, 0).toLocaleString('en-IN')}
                </td>
                <td className="hidden pt-3 text-right font-semibold text-text tabular sm:table-cell">{money(data.totalRevenue)}</td>
                <td className="pt-3 text-right font-semibold text-text tabular">{money(data.totalTaxable)}</td>
                <td />
                <td className="pt-3 text-right text-[15px] font-semibold text-text tabular">{money(data.totalRoyalty)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>
    </div>
  );
}

function Total({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="sm:border-l sm:border-border sm:p-5 sm:first:border-l-0">
      <p className="text-2xs font-medium text-text-muted sm:text-[12.5px]">{label}</p>
      <p className={`mt-1.5 font-semibold leading-none tracking-[-0.02em] text-text tabular ${strong ? 'text-[26px] sm:text-[30px]' : 'text-[18px] sm:text-[22px]'}`}>
        {value}
      </p>
    </div>
  );
}

/** One row per outlet, a subtotal row per franchise and a grand total. Plain numbers so Excel can sum them. */
function downloadCsv(data: RoyaltyReport) {
  const n = (v: number) => v.toFixed(2);
  const rows: (string | number)[][] = [
    ['Month', 'Franchise', 'Owner', 'Outlet code', 'Outlet', 'Bills', 'Revenue (tax incl.)', 'Taxable revenue', 'Royalty %', 'Royalty due'],
  ];
  for (const f of data.franchises) {
    for (const o of f.outlets) {
      rows.push([data.month, f.name, f.ownerName, o.code, o.name, o.bills, n(o.revenue), n(o.taxable), n(f.royaltyPercent), '']);
    }
    rows.push([data.month, f.name, f.ownerName, '', 'Franchise total', f.bills, n(f.revenue), n(f.taxable), n(f.royaltyPercent), n(f.royaltyDue)]);
  }
  rows.push([data.month, 'Grand total', '', '', '', data.franchises.reduce((t, f) => t + f.bills, 0), n(data.totalRevenue), n(data.totalTaxable), '', n(data.totalRoyalty)]);

  const csv = rows.map((r) => r.map(csvCell).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob(['﻿', csv], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = `groomdesk-royalty-${data.month}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

function csvCell(value: string | number): string {
  const s = String(value);
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}
