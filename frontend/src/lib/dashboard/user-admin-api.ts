import { backendRequest, jsonInit, type BackendResult } from "@/lib/auth/backend";

export type ManagedRole = "SCORER" | "ORGANIZER";
export type AdminUser = {
  userId: number;
  displayName: string;
  email?: string;
  roles: ("PLAYER" | "SCORER" | "ORGANIZER" | "ADMIN")[];
};
export type AdminUserPage = {
  content: AdminUser[];
  page: number;
  totalElements: number;
  totalPages: number;
};

export function searchAdminUsers(q = ""): Promise<BackendResult<AdminUserPage>> {
  const search = new URLSearchParams({ q, size: "100" });
  return backendRequest(`/api/v1/admin/users?${search}`, {}, { authenticated: true });
}

export function updateAdminUserRoles(userId: number, roles: ManagedRole[]) {
  return backendRequest<AdminUser>(
    `/api/v1/admin/users/${userId}/roles`,
    jsonInit("PATCH", { roles }),
    { authenticated: true }
  );
}
