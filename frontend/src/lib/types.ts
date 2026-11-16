// Mirrors the backend DTOs (com.groomdesk.*Dtos). Money arrives as JSON numbers with 2 decimals.

export type Role = 'HQ_ADMIN' | 'FRANCHISE_OWNER' | 'OUTLET_MANAGER' | 'CASHIER';
export type PaymentMode = 'CASH' | 'UPI' | 'CARD';
export type ServiceCategory = 'HAIR' | 'BEARD' | 'FACIAL' | 'COMBO' | 'OTHER';

export interface UserProfile {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  franchiseId: number | null;
  franchiseName: string | null;
  outletId: number | null;
  outletCode: string | null;
  outletName: string | null;
  outletTaxRate: number | null;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: UserProfile;
}

export interface ServiceItem {
  id: number;
  code: string | null;
  name: string;
  category: ServiceCategory;
  price: number;
  durationMin: number;
}

export interface Barber {
  id: number;
  name: string;
}

export interface OutletRef {
  id: number;
  code: string;
  name: string;
}

export interface Customer {
  id: number;
  phone: string;
  name: string;
  notes: string | null;
  preferredBarber: { id: number; name: string; outletId: number; outletCode: string } | null;
  firstVisitOutlet: OutletRef;
  createdAt: string;
}

export interface Visit {
  billId: number;
  billNo: string;
  outlet: OutletRef;
  visitedAt: string;
  total: number;
  services: string[];
  barbers: string[];
}

export interface CustomerLookup {
  customer: Customer;
  totalVisits: number;
  totalSpend: number;
  lastVisits: Visit[];
}

export interface CreateBillRequest {
  customerId: number;
  items: { serviceId: number; barberId: number }[];
  discount: { type: 'FLAT' | 'PERCENT'; value: number } | null;
  paymentMode: PaymentMode;
}

export interface BillItem {
  id: number;
  serviceId: number;
  serviceName: string;
  barberId: number;
  barberName: string;
  price: number;
  commissionAmount: number;
}

export interface Bill {
  id: number;
  billNo: string;
  createdAt: string;
  outletId: number;
  outletCode: string;
  outletName: string;
  outletArea: string | null;
  customerId: number;
  customerName: string;
  customerPhone: string;
  cashierName: string;
  items: BillItem[];
  subtotal: number;
  discount: number;
  taxableAmount: number;
  taxRate: number;
  taxAmount: number;
  total: number;
  paymentMode: PaymentMode;
}
