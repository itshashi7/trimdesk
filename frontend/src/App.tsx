import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { canBill, canViewDashboard, canViewReports, homePath, useAuth } from './lib/auth';
import { BillingPage } from './pages/BillingPage';
import { CustomerProfilePage } from './pages/CustomerProfilePage';
import { CustomersPage } from './pages/CustomersPage';
import { LoginPage } from './pages/LoginPage';
import { OutletReportPage } from './pages/OutletReportPage';
import { RoyaltyPage } from './pages/RoyaltyPage';

// The dashboard pulls in the charting library; load it only when someone opens it,
// so the cashier's billing screen stays small and fast.
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user ? <>{children}</> : <Navigate to="/login" replace />;
}

export function App() {
  const { user } = useAuth();
  const home = user ? homePath(user.role) : '/login';
  const reports = user !== null && canViewReports(user.role);
  const dashboard = user !== null && canViewDashboard(user.role);

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
          path="/dashboard"
          element={
            dashboard ? (
              <Suspense fallback={null}>
                <DashboardPage />
              </Suspense>
            ) : (
              <Navigate to={home} replace />
            )
          }
        />
        <Route path="/royalty" element={dashboard ? <RoyaltyPage /> : <Navigate to={home} replace />} />
        <Route path="/reports/outlet" element={reports ? <OutletReportPage /> : <Navigate to={home} replace />} />
        <Route path="/customers" element={reports ? <CustomersPage /> : <Navigate to={home} replace />} />
        <Route path="/customers/:id" element={reports ? <CustomerProfilePage /> : <Navigate to={home} replace />} />
      </Route>
      <Route path="*" element={<Navigate to={home} replace />} />
    </Routes>
  );
}
