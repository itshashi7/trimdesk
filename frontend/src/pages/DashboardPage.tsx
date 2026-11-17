import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ServiceIcon } from '../components/billing/ServiceIcon';
import { ChevronDownIcon } from '../components/Icons';
import { ApiError, api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { dateIST, longDateLabel, money, moneyCompact, moneyWhole, shiftDate, todayIST } from '../lib/format';
import type { HqSummary } from '../lib/types';

const RANGES = [7, 30, 90] as const;
type Range = (typeof RANGES)[number];

/** Last N full days, ending yesterday; today is shown live in its own card. */
function rangeFor(days: Range) {
  const to = shiftDate(todayIST(), -1);
  return { from: shiftDate(to, -(days - 1)), to };
}

export function DashboardPage() {
  const { user } = useAuth();
  const [days, setDays] = useState<Range>(90);
  const { from, to } = rangeFor(days);

  const summary = useQuery({
    queryKey: ['hq-summary', from, to],
    queryFn: () => api.hqSummary(from, to),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000, // keeps "Today so far" live
  });
  const data = summary.data;
  const scope = user?.role === 'FRANCHISE_OWNER' ? (user.franchiseName ?? 'Your outlets') : 'All outlets';

  return (
    <main className="mx-auto w-full max-w-[1180px] px-4 py-5 sm:px-6 sm:py-7">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-[22px] font-semibold tracking-[-0.02em] text-text">Dashboard</h1>
          <p className="mt-0.5 text-[13.5px] text-text-muted">
            {scope}
            {data && ` · ${dateIST(`${data.from}T12:00:00+05:30`)} – ${dateIST(`${data.to}T12:00:00+05:30`)}`}
          </p>
        </div>
        <div role="radiogroup" aria-label="Date range" className="inline-flex self-start rounded-[10px] bg-surface p-1 ring-1 ring-border sm:self-auto">
          {RANGES.map((r) => (
            <button
              key={r}
              type="button"
              role="radio"
              aria-checked={days === r}
              onClick={() => setDays(r)}
              className={`h-8 rounded-[7px] px-3.5 text-[13px] font-medium transition-colors ${
                days === r ? 'bg-accent text-accent-contrast' : 'text-text-muted hover:text-text'
              }`}
            >
              {r} days
            </button>
          ))}
        </div>
      </div>

      <div className="mt-5">
        {summary.isError && !data ? (
          <p role="alert" className="rounded-card bg-danger-surface px-4 py-3 text-[13.5px] text-danger">
            {summary.error instanceof ApiError ? summary.error.message : 'The dashboard could not load.'}
          </p>
        ) : !data ? (
          <Skeleton />
        ) : (
          <DashboardBody data={data} days={days} />
        )}
      </div>
    </main>
  );
}

function DashboardBody({ data, days }: { data: HqSummary; days: Range }) {
  const { totals, today } = data;
  return (
    <div className="space-y-4">
      <section aria-label="Key figures" className="grid grid-cols-2 gap-2.5 sm:gap-4 lg:grid-cols-4">
        <Kpi label="Revenue" value={moneyWhole(totals.revenue)} note={`Last ${days} days, tax included`} />
        <Kpi label="Bills" value={totals.bills.toLocaleString('en-IN')} note={`Average ${moneyWhole(totals.averageBill)}`} />
        <Kpi
          label="Active outlets"
          value={`${totals.activeOutlets} / ${totals.totalOutlets}`}
          note={`${totals.customers.toLocaleString('en-IN')} customers served`}
        />
        <div className="rounded-card border border-border bg-surface p-3.5 sm:p-5">
          <p className="flex items-center gap-1.5 text-2xs font-medium text-text-muted sm:text-[12.5px]">
            <span className="relative flex h-2 w-2" aria-hidden>
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-50 motion-reduce:animate-none" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-success" />
            </span>
            Today so far · Live
          </p>
          <p className="mt-1.5 text-[20px] font-semibold leading-none tracking-[-0.02em] text-text tabular sm:mt-2 sm:text-[28px]">
            {moneyWhole(today.revenue)}
          </p>
          <p className="mt-2 text-[12px] text-text-muted">
            {today.bills} {today.bills === 1 ? 'bill' : 'bills'} · {today.outletsBilling} {today.outletsBilling === 1 ? 'outlet' : 'outlets'} billing
          </p>
        </div>
      </section>

      <TrendCard data={data} />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <OutletRanking data={data} />
        <TopServices data={data} />
      </div>
    </div>
  );
}

function Kpi({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <div className="rounded-card border border-border bg-surface p-3.5 sm:p-5">
      <p className="text-2xs font-medium text-text-muted sm:text-[12.5px]">{label}</p>
      <p className="mt-1.5 text-[20px] font-semibold leading-none tracking-[-0.02em] text-text tabular sm:mt-2 sm:text-[28px]">
        {value}
      </p>
      <p className="mt-2 text-[12px] text-text-muted">{note}</p>
    </div>
  );
}

/** Round axis steps (1, 2, 2.5 or 5 × 10^n) so labels read ₹50K, ₹1L, ₹1.5L… */
function niceTicks(max: number, intervals = 4): number[] {
  if (max <= 0) return [0];
  const raw = max / intervals;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? raw;
  const top = Math.ceil(max / step) * step;
  return Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step);
}

function TrendCard({ data }: { data: HqSummary }) {
  const points = data.trend;
  const best = points.reduce((a, b) => (b.revenue > a.revenue ? b : a), points[0]);
  const ticks = niceTicks(best?.revenue ?? 0);
  return (
    <section aria-labelledby="trend-heading" className="rounded-card border border-border bg-surface p-4 sm:p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="trend-heading" className="text-[14px] font-semibold text-text">Daily revenue</h2>
        {best && best.revenue > 0 && (
          <p className="text-[12.5px] text-text-muted">
            Best day {dateIST(`${best.date}T12:00:00+05:30`)} · <span className="text-text tabular">{moneyWhole(best.revenue)}</span>
          </p>
        )}
      </div>
      <div className="mt-3 h-[220px] sm:h-[260px]">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} title="Daily revenue">
            <CartesianGrid vertical={false} stroke="var(--border)" />
            <XAxis
              dataKey="date"
              tickLine={false}
              axisLine={false}
              minTickGap={28}
              tick={{ fill: 'var(--text-muted)', fontSize: 11.5 }}
              tickFormatter={(d: string) => dateIST(`${d}T12:00:00+05:30`).replace(/ \d{4}$/, '')}
            />
            <YAxis
              width={56}
              ticks={ticks}
              domain={[0, ticks[ticks.length - 1]]}
              tickLine={false}
              axisLine={false}
              tick={{ fill: 'var(--text-muted)', fontSize: 11.5 }}
              tickFormatter={(v: number) => moneyCompact(v)}
            />
            <Tooltip cursor={{ stroke: 'var(--border-strong)', strokeWidth: 1 }} content={<TrendTooltip />} />
            <Line
              type="monotone"
              dataKey="revenue"
              stroke="var(--accent)"
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4, stroke: 'var(--surface)', strokeWidth: 2, fill: 'var(--accent)' }}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}

interface TooltipProps {
  active?: boolean;
  payload?: { payload: { date: string; revenue: number; bills: number } }[];
}

function TrendTooltip({ active, payload }: TooltipProps) {
  const point = active && payload?.[0]?.payload;
  if (!point) return null;
  return (
    <div className="rounded-[10px] bg-surface px-3 py-2 text-[12.5px] shadow-pop">
      <p className="font-medium text-text">{longDateLabel(point.date)}</p>
      <p className="mt-0.5 text-text tabular">{money(point.revenue)}</p>
      <p className="text-text-muted tabular">{point.bills} bills</p>
    </div>
  );
}

type SortKey = 'revenue' | 'bills' | 'averageBill' | 'name';

function OutletRanking({ data }: { data: HqSummary }) {
  const [sort, setSort] = useState<{ key: SortKey; desc: boolean }>({ key: 'revenue', desc: true });
  const rows = useMemo(() => {
    const sorted = [...data.outlets].sort((a, b) =>
      sort.key === 'name' ? a.name.localeCompare(b.name) : (a[sort.key] as number) - (b[sort.key] as number),
    );
    return sort.desc ? sorted.reverse() : sorted;
  }, [data.outlets, sort]);
  const maxRevenue = Math.max(...data.outlets.map((o) => o.revenue), 1);
  const totalRevenue = Math.max(data.totals.revenue, 1);
  const yesterday = shiftDate(todayIST(), -1);

  const header = (key: SortKey, label: string, className = '') => {
    const active = sort.key === key;
    return (
      <th scope="col" aria-sort={active ? (sort.desc ? 'descending' : 'ascending') : 'none'} className={`pb-2 font-medium ${className}`}>
        <button
          type="button"
          onClick={() => setSort({ key, desc: active ? !sort.desc : key !== 'name' })}
          className={`inline-flex items-center gap-1 rounded-[6px] px-1 py-0.5 -mx-1 hover:text-text ${active ? 'text-text' : ''}`}
        >
          {label}
          <ChevronDownIcon
            width={12}
            height={12}
            className={`transition-transform ${sort.desc ? '' : 'rotate-180'} ${active ? '' : 'opacity-0'}`}
          />
        </button>
      </th>
    );
  };

  return (
    <section aria-labelledby="ranking-heading" className="rounded-card border border-border bg-surface p-4 sm:p-5">
      <h2 id="ranking-heading" className="text-[14px] font-semibold text-text">Outlet ranking</h2>
      <table className="mt-3 w-full text-[12.5px] sm:text-[13.5px]">
        <thead>
          <tr className="text-left text-2xs text-text-muted">
            <th scope="col" className="w-7 pb-2 font-medium">#</th>
            {header('name', 'Outlet')}
            {header('revenue', 'Revenue', 'text-right')}
            {header('bills', 'Bills', 'hidden text-right sm:table-cell')}
            {header('averageBill', 'Avg bill', 'hidden text-right md:table-cell')}
            <th scope="col" className="hidden w-[150px] pb-2 pl-4 font-medium lg:table-cell">Share</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border border-t border-border">
          {rows.map((o) => {
            const rank = data.outlets.findIndex((x) => x.outletId === o.outletId) + 1;
            return (
              <tr key={o.outletId} className="group">
                <td className="py-2.5 text-text-muted tabular">{rank}</td>
                <td className="py-2.5 pr-3">
                  <Link
                    to={`/reports/outlet?outlet=${o.outletId}&date=${yesterday}`}
                    className="font-medium text-text underline-offset-2 hover:underline"
                  >
                    {o.name}
                  </Link>
                  <span className="block text-2xs text-text-muted">{o.franchiseName}</span>
                </td>
                <td className="py-2.5 text-right text-text tabular">{moneyWhole(o.revenue)}</td>
                <td className="hidden py-2.5 text-right text-text-muted tabular sm:table-cell">{o.bills.toLocaleString('en-IN')}</td>
                <td className="hidden py-2.5 text-right text-text-muted tabular md:table-cell">{moneyWhole(o.averageBill)}</td>
                <td className="hidden py-2.5 pl-4 lg:table-cell">
                  <span className="flex items-center gap-2">
                    <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-muted" aria-hidden>
                      <span className="block h-full rounded-full bg-accent" style={{ width: `${(o.revenue / maxRevenue) * 100}%` }} />
                    </span>
                    <span className="w-9 text-right text-2xs text-text-muted tabular">{Math.round((o.revenue / totalRevenue) * 100)}%</span>
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}

function TopServices({ data }: { data: HqSummary }) {
  const max = Math.max(...data.topServices.map((s) => s.revenue), 1);
  return (
    <section aria-labelledby="services-heading" className="rounded-card border border-border bg-surface p-4 sm:p-5">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="services-heading" className="text-[14px] font-semibold text-text">Top services</h2>
        <span className="text-2xs text-text-muted">By revenue</span>
      </div>
      {data.topServices.length === 0 ? (
        <p className="mt-4 text-[13.5px] text-text-muted">No services sold in this range.</p>
      ) : (
        <ul className="mt-3 space-y-3">
          {data.topServices.map((s) => (
            <li key={s.serviceId}>
              <div className="flex items-baseline gap-2 text-[13px]">
                <ServiceIcon
                  service={{ id: s.serviceId, code: null, name: s.name, category: s.category, price: 0, durationMin: 0 }}
                  width={16}
                  height={16}
                  className="shrink-0 self-center text-text-muted"
                />
                <span className="min-w-0 flex-1 truncate font-medium text-text">{s.name}</span>
                <span className="text-text-muted tabular">{s.count.toLocaleString('en-IN')}×</span>
                <span className="w-[76px] text-right text-text tabular">{moneyCompact(s.revenue)}</span>
              </div>
              <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-surface-muted" aria-hidden>
                <div className="h-full rounded-full bg-accent" style={{ width: `${(s.revenue / max) * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function Skeleton() {
  return (
    <div className="space-y-4" aria-busy="true" aria-label="Loading dashboard">
      <div className="grid grid-cols-2 gap-2.5 sm:gap-4 lg:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-[92px] animate-pulse rounded-card bg-surface sm:h-[118px]" />
        ))}
      </div>
      <div className="h-[310px] animate-pulse rounded-card bg-surface" />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <div className="h-[380px] animate-pulse rounded-card bg-surface" />
        <div className="h-[380px] animate-pulse rounded-card bg-surface" />
      </div>
    </div>
  );
}
