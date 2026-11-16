import { useAuth } from '../lib/auth';

/** Placeholder until the outlet report (step 5) and HQ dashboard / royalty report (step 6) are built. */
export function OverviewPage() {
  const { user } = useAuth();
  return (
    <main className="flex flex-1 items-center justify-center px-6 py-16">
      <div className="max-w-[420px] text-center">
        <h1 className="text-[18px] font-semibold text-text">Reports are not built yet</h1>
        <p className="mt-2 text-[14px] leading-relaxed text-text-muted">
          {user?.role === 'HQ_ADMIN'
            ? 'The all-outlet dashboard and royalty report come in the next build steps.'
            : user?.role === 'FRANCHISE_OWNER'
              ? `Reports for ${user.franchiseName ?? 'your outlets'} come in the next build steps.`
              : 'The outlet day report comes in the next build step. Billing is ready now.'}
        </p>
      </div>
    </main>
  );
}
