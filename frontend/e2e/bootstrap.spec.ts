import { expect, test } from "@playwright/test";

import { loginViaUi, registerPlayerViaUi } from "./helpers/auth";
import { assertCurrentEditionNaming, requireLiveE2EEnvironment } from "./helpers/data";
import { expectPageHeading, expectProtectedRedirect } from "./helpers/navigation";

test.describe("fresh install bootstrap", () => {
  test.beforeEach(async () => {
    await requireLiveE2EEnvironment();
  });

  test("startup bootstrap admin can log in and reach admin and scorer areas", async ({
    page,
  }) => {
    await test.step("public homepage uses product naming", async () => {
      await assertCurrentEditionNaming();
      await page.goto("/");
      await expect(page.getByText("Kandapara Eid Cricket Fest")).toHaveCount(0);
      await expect(page.getByText("Eid Cricket Fest").first()).toBeVisible();
    });

    await test.step("admin login succeeds", async () => {
      await loginViaUi(page);
      await expectPageHeading(page, "Dashboard");
      await expect(page.getByText("Administrator").first()).toBeVisible();
    });

    await test.step("scorer area is accessible to bootstrap admin", async () => {
      await page.goto("/scorer");
      await expect(page.getByText("Scorer Console").first()).toBeVisible();
      await expectPageHeading(page, "Assigned Matches");
    });
  });

  test("anonymous routes are protected and normal registration is player-only", async ({
    page,
  }) => {
    await expectProtectedRedirect(page, "/dashboard");
    await expectProtectedRedirect(page, "/account");
    await expectProtectedRedirect(page, "/scorer");

    const player = await registerPlayerViaUi(page, "E2E Player Only");
    await expect(page.getByText(player.displayName).first()).toBeVisible();

    await page.goto("/dashboard");
    await expect(page.getByText("Administrator")).toHaveCount(0);
    await expect(page.getByText(/organizer or admin access is required/i))
      .toBeVisible();

    await page.goto("/scorer");
    await expect(page.getByText(/scorer, organizer, or admin role is required/i)).toBeVisible();
  });
});
