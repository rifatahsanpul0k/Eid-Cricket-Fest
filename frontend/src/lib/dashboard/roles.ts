import type { Session } from "@/lib/auth/session";

const organizerRoles = new Set(["ORGANIZER", "ADMIN"]);
const scorerRoles = new Set(["SCORER", "ORGANIZER", "ADMIN"]);

export function hasOrganizerAccess(session?: Session) {
  return Boolean(
    session?.user?.roles?.some((role) => organizerRoles.has(role))
  );
}

export function getDashboardRoleLabel(session?: Session) {
  const roles = session?.user?.roles ?? [];

  if (roles.includes("ADMIN")) {
    return "Administrator";
  }

  if (roles.includes("ORGANIZER")) {
    return "Organizer";
  }

  return "Dashboard";
}

export function hasScorerAccess(session?: Session) {
  return Boolean(
    session?.user?.roles?.some((role) => scorerRoles.has(role))
  );
}

export function hasAdminAccess(session?: Session) {
  return Boolean(session?.user?.roles?.includes("ADMIN"));
}
