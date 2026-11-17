import { useEffect, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { ChevronRightIcon, SearchIcon } from '../components/Icons';
import { ApiError, api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { dateIST, phoneDisplay } from '../lib/format';

/** Find a customer by phone or name, then open their profile. The query lives in the URL. */
export function CustomersPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const [text, setText] = useState(params.get('q') ?? '');
  const [query, setQuery] = useState(text.trim());

  // Search after a short pause in typing.
  useEffect(() => {
    const t = window.setTimeout(() => {
      const q = text.trim();
      setQuery(q);
      setParams(q ? { q } : {}, { replace: true });
    }, 250);
    return () => window.clearTimeout(t);
  }, [text, setParams]);

  const search = useQuery({
    queryKey: ['customer-search', query],
    queryFn: () => api.searchCustomers(query),
    enabled: query.length >= 2,
    placeholderData: keepPreviousData,
  });
  const results = query.length >= 2 ? (search.data ?? []) : [];

  return (
    <main className="mx-auto w-full max-w-[760px] px-4 py-5 sm:px-6 sm:py-7">
      <h1 className="text-[22px] font-semibold tracking-[-0.02em] text-text">Customers</h1>
      <p className="mt-0.5 text-[13.5px] text-text-muted">
        {user?.role === 'FRANCHISE_OWNER'
          ? `Customers who visited ${user.franchiseName ?? 'your'} outlets.`
          : 'Every customer in the chain, with their history at all outlets.'}
      </p>

      <div className="relative mt-5">
        <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted">
          <SearchIcon />
        </span>
        <label htmlFor="customer-search" className="sr-only">Search customers by phone or name</label>
        <input
          id="customer-search"
          autoFocus
          autoComplete="off"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Phone number or name"
          className="h-12 w-full rounded-[12px] border border-border bg-surface pl-11 pr-4 text-[15px] text-text outline-none transition-colors focus:border-border-strong"
        />
      </div>

      <div className="mt-4">
        {query.length < 2 ? (
          <p className="px-1 text-[13.5px] text-text-muted">Type at least 2 characters: the start of a phone number, or part of a name.</p>
        ) : search.isError ? (
          <p role="alert" className="rounded-card bg-danger-surface px-4 py-3 text-[13.5px] text-danger">
            {search.error instanceof ApiError ? search.error.message : 'Search failed. Try again.'}
          </p>
        ) : search.isPending ? (
          <p className="px-1 text-[13.5px] text-text-muted">Searching…</p>
        ) : results.length === 0 ? (
          <p className="px-1 text-[13.5px] text-text-muted">No customers match “{query}”.</p>
        ) : (
          <ul className="divide-y divide-border overflow-hidden rounded-card border border-border bg-surface">
            {results.map((c) => (
              <li key={c.id}>
                <Link
                  to={`/customers/${c.id}`}
                  className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-surface-muted focus-visible:bg-surface-muted"
                >
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[14.5px] font-medium text-text">{c.name}</span>
                    <span className="block text-[12.5px] text-text-muted tabular">{phoneDisplay(c.phone)}</span>
                  </span>
                  <span className="text-right text-[12.5px] text-text-muted">
                    <span className="block text-text tabular">
                      {c.visits} {c.visits === 1 ? 'visit' : 'visits'}
                    </span>
                    {c.lastVisit && <span className="block">Last {dateIST(c.lastVisit)}</span>}
                  </span>
                  <ChevronRightIcon width={16} height={16} className="shrink-0 text-text-muted" />
                </Link>
              </li>
            ))}
          </ul>
        )}
        {results.length === 20 && (
          <p className="mt-2 px-1 text-2xs text-text-muted">Showing the 20 most recent matches. Type more to narrow it down.</p>
        )}
      </div>
    </main>
  );
}
