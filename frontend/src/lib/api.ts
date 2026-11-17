import type {
  Barber,
  Bill,
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

const BASE = `${import.meta.env.VITE_API_URL ?? ''}/api`;

/** An API failure, carrying the server's problem-detail message and per-field errors. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
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
  try {
    res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check that the backend is running.');
  }

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
    throw new ApiError(res.status, detail, fieldErrors);
  }
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
  searchCustomers: (q: string) => request<CustomerSearchResult[]>('GET', `/customers/search?q=${encodeURIComponent(q)}`),
  customerProfile: (id: number) => request<CustomerProfile>('GET', `/customers/${id}`),
  hqSummary: (from: string, to: string) => request<HqSummary>('GET', `/reports/hq/summary?from=${from}&to=${to}`),
  royalty: (month: string) => request<RoyaltyReport>('GET', `/reports/royalty?month=${month}`),
};
