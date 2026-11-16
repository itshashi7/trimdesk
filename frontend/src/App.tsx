import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { canBill, homePath, useAuth } from './lib/auth';
import { BillingPage } from './pages/BillingPage';
import { LoginPage } from './pages/LoginPage';
import { OverviewPage } from './pages/OverviewPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user ? <>{children}</> : <Navigate to="/login" replace />;
}

export function App() {
  const { user } = useAuth();
  const home = user ? homePath(user.role) : '/login';

  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to={home} replace /> : <LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route
          path="/billing"
          element={user && canBill(user.role) ? <BillingPage /> : <Navigate to={home} replace />}
        />
        <Route
          path="/overview"
          element={user && user.role !== 'CASHIER' ? <OverviewPage /> : <Navigate to={home} replace />}
        />
      </Route>
      <Route path="*" element={<Navigate to={home} replace />} />
    </Routes>
  );
}
