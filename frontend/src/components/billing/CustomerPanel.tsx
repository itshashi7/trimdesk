import type { RefObject } from 'react';
import { dateIST, moneyWhole, money, phoneDisplay } from '../../lib/format';
import { applyNameCase } from '../../lib/names';
import type { CustomerLookup } from '../../lib/types';
import { EditIcon, SearchIcon } from '../Icons';
import { Keycap } from '../Keycap';
import { CustomerEditForm, type CustomerChanges } from './CustomerEditForm';

export type LookupStatus = 'idle' | 'loading' | 'found' | 'notFound' | 'creating' | 'error';

interface Props {
  phone: string;
  onPhoneChange: (value: string) => void;
  onLookup: () => void;
  phoneRef: RefObject<HTMLInputElement>;
  status: LookupStatus;
  error: string | null;
  lookup: CustomerLookup | null;
  newName: string;
  onNewNameChange: (value: string) => void;
  onCreate: () => void;
  nameRef: RefObject<HTMLInputElement>;
  currentOutletId: number | null;
  editing: boolean;
  onEditStart: () => void;
  onEditSave: (changes: CustomerChanges) => Promise<void>;
  onEditCancel: () => void;
}

/** Synthetic seeded customer with visits at three other outlets; handy for the demo. */
const DEMO_PHONE = '9910551967';

export function CustomerPanel(props: Props) {
  const { phone, status, error, lookup } = props;

  return (
    <section aria-label="Customer" className="rounded-card border border-border bg-surface">
      <div className="px-4 pt-3">
        <label htmlFor="phone" className="mb-1.5 flex items-center justify-between text-[13px] font-semibold text-text">
          Customer phone
          <span className="flex items-center gap-1 text-2xs font-normal text-text-muted">
            <Keycap>/</Keycap> to focus
          </span>
        </label>
        <div className="relative">
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-text-muted">
            <SearchIcon />
          </span>
          <input
            id="phone"
            ref={props.phoneRef}
            inputMode="numeric"
            autoComplete="off"
            maxLength={10}
            value={phone}
            onChange={(e) => props.onPhoneChange(e.target.value.replace(/\D/g, ''))}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                props.onLookup();
              }
            }}
            placeholder="10-digit mobile number"
            aria-describedby="phone-hint"
            className="h-11 w-full rounded-[10px] border border-border bg-surface-muted pl-10 pr-24 text-[16px] font-medium tracking-[0.02em] text-text outline-none transition-colors tabular focus:border-border-strong focus:bg-surface"
          />
          <span id="phone-hint" className="absolute right-3 top-1/2 flex -translate-y-1/2 items-center gap-1 text-2xs text-text-muted">
            {status === 'loading' ? 'Looking up…' : <><Keycap>Enter</Keycap> look up</>}
          </span>
        </div>
      </div>

      <div className="min-h-[148px] px-4 pb-4 pt-3">
        {error && (
          <p role="alert" className="mb-2 rounded-[8px] bg-danger-surface px-3 py-2 text-[13px] text-danger">
            {error}
          </p>
        )}
        {status === 'found' && lookup ? (
          <CustomerCard
            lookup={lookup}
            currentOutletId={props.currentOutletId}
            editing={props.editing}
            onEditStart={props.onEditStart}
            onEditSave={props.onEditSave}
            onEditCancel={props.onEditCancel}
          />
        ) : status === 'notFound' || status === 'creating' ? (
          <NewCustomer {...props} />
        ) : (
          <EmptyState />
        )}
      </div>
    </section>
  );
}

function EmptyState() {
  return (
    <div className="flex h-[148px] flex-col items-start justify-center gap-1.5">
      <p className="text-[14px] font-medium text-text">Start with the customer's phone number</p>
      <p className="text-[13px] text-text-muted">
        Their history appears here, from any outlet in the chain. Unknown numbers become a new customer.
      </p>
      <p className="mt-2 text-2xs text-text-muted">
        Demo customer: <span className="font-medium text-text tabular">{phoneDisplay(DEMO_PHONE)}</span>
      </p>
    </div>
  );
}

function NewCustomer({ phone, newName, onNewNameChange, onCreate, nameRef, status }: Props) {
  return (
    <div className="flex h-[148px] flex-col justify-center">
      <p className="text-[14px] font-medium text-text">
        New customer <span className="font-normal text-text-muted tabular">· {phoneDisplay(phone)}</span>
      </p>
      <p className="mt-0.5 text-[13px] text-text-muted">No visits at any outlet yet. Add their name to continue.</p>
      <label htmlFor="new-name" className="sr-only">Customer name</label>
      <input
        id="new-name"
        ref={nameRef}
        value={newName}
        maxLength={120}
        onChange={(e) => onNewNameChange(applyNameCase(e.target))}
        autoCapitalize="words"
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            onCreate();
          }
        }}
        placeholder="Customer name"
        className="mt-3 h-10 w-full rounded-[10px] border border-border-strong bg-surface px-3 text-[14px] text-text outline-none"
      />
      <p className="mt-2 flex items-center gap-3 text-2xs text-text-muted">
        <span className="flex items-center gap-1">
          <Keycap>Enter</Keycap> {status === 'creating' ? 'Saving…' : 'save'}
        </span>
        <span className="flex items-center gap-1">
          <Keycap>Esc</Keycap> cancel
        </span>
      </p>
    </div>
  );
}

interface CardProps {
  lookup: CustomerLookup;
  currentOutletId: number | null;
  editing: boolean;
  onEditStart: () => void;
  onEditSave: (changes: CustomerChanges) => Promise<void>;
  onEditCancel: () => void;
}

function CustomerCard({ lookup, currentOutletId, editing, onEditStart, onEditSave, onEditCancel }: CardProps) {
  const { customer, totalVisits, totalSpend, lastVisits } = lookup;
  const outletsSeen = new Set([customer.firstVisitOutlet.id, ...lastVisits.map((v) => v.outlet.id)]);
  const preferred = customer.preferredBarber;

  return (
    <div>
      {editing ? (
        <CustomerEditForm customer={customer} onSave={onEditSave} onCancel={onEditCancel} />
      ) : (
      <>
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="truncate text-[17px] font-semibold tracking-[-0.01em] text-text">{customer.name}</p>
          <p className="flex items-center gap-2 text-[13px] text-text-muted tabular">
            {phoneDisplay(customer.phone)}
            <button
              type="button"
              data-flash="edit-customer"
              onClick={onEditStart}
              aria-label="Edit customer"
              className="flex items-center gap-1 rounded-[6px] px-1 py-0.5 font-sans text-[12.5px] font-medium text-text hover:bg-surface-muted"
            >
              <EditIcon width={15} height={15} />
              Edit <Keycap>E</Keycap>
            </button>
          </p>
        </div>
        <dl className="flex shrink-0 gap-5 text-right">
          <div>
            <dt className="text-2xs text-text-muted">Visits</dt>
            <dd className="text-[15px] font-semibold text-text tabular">{totalVisits}</dd>
          </div>
          <div>
            <dt className="text-2xs text-text-muted">Spent</dt>
            <dd className="text-[15px] font-semibold text-text tabular">{moneyWhole(totalSpend)}</dd>
          </div>
          <div>
            <dt className="text-2xs text-text-muted">Outlets</dt>
            <dd className="text-[15px] font-semibold text-text tabular">{outletsSeen.size}</dd>
          </div>
        </dl>
      </div>

      <p className="mt-1.5 text-[12.5px] text-text-muted">
        {preferred ? (
          <>
            Prefers <span className="font-medium text-text">{preferred.name}</span>
            {preferred.outletId !== currentOutletId && <> at {preferred.outletCode}</>}
          </>
        ) : (
          'No preferred barber'
        )}
        {customer.notes && (
          <>
            {' · '}
            <span className="text-text">{customer.notes}</span>
          </>
        )}
      </p>
      </>
      )}

      {lastVisits.length === 0 ? (
        <p className="mt-3 text-[13px] text-text-muted">First visit today.</p>
      ) : (
        <table className="mt-2.5 w-full text-[12.5px]">
          <caption className="sr-only">Recent visits across all outlets</caption>
          <tbody>
            {lastVisits.map((v) => {
              const here = v.outlet.id === currentOutletId;
              return (
                <tr key={v.billId} className="border-t border-border">
                  <td className="w-[92px] py-[5px] text-text-muted tabular">{dateIST(v.visitedAt)}</td>
                  <td className="w-[118px] py-[5px]">
                    <span className={here ? 'text-text-muted' : 'font-medium text-text'}>{v.outlet.name}</span>
                    {here && <span className="text-text-muted"> (here)</span>}
                  </td>
                  <td className="max-w-0 truncate py-[5px] text-text-muted">{v.services.join(', ')}</td>
                  <td className="w-[84px] py-[5px] text-right text-text tabular">{money(v.total)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
