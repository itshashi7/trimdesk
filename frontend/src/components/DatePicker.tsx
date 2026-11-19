import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { shiftDate } from '../lib/format';
import { ChevronLeftIcon, ChevronRightIcon } from './Icons';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

interface Props {
  /** Selected day as YYYY-MM-DD. */
  value: string;
  onChange: (value: string) => void;
  /** Latest pickable day (YYYY-MM-DD), e.g. today for reports. */
  max?: string;
  /** Today in the business time zone, highlighted in the grid. */
  today: string;
  /** Accessible name, e.g. "Report date". */
  label: string;
  /** Trigger content (icon + formatted date). */
  children: ReactNode;
  className?: string;
}

const WIDTH = 296;
const HEIGHT = 340;
const WEEKDAYS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];
const WEEKDAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const monthTitle = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric', timeZone: 'UTC' });
const dayName = new Intl.DateTimeFormat('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });

const utc = (iso: string) => new Date(`${iso}T00:00:00Z`);
const monthOf = (iso: string) => iso.slice(0, 7);

function shiftMonth(ym: string, delta: number): string {
  const d = utc(`${ym}-01`);
  d.setUTCMonth(d.getUTCMonth() + delta);
  return d.toISOString().slice(0, 7);
}

/** Cells for a month grid starting on Sunday: null for the blanks before day 1. */
function monthCells(ym: string): (string | null)[] {
  const first = utc(`${ym}-01`);
  const days = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate();
  const cells: (string | null)[] = Array.from({ length: first.getUTCDay() }, () => null);
  for (let d = 1; d <= days; d++) cells.push(`${ym}-${String(d).padStart(2, '0')}`);
  return cells;
}

/**
 * iOS-style calendar popover. The native date picker is drawn by the browser and cannot be styled,
 * so this is a small grid: arrows move by day/week, PageUp/PageDown by month, Enter picks, Esc
 * closes. Renders in a portal so scrolling containers cannot clip it.
 */
export function DatePicker({ value, onChange, max, today, label, children, className = '' }: Props) {
  const [open, setOpen] = useState(false);
  const [month, setMonth] = useState(monthOf(value));
  const [active, setActive] = useState(value);
  /** "days" is the month grid; "months" lets the user jump to any month and year. */
  const [view, setView] = useState<'days' | 'months'>('days');
  const [year, setYear] = useState(Number(value.slice(0, 4)));
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const id = useId();

  const isDisabled = (iso: string) => max !== undefined && iso > max;
  const clamp = (iso: string) => (max !== undefined && iso > max ? max : iso);

  function openPicker() {
    setMonth(monthOf(value));
    setActive(value);
    setView('days');
    setOpen(true);
  }

  function close(returnFocus = true) {
    setOpen(false);
    setPos(null);
    if (returnFocus) buttonRef.current?.focus();
  }

  function pick(iso: string) {
    if (isDisabled(iso)) return;
    onChange(iso);
    close();
  }

  function showMonths() {
    setYear(Number(month.slice(0, 4)));
    setView('months');
  }

  /** Jump to a month from the month/year view, keeping the same day number where it exists. */
  function chooseMonth(ym: string) {
    const day = Math.min(Number(active.slice(8)), monthCells(ym).filter(Boolean).length);
    setMonth(ym);
    setActive(clamp(`${ym}-${String(day).padStart(2, '0')}`));
    setView('days');
  }

  function moveActive(next: string) {
    const target = clamp(next);
    setActive(target);
    setMonth(monthOf(target));
  }

  useLayoutEffect(() => {
    if (!open || !buttonRef.current) return;
    const r = buttonRef.current.getBoundingClientRect();
    const below = r.bottom + 8 + HEIGHT <= window.innerHeight - 8;
    setPos({
      top: below ? r.bottom + 8 : Math.max(8, r.top - 8 - HEIGHT),
      left: Math.max(8, Math.min(r.left + r.width / 2 - WIDTH / 2, window.innerWidth - WIDTH - 8)),
    });
  }, [open]);

  // Keep keyboard focus on the calendar: on open, and after switching views (the clicked
  // month button disappears, which would otherwise drop focus to the page).
  useEffect(() => {
    if (!open || !pos) return;
    if (view === 'days') gridRef.current?.focus();
    else panelRef.current?.focus();
  }, [open, pos, view]);

  // Close on outside click, page scroll or resize (the anchor would move).
  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      const t = e.target as Node;
      if (!panelRef.current?.contains(t) && !buttonRef.current?.contains(t)) close(false);
    };
    const onScroll = (e: Event) => {
      if (!panelRef.current?.contains(e.target as Node)) close(false);
    };
    const onResize = () => close(false);
    document.addEventListener('pointerdown', onPointer);
    window.addEventListener('resize', onResize);
    document.addEventListener('scroll', onScroll, true);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      window.removeEventListener('resize', onResize);
      document.removeEventListener('scroll', onScroll, true);
    };
  }, [open]);

  // While open, every key belongs to the calendar (so page shortcuts don't fire underneath).
  const onKeyDown = (e: KeyboardEvent) => {
    e.stopPropagation();
    if (view === 'months') {
      if (e.key === 'Escape') {
        e.preventDefault();
        setView('days');
      } else if (e.key === 'Tab') {
        close(false);
      }
      return;
    }
    const step: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
    if (e.key in step) {
      e.preventDefault();
      moveActive(shiftDate(active, step[e.key]));
    } else if (e.key === 'PageUp' || e.key === 'PageDown') {
      e.preventDefault();
      const ym = shiftMonth(monthOf(active), e.key === 'PageUp' ? -1 : 1);
      const day = Math.min(Number(active.slice(8)), monthCells(ym).filter(Boolean).length);
      moveActive(`${ym}-${String(day).padStart(2, '0')}`);
    } else if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      pick(active);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      close();
    } else if (e.key === 'Tab') {
      close(false);
    }
  };

  const maxYear = max !== undefined ? Number(max.slice(0, 4)) : Infinity;
  const nextBlocked = view === 'days' ? max !== undefined && shiftMonth(month, 1) > monthOf(max) : year + 1 > maxYear;
  const goPrev = () => (view === 'days' ? setMonth(shiftMonth(month, -1)) : setYear(year - 1));
  const goNext = () => (view === 'days' ? setMonth(shiftMonth(month, 1)) : setYear(year + 1));
  const cells = monthCells(month);
  const navButton =
    'flex h-8 w-8 items-center justify-center rounded-full text-accent transition-colors hover:bg-accent-soft disabled:text-text-muted disabled:opacity-40 disabled:hover:bg-transparent';

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`${label}: ${dayName.format(utc(value))}`}
        onClick={() => (open ? close() : openPicker())}
        className={className}
      >
        {children}
      </button>

      {open &&
        pos &&
        createPortal(
          <div
            ref={panelRef}
            role="dialog"
            tabIndex={-1}
            aria-label={label}
            onKeyDown={onKeyDown}
            style={{ top: pos.top, left: pos.left, width: WIDTH }}
            className="menu-in fixed z-50 rounded-[16px] bg-surface p-3 shadow-pop outline-none"
          >
            <div className="flex items-center justify-between pb-2 pl-2">
              {/* Tap the title to pick a month and year, like the iOS calendar. */}
              <button
                type="button"
                tabIndex={-1}
                aria-expanded={view === 'months'}
                aria-label={view === 'days' ? 'Choose month and year' : 'Back to days'}
                onClick={() => (view === 'days' ? showMonths() : setView('days'))}
                className="-ml-2 flex items-center gap-1 rounded-full py-1 pl-2 pr-1.5 text-[15px] font-semibold tracking-[-0.01em] text-text transition-colors hover:bg-surface-muted"
              >
                <span aria-live="polite">{view === 'days' ? monthTitle.format(utc(`${month}-01`)) : year}</span>
                <ChevronRightIcon
                  width={16}
                  height={16}
                  className={`text-accent transition-transform duration-200 ${view === 'months' ? 'rotate-90' : ''}`}
                />
              </button>
              <div className="flex items-center">
                <button type="button" tabIndex={-1} aria-label={view === 'days' ? 'Previous month' : 'Previous year'} onClick={goPrev} className={navButton}>
                  <ChevronLeftIcon width={18} height={18} />
                </button>
                <button
                  type="button"
                  tabIndex={-1}
                  aria-label={view === 'days' ? 'Next month' : 'Next year'}
                  disabled={nextBlocked}
                  onClick={goNext}
                  className={navButton}
                >
                  <ChevronRightIcon width={18} height={18} />
                </button>
              </div>
            </div>

            {view === 'months' && (
              <div role="listbox" aria-label={`Months of ${year}`} className="grid h-[252px] grid-cols-3 content-center gap-2">
                {MONTHS.map((name, i) => {
                  const ym = `${year}-${String(i + 1).padStart(2, '0')}`;
                  const disabled = max !== undefined && ym > monthOf(max);
                  const selected = ym === monthOf(value);
                  const isCurrent = ym === monthOf(today);
                  return (
                    <button
                      key={ym}
                      type="button"
                      role="option"
                      aria-selected={selected}
                      disabled={disabled}
                      onClick={() => chooseMonth(ym)}
                      className={`h-12 rounded-full text-[14px] transition-colors duration-200 ${
                        selected
                          ? 'bg-accent font-semibold text-accent-contrast'
                          : disabled
                            ? 'text-text-muted opacity-35'
                            : isCurrent
                              ? 'font-semibold text-accent hover:bg-accent-soft'
                              : 'text-text hover:bg-accent-soft hover:text-accent-soft-text'
                      }`}
                    >
                      {name}
                    </button>
                  );
                })}
              </div>
            )}

            <div className={view === 'days' ? '' : 'hidden'}>
            <div className="grid grid-cols-7 pb-1" aria-hidden>
              {WEEKDAYS.map((d, i) => (
                <span key={i} className="flex h-6 items-center justify-center text-2xs font-medium text-text-muted">
                  {d}
                </span>
              ))}
            </div>

            <div
              ref={gridRef}
              role="grid"
              tabIndex={-1}
              aria-label={monthTitle.format(utc(`${month}-01`))}
              aria-activedescendant={monthOf(active) === month ? `${id}-${active}` : undefined}
              // Pointer left the days: drop the hover circle (it fades out) back onto the selected day.
              onPointerLeave={() => setActive(value)}
              className="grid grid-cols-7 gap-y-0.5 outline-none"
            >
              {cells.map((iso, i) =>
                iso === null ? (
                  <span key={`blank-${i}`} aria-hidden />
                ) : (
                  <DayCell
                    key={iso}
                    id={`${id}-${iso}`}
                    iso={iso}
                    selected={iso === value}
                    isToday={iso === today}
                    active={iso === active}
                    disabled={isDisabled(iso)}
                    onPick={() => pick(iso)}
                    onHover={() => !isDisabled(iso) && setActive(iso)}
                  />
                ),
              )}
            </div>
            </div>

            <div className="mt-2 flex items-center justify-between border-t border-border pl-2 pt-2">
              <span className="text-2xs text-text-muted">{WEEKDAY_NAMES[utc(active).getUTCDay()]}</span>
              <button
                type="button"
                tabIndex={-1}
                onClick={() => pick(today)}
                disabled={value === today}
                className="h-8 rounded-full px-3 text-[13px] font-medium text-accent transition-colors hover:bg-accent-soft disabled:text-text-muted disabled:hover:bg-transparent"
              >
                Today
              </button>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}

interface DayCellProps {
  id: string;
  iso: string;
  selected: boolean;
  isToday: boolean;
  active: boolean;
  disabled: boolean;
  onPick: () => void;
  onHover: () => void;
}

function DayCell({ id, iso, selected, isToday, active, disabled, onPick, onHover }: DayCellProps) {
  const tone = selected
    ? 'bg-accent font-semibold text-accent-contrast'
    : disabled
      ? 'text-text-muted opacity-35'
      : isToday
        ? `font-semibold text-accent ${active ? 'bg-accent-soft' : ''}`
        : active
          ? 'bg-accent-soft text-accent-soft-text'
          : 'text-text';
  return (
    <span className="flex justify-center">
      <span
        id={id}
        role="gridcell"
        aria-selected={selected}
        aria-disabled={disabled || undefined}
        aria-current={isToday ? 'date' : undefined}
        aria-label={dayName.format(utc(iso))}
        onClick={onPick}
        onPointerEnter={onHover}
        className={`flex h-9 w-9 cursor-default select-none items-center justify-center rounded-full text-[14px] tabular transition-colors duration-200 ${tone} ${
          selected && active ? 'ring-2 ring-accent-soft ring-offset-0' : ''
        }`}
      >
        {Number(iso.slice(8))}
      </span>
    </span>
  );
}
