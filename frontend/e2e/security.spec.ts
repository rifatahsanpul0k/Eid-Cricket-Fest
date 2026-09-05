import { expect, test } from "@playwright/test";

import { loginViaUi, registerPlayerViaUi } from "./helpers/auth";
import { requireLiveE2EEnvironment } from "./helpers/data";
import { expectProtectedRedirect } from "./helpers/navigation";

test.describe("security acceptance", () => {
  test.beforeEach(async () => {
    await requireLiveE2EEnvironment();
  });

  test("anonymous protected deep links redirect to login", async ({ page }) => {
    await expectProtectedRedirect(page, "/dashboard/tournament");
    await expectProtectedRedirect(page, "/dashboard/matches/1");
    await expectProtectedRedirect(page, "/scorer/matches/1");
    await expectProtectedRedirect(page, "/account/team");
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
