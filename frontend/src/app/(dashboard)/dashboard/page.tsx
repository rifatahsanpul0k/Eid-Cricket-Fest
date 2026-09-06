import type { Metadata } from "next";
import Link from "next/link";
import { ShieldAlertIcon } from "lucide-react";

import { buttonVariants } from "@/components/ui/button";
import { getSession } from "@/lib/auth/session";
import { searchOrganizerPayments, searchOrganizerRegistrations } from "@/lib/dashboard/api";
import {
  getDashboardRoleLabel,
  hasAdminAccess,
  hasOrganizerAccess,
} from "@/lib/dashboard/roles";
import { getCurrentEditionData } from "@/lib/tournament/current-edition";
import { cn } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Dashboard",
};

export default async function DashboardPage() {
  const [session, currentEdition] = await Promise.all([
    getSession(),
    getCurrentEditionData(),
  ]);

  if (!hasOrganizerAccess(session)) {
    return <ForbiddenDashboard />;
  }

  const [registrations, payments] =
    currentEdition.status === "ready"
      ? await Promise.all([
          searchOrganizerRegistrations(currentEdition.edition.id, {
            page: 0,
            size: 1,
          }),
          searchOrganizerPayments(currentEdition.edition.id, {
            page: 0,
            size: 1,
          }),
        ])
      : [undefined, undefined];

  if (registrations && !registrations.ok && registrations.status === 403) {
    return <ForbiddenDashboard />;
  }

  return (
    <main className="flex-1">
      <DashboardHeader
        roleLabel={getDashboardRoleLabel(session)}
        description={
          currentEdition.status === "ready"
            ? `${currentEdition.edition.name} · ${formatStatusLabel(currentEdition.edition.status)}`
            : `${currentEdition.message} Set up the tournament before beginning tournament operations.`
        }
        title="Tournament Dashboard"
      />
      <section className="mx-auto grid w-full max-w-5xl gap-8 px-4 py-8 sm:px-6 lg:px-8">
        <DashboardSection title="Tournament Setup">
          <DashboardCard
            actionLabel="Manage tournament"
            description="Create and configure tournament editions, registration settings, and tournament lifecycle status."
            eyebrow="Tournament setup"
            href="/dashboard/tournament"
            label="Tournament"
          />
        </DashboardSection>

        <DashboardSection title="Player Management">
          {hasAdminAccess(session) ? (
            <DashboardCard
              actionLabel="Manage users"
              description="Grant or revoke scorer and organizer access."
              eyebrow="Access control"
              href="/dashboard/users"
              label="Users"
            />
          ) : null}
          <DashboardCard
            actionLabel="Review registrations"
            count={registrations?.ok ? registrations.data.totalElements : undefined}
            description="Review players who applied for the current tournament and approve or reject their registration."
            eyebrow="Player review"
            href="/dashboard/registrations"
            label="Registrations"
          />
          <DashboardCard
            actionLabel="Review payments"
            count={payments?.ok ? payments.data.totalElements : undefined}
            description="Check player registration payments and verify or reject submitted payments."
            eyebrow="Finance"
            href="/dashboard/payments"
            label="Payments"
          />
        </DashboardSection>

        <DashboardSection title="Team Management">
          <DashboardCard
            actionLabel="Manage teams"
            description="Create tournament teams, add them to the current edition, assign captains, and view team setup."
            eyebrow="Team management"
            href="/dashboard/teams"
            label="Teams"
          />
          <DashboardCard
            actionLabel="Open draft"
            description="Run the player draft and assign approved players to tournament teams."
            eyebrow="Player selection"
            href="/dashboard/draft"
            label="Draft"
          />
        </DashboardSection>

        <DashboardSection title="Match Management">
          <DashboardCard
            actionLabel="Manage fixtures"
            description="Create venues and generate the tournament match schedule."
            eyebrow="Match setup"
            href="/dashboard/fixtures"
            label="Fixtures"
          />
          <DashboardCard
            actionLabel="Manage matches"
            description="Prepare matches by scheduling them, assigning scorers, selecting playing XIs, and recording the toss."
            eyebrow="Match operations"
            href="/dashboard/matches"
            label="Matches"
          />
        </DashboardSection>

        <DashboardSection title="Match Day">
          <DashboardCard
            actionLabel="Open scorer console"
            description="Open the scoring area for live tournament matches."
            eyebrow="Live scoring"
            href="/scorer"
            label="Scorer Console"
          />
        </DashboardSection>
      </section>
    </main>
  );
}

function DashboardHeader({
  description,
  roleLabel,
  title,
}: {
  description: string;
  roleLabel: string;
  title: string;
}) {
  return (
    <section className="border-b border-white/10 bg-background">
      <div className="mx-auto w-full max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
        <p className="font-mono text-xs uppercase text-primary">{roleLabel}</p>
        <h1 className="mt-3 font-heading text-4xl font-bold uppercase tracking-normal">
          {title}
        </h1>
        <p className="mt-3 text-sm text-muted-foreground">{description}</p>
      </div>
    </section>
  );
}

function DashboardSection({
  children,
  title,
}: {
  children: React.ReactNode;
  title: string;
}) {
  return (
    <section>
      <h2 className="font-mono text-xs uppercase text-primary">{title}</h2>
      <div className="mt-3 grid gap-4 md:grid-cols-2">{children}</div>
    </section>
  );
}

function DashboardCard({
  actionLabel,
  count,
  description,
  eyebrow,
  href,
  label,
}: {
  actionLabel: string;
  count?: number;
  description: string;
  eyebrow: string;
  href: string;
  label: string;
}) {
  return (
    <article className="rounded-sm border border-white/10 bg-card p-5">
      <p className="font-mono text-xs uppercase text-muted-foreground">
        {count === undefined ? eyebrow : `${eyebrow} · ${count} total`}
      </p>
      <h2 className="mt-3 font-heading text-2xl font-bold uppercase tracking-normal">
        {label}
      </h2>
      <p className="mt-3 text-sm text-muted-foreground">{description}</p>
      <Link className={cn(buttonVariants(), "mt-5")} href={href}>
        {actionLabel}
      </Link>
    </article>
  );
}

function formatStatusLabel(status?: string) {
  return (
    status
      ?.toLowerCase()
      .split("_")
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(" ") ?? "Status unavailable"
  );
}

function ForbiddenDashboard() {
  return (
    <main className="flex-1">
      <section className="mx-auto grid min-h-[52vh] w-full max-w-2xl place-items-center px-4 py-12 text-center sm:px-6 lg:px-8">
        <div>
          <ShieldAlertIcon className="mx-auto size-10 text-destructive" />
          <h1 className="mt-4 font-heading text-3xl font-bold uppercase tracking-normal">
            Dashboard access denied
          </h1>
          <p className="mt-3 text-sm text-muted-foreground">
            Organizer or admin access is required for registration operations.
          </p>
          <Link
            className={cn(buttonVariants({ variant: "outline" }), "mt-6")}
            href="/account"
          >
            Back to account
          </Link>
        </div>
      </section>
    </main>
  );
}
