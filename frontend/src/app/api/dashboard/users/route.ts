import { revalidatePath } from "next/cache";
import { NextRequest, NextResponse } from "next/server";
import { assertSameOriginRequest } from "@/lib/auth/origin";
import { formValue, problemMessage } from "@/lib/auth/forms";
import { updateAdminUserRoles, type ManagedRole } from "@/lib/dashboard/user-admin-api";

export async function POST(request: NextRequest) {
  try { await assertSameOriginRequest(request); }
  catch { return NextResponse.json({ error: "Invalid request origin" }, { status: 403 }); }
  const data = await request.formData();
  const userId = Number(formValue(data, "userId"));
  const q = formValue(data, "q") ?? "";
  const roles: ManagedRole[] = [];
  if (data.get("SCORER") === "on") roles.push("SCORER");
  if (data.get("ORGANIZER") === "on") roles.push("ORGANIZER");
  const result = Number.isFinite(userId) ? await updateAdminUserRoles(userId, roles) : undefined;
  const url = new URL("/dashboard/users", request.url);
  if (q) url.searchParams.set("q", q);
  if (!result?.ok) url.searchParams.set("error", result ? problemMessage(result.error) ?? "Role update failed." : "User is missing.");
  else url.searchParams.set("success", `Roles updated for ${result.data.displayName}.`);
  revalidatePath("/dashboard/users");
  return NextResponse.redirect(url, { status: 303 });
}
