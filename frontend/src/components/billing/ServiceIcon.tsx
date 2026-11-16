// Service pictograms: same family as components/Icons (20px box, 1.6 stroke, round caps).
// Picked by keyword, then by category, so new services from the real rate card still get one.
import type { SVGProps } from 'react';
import type { ServiceItem } from '../../lib/types';

type IconProps = SVGProps<SVGSVGElement>;

const base = {
  width: 20,
  height: 20,
  viewBox: '0 0 20 20',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
};

const Scissors = (p: IconProps) => (
  <svg {...base} {...p}>
    <circle cx="5.5" cy="6" r="2.25" />
    <circle cx="5.5" cy="14" r="2.25" />
    <path d="M7.4 7.3 16 14M7.4 12.7 16 6" />
  </svg>
);

const Clipper = (p: IconProps) => (
  <svg {...base} {...p}>
    <rect x="7" y="6.5" width="6" height="10.5" rx="2" />
    <path d="M6 3.5h8M7.5 3.5v3M10 3.5v3M12.5 3.5v3M10 10v2" />
  </svg>
);

const Razor = (p: IconProps) => (
  <svg {...base} {...p}>
    <rect x="4" y="3.5" width="12" height="3.5" rx="1.2" />
    <path d="M10 7v9M8.5 16.5h3" />
  </svg>
);

const Moustache = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M2.5 8.5c.8 3 4.2 4 6.3 2.1L10 9.4l1.2 1.2c2.1 1.9 5.5.9 6.3-2.1.5 4.6-4.1 7-7 4.4l-.5-.5-.5.5c-2.9 2.6-7.5.2-7-4.4Z" />
  </svg>
);

const Drop = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M10 3.5c2.8 3.4 4.5 6 4.5 8.2a4.5 4.5 0 0 1-9 0c0-2.2 1.7-4.8 4.5-8.2Z" />
  </svg>
);

const Shower = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M10 3.5V5M4.5 10a5.5 5.5 0 0 1 11 0Z" />
    <path d="M6.5 13.5v.01M10 14.5v.01M13.5 13.5v.01M8 16.5v.01M12 16.5v.01" />
  </svg>
);

const Hand = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M7 11V5.5a1 1 0 0 1 2 0V10M9 9.5v-5a1 1 0 0 1 2 0V10M11 10V5.5a1 1 0 0 1 2 0V11" />
    <path d="M13 11V8a1 1 0 0 1 2 0v4c0 3-2 4.8-5 4.8-2 0-3.3-1-4.3-2.6L4.2 11.8a1 1 0 0 1 1.6-1.1L7 12" />
  </svg>
);

const Leaf = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M4.5 15.5C4.5 8.5 8.5 4.5 16 4.5c0 7-4 11-11.5 11Z" />
    <path d="M4.5 15.5 11 9" />
  </svg>
);

const Smile = (p: IconProps) => (
  <svg {...base} {...p}>
    <circle cx="10" cy="10" r="6.5" />
    <path d="M7.5 11.5a3 3 0 0 0 5 0M8 8.3v.01M12 8.3v.01" />
  </svg>
);

const Sparkle = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="m9 3.5 1.4 4.1 4.1 1.4-4.1 1.4L9 14.5l-1.4-4.1L3.5 9l4.1-1.4Z" />
    <path d="M15 13.5v3M13.5 15h3" />
  </svg>
);

const Stack = (p: IconProps) => (
  <svg {...base} {...p}>
    <path d="M10 3.5 16.5 7 10 10.5 3.5 7Z" />
    <path d="m3.5 10.5 6.5 3.5 6.5-3.5M3.5 14l6.5 3.5 6.5-3.5" />
  </svg>
);

const Gift = (p: IconProps) => (
  <svg {...base} {...p}>
    <rect x="3.5" y="8" width="13" height="3" rx="1" />
    <path d="M4.5 11v5.5h11V11M10 8v8.5" />
    <path d="M10 8C8.6 5 5.4 5 5.8 6.9 6.1 7.9 7.7 8 10 8Zm0 0c1.4-3 4.6-3 4.2-1.1-.3 1-1.9 1.1-4.2 1.1Z" />
  </svg>
);

/** First matching keyword wins; order matters ("Beard Colour" is a colour, not a trim). */
const KEYWORDS: [RegExp, (p: IconProps) => JSX.Element][] = [
  [/package/i, Gift],
  [/moustache|mustache/i, Moustache],
  [/shave/i, Razor],
  [/colou?r|dye/i, Drop],
  [/wash/i, Shower],
  [/massage/i, Hand],
  [/spa/i, Leaf],
  [/kid|child/i, Smile],
  [/fade|clipper/i, Clipper],
  [/facial|mask|de-?tan|cleanup/i, Sparkle],
];

const BY_CATEGORY: Record<ServiceItem['category'], (p: IconProps) => JSX.Element> = {
  HAIR: Scissors,
  BEARD: Clipper,
  FACIAL: Sparkle,
  COMBO: Stack,
  OTHER: Leaf,
};

export function ServiceIcon({ service, ...props }: { service: ServiceItem } & IconProps) {
  const fromName = service.category === 'COMBO' && !/package/i.test(service.name)
    ? undefined
    : KEYWORDS.find(([pattern]) => pattern.test(service.name))?.[1];
  const Icon = fromName ?? BY_CATEGORY[service.category] ?? Leaf;
  return <Icon {...props} />;
}
