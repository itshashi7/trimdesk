// App icons: Lucide's rounded line set (the Firecorp reference style), one stroke weight everywhere.
// Wrapped under app names so screens never import the library directly.
import {
  BadgePercent,
  Calendar,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Download,
  LayoutDashboard,
  LogOut,
  MessageCircle,
  Printer,
  ReceiptText,
  Scissors,
  Search,
  SquarePen,
  Store,
  Users,
  X,
  type LucideIcon,
  type LucideProps,
} from 'lucide-react';

const STROKE = 1.75;
const make = (Icon: LucideIcon) => (p: LucideProps) => (
  <Icon size={18} strokeWidth={STROKE} aria-hidden {...p} />
);

export const SearchIcon = make(Search);
export const PrintIcon = make(Printer);
export const ChatIcon = make(MessageCircle);
export const LogoutIcon = make(LogOut);
export const EditIcon = make(SquarePen);
export const DownloadIcon = make(Download);
export const CalendarIcon = make(Calendar);
export const ChevronDownIcon = make(ChevronDown);
export const ChevronLeftIcon = make(ChevronLeft);
export const ChevronRightIcon = make(ChevronRight);
export const CloseIcon = make(X);
export const CheckIcon = make(Check);
export const StoreIcon = make(Store);

// Navigation
export const DashboardIcon = make(LayoutDashboard);
export const BillingIcon = make(ReceiptText);
export const RoyaltyIcon = make(BadgePercent);
export const CustomersIcon = make(Users);
export const BrandIcon = make(Scissors);
