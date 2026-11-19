import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BillPanel, type DraftItem } from '../components/billing/BillPanel';
import type { CustomerChanges } from '../components/billing/CustomerEditForm';
import { CustomerPanel, type LookupStatus } from '../components/billing/CustomerPanel';
import { ServiceGrid, serviceFlashId } from '../components/billing/ServiceGrid';
import { ShortcutLegend } from '../components/billing/ShortcutLegend';
import { Receipt } from '../components/Receipt';
import { useToast } from '../components/Toast';
import { ApiError, api, errorMessage } from '../lib/api';
import { useAuth } from '../lib/auth';
import { parseDiscount, previewBill } from '../lib/billMath';
import { flash } from '../lib/flash';
import { dateTimeIST, money } from '../lib/format';
import { normalizeName } from '../lib/names';
import { usePhoneHistory } from '../hooks/usePhoneHistory';
import type { Bill, CustomerLookup, PaymentMode, ServiceItem } from '../lib/types';

const PAY_KEYS: Record<string, PaymentMode> = { F2: 'CASH', F3: 'UPI', F4: 'CARD' };
const PHONE_PATTERN = /^[6-9]\d{9}$/;

/** Shortcut services by code, then the rest alphabetically. */
function sortServices(services: ServiceItem[]) {
  return [...services].sort((a, b) => {
    if (a.code && b.code) return Number(a.code) - Number(b.code);
    if (a.code) return -1;
    if (b.code) return 1;
    return a.name.localeCompare(b.name);
  });
}

const errorText = errorMessage;

export function BillingPage() {
  const { user } = useAuth();
  const toast = useToast();
  const outletId = user?.outletId ?? 0;
  const taxRate = user?.outletTaxRate ?? 0;

  const servicesQuery = useQuery({ queryKey: ['services'], queryFn: api.services });
  const barbersQuery = useQuery({ queryKey: ['barbers', outletId], queryFn: () => api.barbers(outletId) });
  const services = useMemo(() => sortServices(servicesQuery.data ?? []), [servicesQuery.data]);
  const byCode = useMemo(() => new Map(services.filter((s) => s.code).map((s) => [s.code as string, s])), [services]);
  const barbers = useMemo(() => barbersQuery.data ?? [], [barbersQuery.data]);

  const [phone, setPhone] = useState('');
  const phoneHistory = usePhoneHistory(user?.id);
  const [status, setStatus] = useState<LookupStatus>('idle');
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [lookup, setLookup] = useState<CustomerLookup | null>(null);
  const [newName, setNewName] = useState('');
  const [items, setItems] = useState<DraftItem[]>([]);
  const [discountText, setDiscountText] = useState('');
  const [saving, setSaving] = useState<PaymentMode | null>(null);
  const [payError, setPayError] = useState<string | null>(null);
  const [saved, setSaved] = useState<Bill | null>(null);
  const [editing, setEditing] = useState(false);

  const phoneRef = useRef<HTMLInputElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const discountRef = useRef<HTMLInputElement>(null);
  const workspaceRef = useRef<HTMLDivElement>(null);
  const lastBarberRef = useRef<number | null>(null);
  const nextUid = useRef(1);
  const pendingItemFlash = useRef<number | null>(null);

  const discount = parseDiscount(discountText);
  const preview = previewBill(items.map((i) => i.service.price), discount ?? null, taxRate);

  const preferred = lookup?.customer.preferredBarber;
  const preferredHere = preferred && preferred.outletId === outletId ? preferred.id : null;

  const focusWorkspace = () => workspaceRef.current?.focus();

  // Move focus to where the next keystroke belongs.
  useEffect(() => {
    if (status === 'found') focusWorkspace();
    if (status === 'notFound') nameRef.current?.focus();
  }, [status]);

  useEffect(() => {
    phoneRef.current?.focus();
  }, []);

  // Ring a newly added bill line once it is on screen.
  useEffect(() => {
    if (pendingItemFlash.current !== null) {
      flash(`item-${pendingItemFlash.current}`);
      pendingItemFlash.current = null;
    }
  }, [items]);

  function resetBill() {
    setPhone('');
    setStatus('idle');
    setLookupError(null);
    setLookup(null);
    setNewName('');
    setItems([]);
    setDiscountText('');
    setPayError(null);
    setSaved(null);
    setEditing(false);
    lastBarberRef.current = null;
    requestAnimationFrame(() => phoneRef.current?.focus());
  }

  function onPhoneChange(value: string) {
    if (saved) {
      setSaved(null);
      setItems([]);
      setDiscountText('');
      setPayError(null);
    }
    setPhone(value);
    setEditing(false);
    if (status !== 'idle' && status !== 'loading') {
      setStatus('idle');
      setLookup(null);
    }
    setLookupError(null);
  }

  async function lookUp() {
    if (!PHONE_PATTERN.test(phone)) {
      setLookupError('Enter a 10-digit mobile number starting with 6, 7, 8 or 9.');
      return;
    }
    phoneHistory.remember(phone);
    setStatus('loading');
    setLookupError(null);
    try {
      setLookup(await api.lookupCustomer(phone));
      setStatus('found');
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setLookup(null);
        setNewName('');
        setStatus('notFound');
      } else {
        setStatus('error');
        setLookupError(errorText(e, 'Lookup failed. Try again.'));
      }
    }
  }

  async function createCustomer() {
    const name = normalizeName(newName);
    if (!name) {
      setLookupError('Enter the customer name to continue.');
      return;
    }
    setStatus('creating');
    setLookupError(null);
    try {
      const customer = await api.createCustomer(phone, name);
      setLookup({ customer, totalVisits: 0, totalSpend: 0, lastVisits: [] });
      setStatus('found');
      toast(`${customer.name} added as a new customer`);
    } catch (e) {
      setStatus('notFound');
      setLookupError(errorText(e, 'Could not save the customer.'));
    }
  }

  function startEdit() {
    if (status !== 'found' || !lookup) return;
    flash('edit-customer', 'key-e');
    setEditing(true);
  }

  /** Applies a correction; the card and phone field show the saved values. Errors go back to the form. */
  async function saveEdit(changes: CustomerChanges) {
    if (!lookup) return;
    const customer = await api.updateCustomer(lookup.customer.id, changes);
    setLookup({ ...lookup, customer });
    setPhone(customer.phone);
    setEditing(false);
    toast(`${customer.name} updated`);
    focusWorkspace();
  }

  function cancelEdit() {
    setEditing(false);
    focusWorkspace();
  }

  function addService(service: ServiceItem) {
    if (saved) {
      toast('This bill is paid. Press Esc for a new bill.', 'error');
      return;
    }
    const barberId = preferredHere ?? lastBarberRef.current ?? barbers[0]?.id;
    if (barberId === undefined) {
      toast('Barbers are still loading.', 'error');
      return;
    }
    const uid = nextUid.current++;
    pendingItemFlash.current = uid;
    setItems((prev) => [...prev, { uid, service, barberId }]);
    setPayError(null);
    flash(serviceFlashId(service));
  }

  function changeBarber(uid: number, barberId: number) {
    lastBarberRef.current = barberId;
    setItems((prev) => prev.map((i) => (i.uid === uid ? { ...i, barberId } : i)));
  }

  function cycleBarber() {
    if (saved || items.length === 0 || barbers.length < 2) return;
    const last = items[items.length - 1];
    const idx = barbers.findIndex((b) => b.id === last.barberId);
    changeBarber(last.uid, barbers[(idx + 1) % barbers.length].id);
    flash('barber-last', 'key-b');
  }

  function removeLast() {
    if (saved || items.length === 0) return;
    setItems((prev) => prev.slice(0, -1));
    flash('key-del');
  }

  async function pay(mode: PaymentMode) {
    if (saving || saved) return;
    flash(`pay-${mode}`, `key-${Object.keys(PAY_KEYS).find((k) => PAY_KEYS[k] === mode)?.toLowerCase()}`);
    if (!lookup) {
      setPayError('Look up the customer first. Press / and type their phone.');
      phoneRef.current?.focus();
      return;
    }
    if (items.length === 0) {
      setPayError('Add at least one service. Press 1–9.');
      return;
    }
    if (discount === undefined) {
      setPayError('Fix the discount: an amount like 50 or a percent like 10%.');
      discountRef.current?.focus();
      return;
    }
    setSaving(mode);
    setPayError(null);
    try {
      const bill = await api.createBill({
        customerId: lookup.customer.id,
        items: items.map((i) => ({ serviceId: i.service.id, barberId: i.barberId })),
        discount,
        paymentMode: mode,
      });
      setSaved(bill);
      toast(`Bill ${bill.billNo} saved`);
      focusWorkspace();
    } catch (e) {
      setPayError(errorText(e, 'Could not save the bill. Try again.'));
    } finally {
      setSaving(null);
    }
  }

  function print() {
    if (!saved) {
      toast('Save the bill first with F2, F3 or F4.', 'error');
      return;
    }
    flash('act-print', 'key-print');
    window.print();
  }

  function whatsApp() {
    if (!saved) {
      toast('Save the bill first with F2, F3 or F4.', 'error');
      return;
    }
    flash('act-w', 'key-w');
    const lines = [
      `${saved.outletName} · Bill ${saved.billNo}`,
      dateTimeIST(saved.createdAt),
      '',
      ...saved.items.map((i) => `${i.serviceName}: ${money(i.price)}`),
      saved.discount > 0 ? `Discount: -${money(saved.discount)}` : null,
      `Total: ${money(saved.total)} (${saved.paymentMode})`,
      `Includes tax: ${money(saved.taxAmount)}`,
      '',
      'Thank you, visit again!',
    ].filter((l): l is string => l !== null);
    window.open(`https://wa.me/91${saved.customerPhone}?text=${encodeURIComponent(lines.join('\n'))}`, '_blank', 'noopener');
  }

  // The whole counter runs from the keyboard (CLAUDE.md §6).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      const inField = target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement;
      const key = e.key;

      if (key === 'F1' || (key === '/' && !inField)) {
        e.preventDefault();
        flash('key-search');
        if (saved) resetBill();
        else {
          phoneRef.current?.focus();
          phoneRef.current?.select();
        }
        return;
      }
      if (key in PAY_KEYS) {
        e.preventDefault();
        if (!e.repeat) void pay(PAY_KEYS[key]);
        return;
      }
      if ((e.ctrlKey || e.metaKey) && key.toLowerCase() === 'p') {
        e.preventDefault();
        print();
        return;
      }
      if (key === 'Escape') {
        e.preventDefault();
        flash('key-esc');
        if (target === discountRef.current || target instanceof HTMLSelectElement) focusWorkspace();
        else if (target === nameRef.current) {
          setStatus('idle');
          setNewName('');
          setLookupError(null);
          phoneRef.current?.focus();
        } else resetBill();
        return;
      }
      if (key === 'Enter' && target === discountRef.current) {
        e.preventDefault();
        focusWorkspace();
        return;
      }
      if (inField || e.ctrlKey || e.metaKey || e.altKey) return;

      if (/^[1-9]$/.test(key)) {
        const service = byCode.get(key);
        if (service) {
          e.preventDefault();
          flash('key-digit');
          addService(service);
        }
        return;
      }
      switch (key.toLowerCase()) {
        case 'b':
          e.preventDefault();
          cycleBarber();
          break;
        case 'd':
          if (saved) break;
          e.preventDefault();
          flash('key-d');
          discountRef.current?.focus();
          discountRef.current?.select();
          break;
        case 'w':
          e.preventDefault();
          whatsApp();
          break;
        case 'e':
          e.preventDefault();
          startEdit();
          break;
        case 'delete':
          e.preventDefault();
          removeLast();
          break;
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const menuError = servicesQuery.isError || barbersQuery.isError;

  return (
    // Keeps its full counter layout when a laptop is zoomed in; the panel scrolls sideways instead.
    <div className="flex min-h-0 min-w-[1000px] flex-1 flex-col">
      <div
        ref={workspaceRef}
        tabIndex={-1}
        className="grid min-h-0 flex-1 grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)] gap-4 p-4 outline-none"
      >
        <div className="flex min-h-0 flex-col gap-4">
          <CustomerPanel
            phone={phone}
            onPhoneChange={(value) => {
              phoneHistory.reset();
              onPhoneChange(value);
            }}
            onHistory={(direction) => {
              const value = phoneHistory.step(direction, phone);
              if (value !== null) onPhoneChange(value);
            }}
            hasHistory={phoneHistory.count > 0}
            onLookup={() => void lookUp()}
            phoneRef={phoneRef}
            status={status}
            error={lookupError}
            lookup={lookup}
            newName={newName}
            onNewNameChange={setNewName}
            onCreate={() => void createCustomer()}
            nameRef={nameRef}
            currentOutletId={outletId}
            editing={editing}
            onEditStart={startEdit}
            onEditSave={saveEdit}
            onEditCancel={cancelEdit}
          />
          {menuError ? (
            <p role="alert" className="rounded-card border border-border bg-danger-surface p-4 text-[13px] text-danger">
              The service menu could not load. Check the connection and reload the page.
            </p>
          ) : servicesQuery.isPending ? (
            <div className="rounded-card border border-border bg-surface p-3" aria-busy="true" aria-label="Loading the service menu">
              <div className="mb-3 h-4 w-20 animate-pulse rounded bg-surface-muted" />
              <div className="grid grid-cols-4 gap-2">
                {Array.from({ length: 12 }, (_, i) => (
                  <div key={i} className="h-[53px] animate-pulse rounded-[10px] bg-surface-muted" />
                ))}
              </div>
            </div>
          ) : (
            <ServiceGrid services={services} disabled={saved !== null} onAdd={addService} />
          )}
        </div>

        <BillPanel
          items={items}
          barbers={barbers}
          onBarberChange={changeBarber}
          onRemove={(uid) => setItems((prev) => prev.filter((i) => i.uid !== uid))}
          discountText={discountText}
          onDiscountChange={setDiscountText}
          discountRef={discountRef}
          discountInvalid={discount === undefined}
          preview={preview}
          taxRate={taxRate}
          customerName={lookup?.customer.name ?? null}
          branch={user?.outletName ? `${user.outletName} (${user.outletCode})` : null}
          saving={saving}
          error={payError}
          onPay={(mode) => void pay(mode)}
          saved={saved}
          onPrint={print}
          onWhatsApp={whatsApp}
          onNewBill={resetBill}
        />
      </div>
      <ShortcutLegend />
      {saved && <Receipt bill={saved} />}
    </div>
  );
}
