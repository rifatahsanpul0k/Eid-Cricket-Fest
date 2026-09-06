import { expect, test } from "@playwright/test";

import { loginViaUi, logoutViaUi, registerPlayerViaUi } from "./helpers/auth";
import { requireLiveE2EEnvironment } from "./helpers/data";
import { expectProtectedRedirect } from "./helpers/navigation";

test.describe.serial("security acceptance", () => {
  test.beforeEach(async () => {
    await requireLiveE2EEnvironment();
  });

  test("anonymous protected deep links redirect to login", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText("Eid Cricket Fest").first()).toBeVisible();
    await expectProtectedRedirect(page, "/account");
    await expectProtectedRedirect(page, "/dashboard");
    await expectProtectedRedirect(page, "/scorer");
    await expectProtectedRedirect(page, "/dashboard/tournament");
    await expectProtectedRedirect(page, "/dashboard/matches/1");
    await expectProtectedRedirect(page, "/scorer/matches/1");
    await expectProtectedRedirect(page, "/account/team");
  });

  test("admin provisions and revokes scorer and organizer roles through the UI", async ({ page }) => {
    const scorer = await registerPlayerViaUi(page, "E2E Dedicated Scorer");
    const organizer = await registerPlayerViaUi(page, "E2E Dedicated Organizer");
    await logoutViaUi(page);
    await loginViaUi(page, undefined, undefined, "/dashboard/users");
    await expect(page.getByRole("heading", { name: "User Management" })).toBeVisible();
    await setRoles(page, scorer.email, ["Scorer"]);
    await setRoles(page, organizer.email, ["Organizer"]);

    await logoutViaUi(page);
    await loginViaUi(page, scorer.email, scorer.password, "/scorer");
    await expect(page.getByRole("heading", { name: "Assigned Matches" })).toBeVisible();
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: "Dashboard access denied" })).toBeVisible();
    await page.goto("/dashboard/users");
    await expect(page.getByRole("heading", { name: "User management access denied" })).toBeVisible();

    await logoutViaUi(page);
    await loginViaUi(page, organizer.email, organizer.password, "/dashboard");
    await expect(page.getByText("Organizer").first()).toBeVisible();
    for (const path of ["tournament", "registrations", "payments", "teams", "draft", "fixtures", "matches"]) {
      const response = await page.goto(`/dashboard/${path}`);
      expect(response?.ok()).toBe(true);
      await expect(page).toHaveURL(new RegExp(`/dashboard/${path}(?:$|[?#])`));
      await expect(page.getByText(/access denied/i)).toHaveCount(0);
    }
    await page.goto("/dashboard/users");
    await expect(page.getByRole("heading", { name: "User management access denied" })).toBeVisible();

    await logoutViaUi(page);
    await loginViaUi(page, undefined, undefined, "/dashboard/users");
    await setRoles(page, scorer.email, []);
    await setRoles(page, organizer.email, []);
    await logoutViaUi(page);
    await loginViaUi(page, scorer.email, scorer.password, "/scorer");
    await expect(page.getByRole("heading", { name: "Scorer access denied" })).toBeVisible();
    await logoutViaUi(page);
    await loginViaUi(page, organizer.email, organizer.password, "/dashboard");
    await expect(page.getByRole("heading", { name: "Dashboard access denied" })).toBeVisible();
  });

  test("admin can access dashboard and scorer landing pages", async ({ page }) => {
    await loginViaUi(page);

    await page.goto("/dashboard");
    await expect(page.getByText("Administrator").first()).toBeVisible();

    await page.goto("/scorer");
    await expect(page.getByText("Scorer Console").first()).toBeVisible();
    await expect(page.getByRole("heading", { name: "Assigned Matches" }))
      .toBeVisible();
  });

  test("normal player can use account pages but cannot use admin or scorer pages", async ({
    page,
  }) => {
    await registerPlayerViaUi(page, "E2E Security Player");

    await page.goto("/account/team");
    await expect(page.getByRole("heading", { name: "My Team" })).toBeVisible();

    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: "Dashboard access denied" }))
      .toBeVisible();

    await page.goto("/scorer");
    await expect(page.getByRole("heading", { name: "Scorer access denied" }))
      .toBeVisible();
  });
});

async function setRoles(page: import("@playwright/test").Page, email: string, roles: ("Scorer" | "Organizer")[]) {
  await page.goto(`/dashboard/users?q=${encodeURIComponent(email)}`);
  const form = page.locator("form").filter({ hasText: email });
  await expect(form).toHaveCount(1);
  for (const role of ["Scorer", "Organizer"] as const) {
    const checkbox = form.getByLabel(role, { exact: true });
    if (roles.includes(role)) await checkbox.check(); else await checkbox.uncheck();
  }
  await form.getByRole("button", { name: "Save roles" }).click();
  await expect(page.getByRole("status")).toContainText("Roles updated");
}
