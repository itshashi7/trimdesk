import type { Barber } from '../../lib/types';
import { MenuSelect } from '../MenuSelect';

interface Props {
  barbers: Barber[];
  value: number;
  onChange: (barberId: number) => void;
  /** Accessible name, e.g. "Barber for Haircut". */
  label: string;
  flashId?: string;
}

/** Barber picker on a bill line: the shared rounded menu in its quiet inline style. */
export function BarberSelect({ barbers, value, onChange, label, flashId }: Props) {
  return (
    <MenuSelect
      options={barbers.map((b) => ({ value: b.id, label: b.name }))}
      value={value}
      onChange={onChange}
      label={label}
      flashId={flashId}
      placeholder="Choose barber"
    />
  );
}
