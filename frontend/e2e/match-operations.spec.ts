import { expect, test } from "@playwright/test";

import { loginViaUi } from "./helpers/auth";
import { requireLiveE2EEnvironment } from "./helpers/data";

test.describe("match operations acceptance", () => {
  test.beforeEach(async () => {
    await requireLiveE2EEnvironment();
  });

  test("match operations dashboard is reachable and shows tournament match controls", async ({
    page,
  }) => {
    await loginViaUi(page, undefined, undefined, "/dashboard/matches");

    await expect(page.getByRole("heading", { name: "Matches", exact: true }))
      .toBeVisible();
    await expect(page.getByRole("heading", { name: "Tournament Matches" }))
      .toBeVisible();
    const stageFilter = page.locator('select[name="stage"]');

    if (await stageFilter.isVisible()) {
      await expect(stageFilter).toBeVisible();
      await expect(page.locator('select[name="status"]')).toBeVisible();
      await expect(page.getByRole("button", { name: "Apply" })).toBeVisible();
    } else {
      await expect(page.getByText("No tournament has been created yet."))
        .toBeVisible();
    }
  });
});
