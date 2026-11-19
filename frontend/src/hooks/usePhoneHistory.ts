import { useRef, useState } from 'react';

const MAX_ENTRIES = 20;

function read(key: string): string[] {
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((p): p is string => typeof p === 'string') : [];
  } catch {
    return [];
  }
}

function write(key: string, entries: string[]) {
  try {
    localStorage.setItem(key, JSON.stringify(entries));
  } catch {
    // Private mode or storage full: history just won't survive a reload.
  }
}

/**
 * Shell-style history for the phone field: Up walks back through numbers already looked up,
 * Down walks forward and finally restores what was being typed. Kept per signed-in user on
 * the counter machine, newest last, at most 20 numbers.
 */
export function usePhoneHistory(scope: string | number | undefined) {
  const key = `trimdesk.phoneHistory.${scope ?? 'anon'}`;
  const [entries, setEntries] = useState<string[]>(() => read(key));
  /** Position in `entries` while browsing; null while the cashier is typing. */
  const cursor = useRef<number | null>(null);
  /** What was in the field before browsing started, restored when Down passes the newest entry. */
  const draft = useRef('');

  /** Record a number that was just looked up (moves it to the newest slot if already there). */
  function remember(phone: string) {
    cursor.current = null;
    setEntries((prev) => {
      const next = [...prev.filter((p) => p !== phone), phone].slice(-MAX_ENTRIES);
      write(key, next);
      return next;
    });
  }

  /** Typing ends browsing, like editing a recalled command in a shell. */
  function reset() {
    cursor.current = null;
  }

  /**
   * Move through history. Returns the value to show, or null when nothing should change
   * (no history, Down while not browsing, or Up at the oldest entry).
   */
  function step(direction: 'older' | 'newer', current: string): string | null {
    if (entries.length === 0) return null;
    if (cursor.current === null) {
      if (direction === 'newer') return null;
      draft.current = current;
      cursor.current = entries.length - 1;
      return entries[cursor.current];
    }
    const next = cursor.current + (direction === 'older' ? -1 : 1);
    if (next < 0) return null;
    if (next >= entries.length) {
      cursor.current = null;
      return draft.current;
    }
    cursor.current = next;
    return entries[next];
  }

  return { remember, reset, step, count: entries.length };
}
