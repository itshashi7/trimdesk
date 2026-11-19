import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { ErrorBoundary } from './components/ErrorBoundary';
import { ToastProvider } from './components/Toast';
import { ApiError } from './lib/api';
import { reportClientError } from './lib/errorReporting';
import { log } from './lib/log';
import { AuthProvider } from './lib/auth';
import '@fontsource-variable/inter';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 5 * 60_000, retry: 1, refetchOnWindowFocus: false },
  },
});

// Errors outside React's drawing (event handlers, timers, forgotten promises) also reach the server
// logs. Failed API calls are skipped here: the server already logged them under their ticket.
window.addEventListener('error', (event) => {
  log.error('Uncaught error', event.error ?? event.message);
  void reportClientError('uncaught', event.error ?? event.message);
});
window.addEventListener('unhandledrejection', (event) => {
  if (event.reason instanceof ApiError) return;
  log.error('Unhandled promise rejection', event.reason);
  void reportClientError('promise', event.reason);
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider>
            <ErrorBoundary>
              <App />
            </ErrorBoundary>
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
