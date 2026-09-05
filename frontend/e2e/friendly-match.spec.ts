import { expect, test } from "@playwright/test";

import { loginViaUi } from "./helpers/auth";
import { requireLiveE2EEnvironment } from "./helpers/data";

test.describe("friendly match acceptance", () => {
  test.beforeEach(async () => {
    await requireLiveE2EEnvironment();
  });

  test("friendly match creation page is reachable without tournament roster shortcuts", async ({
    page,
  }) => {
    await loginViaUi(page, undefined, undefined, "/dashboard/matches/friendly/new");

    await expect(page.getByRole("heading", { name: "New Friendly Match" }))
      .toBeVisible();
    await expect(page.getByLabel("Team A name")).toBeVisible();
    await expect(page.getByLabel("Team B name")).toBeVisible();
    await expect(page.getByLabel("Overs")).toBeVisible();
  });
});
