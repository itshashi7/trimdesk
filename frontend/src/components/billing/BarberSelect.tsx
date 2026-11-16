import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import type { Barber } from '../../lib/types';
import { CheckIcon, ChevronDownIcon } from '../Icons';

interface Props {
  barbers: Barber[];
  value: number;
  onChange: (barberId: number) => void;
  /** Accessible name, e.g. "Barber for Haircut". */
  label: string;
  flashId?: string;
}

const MENU_WIDTH = 200;
const OPTION_HEIGHT = 36;

/**
 * Barber picker with a rounded, iOS-style floating menu. Native <select> lists are drawn by the
 * OS and cannot be styled, so this is a small listbox: arrows move, Enter/Space pick, Esc closes.
 * The menu renders in a portal so the scrolling bill list cannot clip it.
 */
export function BarberSelect({ barbers, value, onChange, label, flashId }: Props) {
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const listId = useId();
  const selected = barbers.find((b) => b.id === value);

  function openMenu() {
    setActive(Math.max(0, barbers.findIndex((b) => b.id === value)));
    setOpen(true);
  }

  function close(returnFocus = true) {
    setOpen(false);
    setPos(null);
    if (returnFocus) buttonRef.current?.focus();
  }

  function pick(index: number) {
    const barber = barbers[index];
    if (barber) onChange(barber.id);
    close();
  }

  // Place the menu under the trigger, or above it when there is no room below.
  useLayoutEffect(() => {
    if (!open || !buttonRef.current) return;
    const r = buttonRef.current.getBoundingClientRect();
    const height = barbers.length * OPTION_HEIGHT + 8;
    const below = r.bottom + 6 + height <= window.innerHeight - 8;
    setPos({
      top: below ? r.bottom + 6 : r.top - 6 - height,
      left: Math.min(r.left, window.innerWidth - MENU_WIDTH - 8),
    });
  }, [open, barbers.length]);

  // Focus the menu once it is on screen, so arrow keys and Enter work right away.
  useEffect(() => {
    if (open && pos) listRef.current?.focus();
  }, [open, pos]);

  // Close on outside click, scroll or resize (the anchor would move).
  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      const t = e.target as Node;
      if (!listRef.current?.contains(t) && !buttonRef.current?.contains(t)) close(false);
    };
    const onMove = () => close(false);
    document.addEventListener('pointerdown', onPointer);
    window.addEventListener('resize', onMove);
    document.addEventListener('scroll', onMove, true);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      window.removeEventListener('resize', onMove);
      document.removeEventListener('scroll', onMove, true);
    };
  }, [open]);

  // While open, every key belongs to the menu (so 1-9 don't add services underneath).
  const onListKeyDown = (e: KeyboardEvent) => {
    e.stopPropagation();
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setActive((i) => (i + 1) % barbers.length);
        break;
      case 'ArrowUp':
        e.preventDefault();
        setActive((i) => (i - 1 + barbers.length) % barbers.length);
        break;
      case 'Home':
        e.preventDefault();
        setActive(0);
        break;
      case 'End':
        e.preventDefault();
        setActive(barbers.length - 1);
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
      default: {
        // Type-ahead: first barber whose name starts with the letter.
        if (e.key.length === 1) {
          const i = barbers.findIndex((b) => b.name.toLowerCase().startsWith(e.key.toLowerCase()));
          if (i >= 0) setActive(i);
        }
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

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        data-flash={flashId}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-label={`${label}: ${selected?.name ?? 'none'}`}
        onClick={() => (open ? close() : openMenu())}
        onKeyDown={onButtonKeyDown}
        className={`-ml-1.5 flex items-center gap-1 rounded-[8px] py-0.5 pl-1.5 pr-1 text-[12.5px] text-text-muted transition-colors hover:bg-surface-muted hover:text-text ${
          open ? 'bg-surface-muted text-text' : ''
        }`}
      >
        {selected?.name ?? 'Choose barber'}
        <ChevronDownIcon width={14} height={14} className={`transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
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
            style={{ top: pos.top, left: pos.left, width: MENU_WIDTH }}
            className="menu-in fixed z-50 rounded-[12px] bg-surface p-1 shadow-pop outline-none"
          >
            {barbers.map((b, i) => {
              const isSelected = b.id === value;
              return (
                <li
                  key={b.id}
                  id={`${listId}-${i}`}
                  role="option"
                  aria-selected={isSelected}
                  onPointerEnter={() => setActive(i)}
                  onClick={() => pick(i)}
                  className={`flex h-9 cursor-default select-none items-center justify-between rounded-[8px] px-2.5 text-[13.5px] text-text ${
                    i === active ? 'bg-surface-muted' : ''
                  } ${isSelected ? 'font-medium' : ''}`}
                >
                  {b.name}
                  {isSelected && <CheckIcon width={16} height={16} />}
                </li>
              );
            })}
          </ul>,
          document.body,
        )}
    </>
  );
}
