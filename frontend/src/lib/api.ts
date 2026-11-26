import type {
  Barber,
  Bill,
  BillRow,
  CreateBillRequest,
  Customer,
  CustomerLookup,
  CustomerProfile,
  CustomerSearchResult,
  HqSummary,
  LoginResponse,
  RoyaltyReport,
  OutletDayReport,
  OutletSummary,
  ServiceItem,
} from './types';

import { rememberFailedTicket } from './errorReporting';
import { log } from './log';

const BASE = `${import.meta.env.VITE_API_URL ?? ''}/api`;

/** Response header with the server's ticket number for the request (see backend Tickets). */
const TICKET_HEADER = 'X-Ticket-Id';

/**
 * An API failure, carrying the server's problem-detail message, per-field errors and the ticket
 * number that finds this request in the server logs.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
    readonly ticket: string | null = null,
  ) {
    super(message);
  }
}

/**
 * Message to show for a failed request, with the ticket a cashier can quote:
 * "Could not save the bill. (Ref TD-20261120-7F3A9C)".
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) return fallback;
  return error.ticket ? `${error.message} (Ref ${error.ticket})` : error.message;
}

let currentToken: string | null = null;
let onUnauthorized: () => void = () => {};

export function setApiSession(token: string | null, unauthorizedHandler?: () => void) {
  currentToken = token;
  if (unauthorizedHandler) onUnauthorized = unauthorizedHandler;
}

async function request<T>(method: string, path: string, body?: unknown, auth = true): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && currentToken) headers.Authorization = `Bearer ${currentToken}`;

  let res: Response;
  const started = performance.now();
  try {
    res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch (e) {
    log.warn(`${method} ${path} failed: server unreachable`, e);
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
  }
  const ticket = res.headers.get(TICKET_HEADER);
  const ms = Math.round(performance.now() - started);

  if (res.status === 401 && auth) {
    onUnauthorized();
  }
  if (!res.ok) {
    let detail = res.statusText || 'Request failed';
    let fieldErrors: Record<string, string> = {};
    try {
      const problem = await res.json();
      detail = problem.detail ?? detail;
      fieldErrors = problem.errors ?? {};
    } catch {
      // non-JSON error body
    }
    // 404 is a normal answer (e.g. an unknown phone at the counter), not a problem worth a warning.
    (res.status === 404 ? log.debug : log.warn)(`${method} ${path} -> ${res.status} in ${ms} ms`, { ticket, detail });
    rememberFailedTicket(ticket);
    throw new ApiError(res.status, detail, fieldErrors, ticket);
  }
  log.debug(`${method} ${path} -> ${res.status} in ${ms} ms`, ticket);
  return (res.status === 204 ? undefined : await res.json()) as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>('POST', '/auth/login', { username, password }, false),
  services: () => request<ServiceItem[]>('GET', '/services'),
  barbers: (outletId: number) => request<Barber[]>('GET', `/outlets/${outletId}/barbers`),
  lookupCustomer: (phone: string) =>
    request<CustomerLookup>('GET', `/customers/lookup?phone=${encodeURIComponent(phone)}`),
  createCustomer: (phone: string, name: string) => request<Customer>('POST', '/customers', { phone, name }),
  updateCustomer: (id: number, changes: { phone?: string; name?: string; notes?: string }) =>
    request<Customer>('PATCH', `/customers/${id}`, changes),
  createBill: (body: CreateBillRequest) => request<Bill>('POST', '/bills', body),
  outlets: () => request<OutletSummary[]>('GET', '/outlets'),
  outletDay: (outletId: number, date: string) =>
    request<OutletDayReport>('GET', `/reports/outlet/${outletId}/day?date=${date}`),
  outletDayBills: (outletId: number, date: string) =>
    request<BillRow[]>('GET', `/reports/outlet/${outletId}/bills?date=${date}`),
  searchCustomers: (q: string) => request<CustomerSearchResult[]>('GET', `/customers/search?q=${encodeURIComponent(q)}`),
  customerProfile: (id: number) => request<CustomerProfile>('GET', `/customers/${id}`),
  hqSummary: (from: string, to: string) => request<HqSummary>('GET', `/reports/hq/summary?from=${from}&to=${to}`),
  royalty: (month: string) => request<RoyaltyReport>('GET', `/reports/royalty?month=${month}`),
};
