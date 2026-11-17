import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { CheckIcon, ChevronDownIcon } from './Icons';

export interface MenuOption {
  value: number;
  label: string;
  /** Muted text on the right, e.g. an outlet code. */
  detail?: string;
}

interface Props {
  options: MenuOption[];
  value: number | null;
  onChange: (value: number) => void;
  /** Accessible name, e.g. "Barber for Haircut" or "Outlet". */
  label: string;
  /** "inline": quiet text trigger inside a row. "field": bordered button like an input. */
  variant?: 'inline' | 'field';
  placeholder?: string;
  flashId?: string;
  className?: string;
}

const OPTION_HEIGHT = 36;

/**
 * Select with a rounded, iOS-style floating menu. Native <select> lists are drawn by the OS and
 * cannot be styled, so this is a small listbox: arrows move, Enter/Space pick, Esc closes, letters
 * jump. The menu renders in a portal so scrolling containers cannot clip it.
 */
export function MenuSelect({ options, value, onChange, label, variant = 'inline', placeholder = 'Choose', flashId, className = '' }: Props) {
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [pos, setPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const listId = useId();
  const selected = options.find((o) => o.value === value);

  function openMenu() {
    setActive(Math.max(0, options.findIndex((o) => o.value === value)));
    setOpen(true);
  }

  function close(returnFocus = true) {
    setOpen(false);
    setPos(null);
    if (returnFocus) buttonRef.current?.focus();
  }

  function pick(index: number) {
    const option = options[index];
    if (option) onChange(option.value);
    close();
  }

  // Place the menu under the trigger, or above it when there is no room below.
  useLayoutEffect(() => {
    if (!open || !buttonRef.current) return;
    const r = buttonRef.current.getBoundingClientRect();
    const width = Math.max(variant === 'field' ? r.width : 0, 200);
    const height = Math.min(options.length * OPTION_HEIGHT + 8, 320);
    const below = r.bottom + 6 + height <= window.innerHeight - 8;
    setPos({
      top: below ? r.bottom + 6 : Math.max(8, r.top - 6 - height),
      left: Math.max(8, Math.min(r.left, window.innerWidth - width - 8)),
      width,
    });
  }, [open, options.length, variant]);

  // Focus the menu once it is on screen, so arrow keys and Enter work right away.
  useEffect(() => {
    if (open && pos) listRef.current?.focus();
  }, [open, pos]);

  // Keep the highlighted option visible in a long list.
  useEffect(() => {
    if (open) document.getElementById(`${listId}-${active}`)?.scrollIntoView({ block: 'nearest' });
  }, [open, active, listId]);

  // Close on outside click, page scroll or resize (the anchor would move).
  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      const t = e.target as Node;
      if (!listRef.current?.contains(t) && !buttonRef.current?.contains(t)) close(false);
    };
    const onScroll = (e: Event) => {
      if (!listRef.current?.contains(e.target as Node)) close(false);
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

  // While open, every key belongs to the menu (so page shortcuts don't fire underneath).
  const onListKeyDown = (e: KeyboardEvent) => {
    e.stopPropagation();
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setActive((i) => (i + 1) % options.length);
        break;
      case 'ArrowUp':
        e.preventDefault();
        setActive((i) => (i - 1 + options.length) % options.length);
        break;
      case 'Home':
        e.preventDefault();
        setActive(0);
        break;
      case 'End':
        e.preventDefault();
        setActive(options.length - 1);
        break;
      case 'Enter':
      case ' ':
        e.preventDefault();
        pick(active);
        break;
      case 'Escape':
        e.preventDefault();
        close();
        break;
      case 'Tab':
        close(false);
        break;
      default:
        if (e.key.length === 1) {
          const i = options.findIndex((o) => o.label.toLowerCase().startsWith(e.key.toLowerCase()));
          if (i >= 0) setActive(i);
        }
    }
  };

  const onButtonKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      e.stopPropagation();
      openMenu();
    }
  };

  const triggerClass =
    variant === 'field'
      ? `flex h-10 items-center justify-between gap-2 rounded-[10px] border bg-surface px-3 text-[14px] text-text transition-colors hover:border-border-strong ${
          open ? 'border-border-strong' : 'border-border'
        }`
      : `-ml-1.5 flex items-center gap-1 rounded-[8px] py-0.5 pl-1.5 pr-1 text-[12.5px] text-text-muted transition-colors hover:bg-surface-muted hover:text-text ${
          open ? 'bg-surface-muted text-text' : ''
        }`;

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        data-flash={flashId}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-label={`${label}: ${selected?.label ?? 'none'}`}
        onClick={() => (open ? close() : openMenu())}
        onKeyDown={onButtonKeyDown}
        className={`${triggerClass} ${className}`}
      >
        <span className="truncate">
          {selected ? selected.label : placeholder}
          {variant === 'field' && selected?.detail && <span className="ml-1.5 text-text-muted">{selected.detail}</span>}
        </span>
        <ChevronDownIcon
          width={variant === 'field' ? 16 : 14}
          height={variant === 'field' ? 16 : 14}
          className={`shrink-0 text-text-muted transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open &&
        pos &&
        createPortal(
          <ul
            ref={listRef}
            id={listId}
            role="listbox"
            tabIndex={-1}
            aria-label={label}
            aria-activedescendant={`${listId}-${active}`}
            onKeyDown={onListKeyDown}
            style={{ top: pos.top, left: pos.left, width: pos.width }}
            className="menu-in fixed z-50 max-h-[320px] overflow-y-auto rounded-[12px] bg-surface p-1 shadow-pop outline-none"
          >
            {options.map((o, i) => {
              const isSelected = o.value === value;
              return (
                <li
                  key={o.value}
                  id={`${listId}-${i}`}
                  role="option"
                  aria-selected={isSelected}
                  onPointerEnter={() => setActive(i)}
                  onClick={() => pick(i)}
                  className={`flex h-9 cursor-default select-none items-center gap-2 rounded-[8px] px-2.5 text-[13.5px] text-text ${
                    i === active ? 'bg-surface-muted' : ''
                  } ${isSelected ? 'font-medium' : ''}`}
                >
                  <span className="min-w-0 flex-1 truncate">{o.label}</span>
                  {o.detail && <span className="shrink-0 text-2xs font-normal text-text-muted tabular">{o.detail}</span>}
                  <span className="w-4 shrink-0">{isSelected && <CheckIcon width={16} height={16} />}</span>
                </li>
              );
            })}
          </ul>,
          document.body,
        )}
    </>
  );
}
