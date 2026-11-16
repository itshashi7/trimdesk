import { money } from '../../lib/format';
import type { ServiceItem } from '../../lib/types';
import { Keycap } from '../Keycap';
import { ServiceIcon } from './ServiceIcon';

interface Props {
  services: ServiceItem[];
  disabled: boolean;
  onAdd: (service: ServiceItem) => void;
}

export const serviceFlashId = (s: ServiceItem) => `svc-${s.id}`;

/** Central menu. Shortcut services (1-9) first, then the rest by name. */
export function ServiceGrid({ services, disabled, onAdd }: Props) {
  return (
    <section aria-labelledby="services-heading" className="flex min-h-0 flex-col rounded-card border border-border bg-surface">
      <div className="flex items-baseline justify-between px-4 pb-2 pt-3">
        <h2 id="services-heading" className="text-[13px] font-semibold text-text">Services</h2>
        <span className="text-2xs text-text-muted">Press a number to add</span>
      </div>
      <div className="grid min-h-0 grid-cols-4 gap-2 overflow-y-auto px-3 pb-3">
        {services.map((s) => (
          <button
            key={s.id}
            type="button"
            data-flash={serviceFlashId(s)}
            disabled={disabled}
            onClick={() => onAdd(s)}
            className="group flex h-[53px] flex-col justify-between rounded-[10px] border border-border bg-surface px-2.5 py-2 text-left transition-colors hover:border-border-strong hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50"
          >
            <span className="flex items-start justify-between gap-1.5">
              <span className="line-clamp-1 text-[13px] font-medium leading-tight text-text">{s.name}</span>
              {s.code && <Keycap className="shrink-0">{s.code}</Keycap>}
            </span>
            <span className="flex items-end justify-between">
              <span className="text-[12.5px] text-text-muted tabular">{money(s.price)}</span>
              <ServiceIcon
                service={s}
                width={18}
                height={18}
                className="-mb-0.5 -mr-0.5 shrink-0 text-text-muted transition-colors group-hover:text-text"
              />
            </span>
          </button>
        ))}
      </div>
    </section>
  );
}
