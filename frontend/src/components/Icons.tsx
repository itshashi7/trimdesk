// Authored line icons: 20px box, 1.6 stroke, round caps. One family for the whole app.
import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement>;

const base = {
  width: 18,
  height: 18,
  viewBox: '0 0 20 20',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
};

export const SearchIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <circle cx="9" cy="9" r="5.5" />
    <path d="m13.2 13.2 3.3 3.3" />
  </svg>
);

export const PrintIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M6 7.5V3.5h8v4" />
    <rect x="3" y="7.5" width="14" height="6.5" rx="1.5" />
    <path d="M6 12h8v4.5H6z" />
  </svg>
);

export const ChatIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M4 15.5 5 12.6A6.5 6.5 0 1 1 7.6 15l-3.6.5Z" />
  </svg>
);

export const LogoutIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M8 4H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3" />
    <path d="M12.5 13.5 16 10l-3.5-3.5M16 10H8" />
  </svg>
);

/** Pencil over a box open at the top-right corner. */
export const EditIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M9 4H5.5A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16h9a1.5 1.5 0 0 0 1.5-1.5V11" />
    <path d="M14.2 3.8a1.5 1.5 0 0 1 2.1 2.1L10 12.2l-2.8.7.7-2.8 6.3-6.3Z" />
  </svg>
);

export const ChevronDownIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="m6 8 4 4 4-4" />
  </svg>
);

export const CalendarIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <rect x="3.5" y="4.5" width="13" height="12" rx="2" />
    <path d="M3.5 8.5h13M7 3v3M13 3v3" />
  </svg>
);

export const ChevronLeftIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="m12 5-5 5 5 5" />
  </svg>
);

export const ChevronRightIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="m8 5 5 5-5 5" />
  </svg>
);

export const CloseIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="m5.5 5.5 9 9m0-9-9 9" />
  </svg>
);

export const CheckIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="m4.5 10.5 3.5 3.5 7.5-8" />
  </svg>
);

export const StoreIcon = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M3.5 8 5 4h10l1.5 4" />
    <path d="M3.5 8h13v1a2.2 2.2 0 0 1-4.3.6 2.2 2.2 0 0 1-4.4 0A2.2 2.2 0 0 1 3.5 9V8Z" />
    <path d="M4.5 11v5h11v-5" />
  </svg>
);
