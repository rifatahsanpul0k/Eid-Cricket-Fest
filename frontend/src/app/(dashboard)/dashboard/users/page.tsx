import type { Metadata } from "next";
import { ShieldAlertIcon } from "lucide-react";
import { getSession } from "@/lib/auth/session";
import { hasAdminAccess } from "@/lib/dashboard/roles";
import { searchAdminUsers } from "@/lib/dashboard/user-admin-api";
import { Button } from "@/components/ui/button";

export const metadata: Metadata = { title: "Users | Dashboard" };

export default async function UsersPage({ searchParams }: { searchParams: Promise<{ q?: string; success?: string; error?: string }> }) {
  const [session, params] = await Promise.all([getSession(), searchParams]);
  if (!hasAdminAccess(session)) return <Denied />;
  const result = await searchAdminUsers(params.q ?? "");
  return (
    <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-10 sm:px-6 lg:px-8">
      <p className="font-mono text-xs uppercase text-primary">Administrator</p>
      <h1 className="mt-3 font-heading text-4xl font-bold uppercase">User Management</h1>
      <p className="mt-3 text-sm text-muted-foreground">Grant or revoke operational roles. Role changes take effect after the user signs in again.</p>
      <form className="mt-7 flex gap-3" method="get">
        <label className="sr-only" htmlFor="user-search">Search users</label>
        <input className="min-h-10 flex-1 rounded-sm border border-white/15 bg-background px-3" defaultValue={params.q} id="user-search" name="q" placeholder="Search by name or email" />
        <Button type="submit">Search</Button>
      </form>
      {params.success ? <p className="mt-4 text-sm text-emerald-400" role="status">{params.success}</p> : null}
      {params.error ? <p className="mt-4 text-sm text-destructive" role="alert">{params.error}</p> : null}
      {!result.ok ? <p className="mt-6 text-destructive">{result.error.detail ?? result.error.title}</p> : (
        <div className="mt-6 grid gap-3">
          {result.data.content.map((user) => (
            <form action="/api/dashboard/users" className="grid gap-4 rounded-sm border border-white/10 bg-card p-5 md:grid-cols-[1fr_auto_auto] md:items-center" method="post" key={user.userId}>
              <input name="userId" type="hidden" value={user.userId} /><input name="q" type="hidden" value={params.q ?? ""} />
              <div><h2 className="font-heading text-xl font-bold uppercase">{user.displayName}</h2><p className="text-sm text-muted-foreground">{user.email ?? "No email"}</p><p className="mt-1 font-mono text-xs text-muted-foreground">{user.roles.join(" · ")}</p></div>
              <div className="flex gap-4">
                <label className="flex items-center gap-2"><input defaultChecked={user.roles.includes("SCORER")} name="SCORER" type="checkbox" /> Scorer</label>
                <label className="flex items-center gap-2"><input defaultChecked={user.roles.includes("ORGANIZER")} name="ORGANIZER" type="checkbox" /> Organizer</label>
              </div>
              <Button type="submit">Save roles</Button>
            </form>
          ))}
        </div>
      )}
    </main>
  );
}

function Denied() { return <main className="grid min-h-[52vh] place-items-center text-center"><div><ShieldAlertIcon className="mx-auto size-10 text-destructive"/><h1 className="mt-4 font-heading text-3xl font-bold uppercase">User management access denied</h1><p className="mt-3 text-sm text-muted-foreground">Administrator access is required.</p></div></main>; }
