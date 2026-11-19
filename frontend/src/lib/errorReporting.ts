import { log } from './log';

/**
 * Sends browser crashes to the backend (POST /api/client-errors) so they appear in the server logs
 * next to API requests. A customer's browser console is otherwise invisible to us.
 * At most 5 reports per page load, and the same message is never sent twice.
 */

const MAX_REPORTS = 5;
const sent = new Set<string>();
let lastApiTicket: string | null = null;

/** Called by the API layer: the ticket of the most recent failed request, sent along with a crash. */
export function rememberFailedTicket(ticket: string | null) {
  if (ticket) lastApiTicket = ticket;
}

export type ClientErrorKind = 'render' | 'uncaught' | 'promise';

function describe(error: unknown): { message: string; stack?: string } {
  if (error instanceof Error) return { message: error.message || error.name, stack: error.stack };
  if (typeof error === 'string') return { message: error };
  try {
    return { message: JSON.stringify(error) };
  } catch {
    return { message: String(error) };
  }
}

/**
 * Reports one crash. Resolves to the server's ticket number (to show on the error screen), or null
 * when the report was skipped or could not be sent. Never throws.
 */
export async function reportClientError(kind: ClientErrorKind, error: unknown, extraStack?: string): Promise<string | null> {
  const { message, stack } = describe(error);
  const key = `${kind}:${message}`;
  if (sent.has(key) || sent.size >= MAX_REPORTS) return null;
  sent.add(key);

  const body = {
    kind,
    message: message.slice(0, 500),
    stack: [stack, extraStack].filter(Boolean).join('\n').slice(0, 2000) || undefined,
    page: window.location.pathname.slice(0, 200),
    relatedTicket: lastApiTicket ?? undefined,
  };
  try {
    const res = await fetch(`${import.meta.env.VITE_API_URL ?? ''}/api/client-errors`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      keepalive: true,
    });
    const ticket = res.headers.get('X-Ticket-Id');
    log.warn(`Reported ${kind} error to the server`, ticket ? `(ticket ${ticket})` : `(status ${res.status})`);
    return ticket;
  } catch (e) {
    log.warn('Could not report the error to the server', e);
    return null;
  }
}
