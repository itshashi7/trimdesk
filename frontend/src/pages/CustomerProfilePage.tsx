import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ChevronLeftIcon } from '../components/Icons';
import { ApiError, api, errorMessage } from '../lib/api';
import { useAuth } from '../lib/auth';
import { dateIST, money, moneyWhole, phoneDisplay, timeIST } from '../lib/format';
import type { CustomerProfile } from '../lib/types';

export function CustomerProfilePage() {
  const { id } = useParams();
  const customerId = Number(id);
  const profile = useQuery({
    queryKey: ['customer-profile', customerId],
    queryFn: () => api.customerProfile(customerId),
    enabled: Number.isFinite(customerId),
  });

  return (
    <main className="mx-auto w-full max-w-[860px] px-4 py-5 sm:px-6 sm:py-7">
      <Link
        to="/customers"
        className="-ml-1.5 inline-flex items-center gap-0.5 rounded-[8px] px-1.5 py-1 text-[13px] font-medium text-text-muted hover:bg-surface-muted hover:text-text"
      >
        <ChevronLeftIcon width={16} height={16} />
        Customers
      </Link>

      {profile.isError ? (
        <p role="alert" className="mt-4 rounded-card bg-danger-surface px-4 py-3 text-[13.5px] text-danger">
          {profile.error instanceof ApiError && profile.error.status === 404
            ? 'This customer is not in your records.'
            : errorMessage(profile.error, 'The profile could not load.')}
        </p>
      ) : !profile.data ? (
        <div className="mt-4 space-y-4" aria-busy="true" aria-label="Loading profile">
          <div className="h-[150px] animate-pulse rounded-card bg-surface" />
          <div className="h-[260px] animate-pulse rounded-card bg-surface" />
        </div>
      ) : (
        <Profile data={profile.data} />
      )}
    </main>
  );
}

function Profile({ data }: { data: CustomerProfile }) {
  const { user } = useAuth();
  const { customer, visits } = data;
  const preferred = customer.preferredBarber;
  const owner = user?.role === 'FRANCHISE_OWNER';

  // A franchise owner sees only their outlets; name nothing outside them.
  const outletsQuery = useQuery({ queryKey: ['outlets'], queryFn: api.outlets, enabled: owner });
  const visible = (outletId: number) => !owner || (outletsQuery.data ?? []).some((o) => o.id === outletId);
  const elsewhere = 'An outlet outside your franchise';

  return (
    <div className="mt-3 space-y-4">
      <section aria-label="Customer details" className="rounded-card border border-border bg-surface p-5 sm:p-6">
        <h1 className="text-[24px] font-semibold tracking-[-0.02em] text-text">{customer.name}</h1>
        <p className="mt-0.5 text-[14px] text-text-muted tabular">{phoneDisplay(customer.phone)}</p>

        <dl className="mt-4 grid gap-x-6 gap-y-3 text-[13.5px] sm:grid-cols-2">
          <Detail
            label="Preferred barber"
            value={!preferred ? 'None yet' : visible(preferred.outletId) ? `${preferred.name} · ${preferred.outletCode}` : elsewhere}
          />
          <Detail
            label="First visit"
            value={`${visible(customer.firstVisitOutlet.id) ? customer.firstVisitOutlet.name : elsewhere} · ${dateIST(customer.createdAt)}`}
          />
          <div className="sm:col-span-2">
            <Detail label="Notes" value={customer.notes ?? 'No notes'} muted={!customer.notes} />
          </div>
        </dl>

        <dl className="mt-5 grid grid-cols-2 gap-px overflow-hidden rounded-[12px] border border-border bg-border sm:grid-cols-4">
          <Stat label="Visits" value={String(data.totalVisits)} />
          <Stat label="Total spend" value={moneyWhole(data.totalSpend)} />
          <Stat label="Outlets visited" value={String(data.outletsVisited)} />
          <Stat label="Last visit" value={data.lastVisit ? dateIST(data.lastVisit) : '—'} />
        </dl>
      </section>

      <section aria-labelledby="visits-heading" className="rounded-card border border-border bg-surface p-5 sm:p-6">
        <div className="flex items-baseline justify-between gap-3">
          <h2 id="visits-heading" className="text-[15px] font-semibold text-text">Visit history</h2>
          <span className="text-2xs text-text-muted">{owner ? 'Your outlets' : 'All outlets'}, newest first</span>
        </div>

        {data.hiddenVisits > 0 && user?.role === 'FRANCHISE_OWNER' && (
          <p className="mt-3 rounded-[10px] bg-surface-muted px-3 py-2 text-[12.5px] text-text-muted">
            {data.hiddenVisits} {data.hiddenVisits === 1 ? 'visit' : 'visits'} at outlets outside {user.franchiseName ?? 'your franchise'} {data.hiddenVisits === 1 ? 'is' : 'are'} not shown.
          </p>
        )}

        {visits.length === 0 ? (
          <p className="mt-4 text-[13.5px] text-text-muted">No visits yet.</p>
        ) : (
          <ol className="mt-4">
            {visits.map((v, i) => {
              const here = v.outlet.id === user?.outletId;
              const last = i === visits.length - 1;
              return (
                <li key={v.billId} className="relative flex gap-4 pb-5 last:pb-0">
                  {!last && <span className="absolute left-[5px] top-3 h-full w-px bg-border" aria-hidden />}
                  <span className="relative mt-1.5 h-[11px] w-[11px] shrink-0 rounded-full border-2 border-border-strong bg-surface" aria-hidden />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-baseline justify-between gap-x-3">
                      <p className="text-[14px] font-medium text-text">
                        {v.outlet.name}
                        {here && <span className="font-normal text-text-muted"> (your outlet)</span>}
                      </p>
                      <p className="text-[14px] font-medium text-text tabular">{money(v.total)}</p>
                    </div>
                    <p className="mt-0.5 text-[13px] text-text">{v.services.join(', ')}</p>
                    <p className="mt-0.5 text-[12.5px] text-text-muted">
                      <span className="whitespace-nowrap tabular">{dateIST(v.visitedAt)}, {timeIST(v.visitedAt)}</span>
                      {' · '}
                      {v.barbers.join(', ')}
                      {' · '}
                      <span className="whitespace-nowrap tabular">{v.billNo}</span>
                    </p>
                  </div>
                </li>
              );
            })}
          </ol>
        )}
      </section>
    </div>
  );
}

function Detail({ label, value, muted = false }: { label: string; value: string; muted?: boolean }) {
  return (
    <div>
      <dt className="text-2xs font-medium text-text-muted">{label}</dt>
      <dd className={`mt-0.5 ${muted ? 'text-text-muted' : 'text-text'}`}>{value}</dd>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-surface px-4 py-3">
      <dt className="text-2xs font-medium text-text-muted">{label}</dt>
      <dd className="mt-1 text-[17px] font-semibold tracking-[-0.01em] text-text tabular">{value}</dd>
    </div>
  );
}
