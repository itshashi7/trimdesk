/**
 * Small console logger. In production only warnings and errors print, so the console stays clean
 * for customers; turn everything on in any browser with
 *   localStorage.setItem('trimdesk.debug', '1')   (and reload)
 * Development builds always print everything.
 */

function debugEnabled(): boolean {
  if (import.meta.env.DEV) return true;
  try {
    return localStorage.getItem('trimdesk.debug') === '1';
  } catch {
    return false;
  }
}

const PREFIX = '[TrimDesk]';

export const log = {
  debug: (...args: unknown[]) => {
    if (debugEnabled()) console.debug(PREFIX, ...args);
  },
  info: (...args: unknown[]) => {
    if (debugEnabled()) console.info(PREFIX, ...args);
  },
  warn: (...args: unknown[]) => console.warn(PREFIX, ...args),
  error: (...args: unknown[]) => console.error(PREFIX, ...args),
};
