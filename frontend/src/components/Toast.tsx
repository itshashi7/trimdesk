import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { CheckIcon, CloseIcon } from './Icons';

type Tone = 'success' | 'error';
interface ToastState {
  id: number;
  tone: Tone;
  message: string;
}

const ToastContext = createContext<(message: string, tone?: Tone) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState | null>(null);
  const timer = useRef<number>();

  const show = useCallback((message: string, tone: Tone = 'success') => {
    window.clearTimeout(timer.current);
    setToast({ id: Date.now(), tone, message });
    timer.current = window.setTimeout(() => setToast(null), tone === 'error' ? 6000 : 3500);
  }, []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-14 z-50 flex justify-center px-4">
        {toast && (
          <div
            key={toast.id}
            role="status"
            className="toast-in pointer-events-auto flex items-center gap-2.5 rounded-xl bg-text px-4 py-2.5 text-[13px] font-medium text-surface shadow-pop"
          >
            <span className={toast.tone === 'success' ? 'text-success-surface' : 'text-danger-surface'}>
              {toast.tone === 'success' ? <CheckIcon /> : <CloseIcon />}
            </span>
            {toast.message}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
