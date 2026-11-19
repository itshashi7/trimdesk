import { Component, type ErrorInfo, type ReactNode } from 'react';
import { RotateCcw } from 'lucide-react';
import { reportClientError } from '../lib/errorReporting';
import { log } from '../lib/log';

interface State {
  failed: boolean;
  /** Server ticket for the crash report, once it has been sent. */
  ticket: string | null;
  reporting: boolean;
}

/**
 * Catches a crash while drawing any page. Instead of a blank white screen the user gets a short
 * message, a reference to quote, and a reload button; the crash is reported to the server so it
 * appears in the logs under that reference.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false, ticket: null, reporting: false };

  static getDerivedStateFromError(): Partial<State> {
    return { failed: true, reporting: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    log.error('Page crashed', error, info.componentStack);
    void reportClientError('render', error, info.componentStack ?? undefined).then((ticket) =>
      this.setState({ ticket, reporting: false }),
    );
  }

  render() {
    if (!this.state.failed) return this.props.children;
    const { ticket, reporting } = this.state;
    return (
      <main className="app-min-height flex items-center justify-center bg-bg px-5 py-12">
        <div role="alert" className="w-full max-w-[400px] rounded-card border border-border bg-surface p-6 text-center">
          <h1 className="text-[18px] font-semibold tracking-[-0.01em] text-text">Something went wrong</h1>
          <p className="mt-2 text-[14px] leading-relaxed text-text-muted">
            This screen hit an unexpected error. Reloading usually fixes it; nothing you saved has been lost.
          </p>
          <p className="mt-4 rounded-[10px] bg-surface-muted px-3 py-2 text-[13px] text-text-muted">
            {reporting ? (
              'Reporting the problem…'
            ) : ticket ? (
              <>
                Reference <span className="font-semibold text-text tabular">{ticket}</span>
              </>
            ) : (
              'The problem could not be reported automatically.'
            )}
          </p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-5 inline-flex h-10 items-center gap-2 rounded-[10px] bg-accent px-4 text-[13.5px] font-medium text-accent-contrast"
          >
            <RotateCcw size={16} strokeWidth={1.75} aria-hidden />
            Reload
          </button>
        </div>
      </main>
    );
  }
}
