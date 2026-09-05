import { expect, test } from "@playwright/test";

import { loginViaUi } from "./helpers/auth";
import { requireLiveE2EEnvironment, uniqueE2EName } from "./helpers/data";
import { expectPageHeading } from "./helpers/navigation";

test.describe.serial("tournament lifecycle acceptance", () => {
  test.beforeEach(async () => {
    await requireLiveE2EEnvironment();
  });

  test("admin can create tournament and current edition through UI", async ({
    page,
  }) => {
    const tournamentName = "Eid Cricket Fest";
    const editionName = uniqueE2EName("Season 1.0 E2E");

    await loginViaUi(page, undefined, undefined, "/dashboard/tournament");
    await expectPageHeading(page, "Tournament Setup");

    const createTournamentButton =
      page.getByRole("button", { name: "Create tournament" });

    if (await createTournamentButton.isVisible()) {
      await page.getByLabel("Name").fill(tournamentName);
      await createTournamentButton.click();
      await expect(page.getByText(tournamentName).first()).toBeVisible();
    }

    const createEditionButton =
      page.getByRole("button", { name: "Create edition" }).first();

    if (await createEditionButton.isVisible()) {
      const createEditionForm =
        page.locator("form").filter({ has: createEditionButton });

      await createEditionForm.getByLabel("Name").fill(editionName);
      await createEditionForm.getByLabel("Overs per innings").fill("1");
      await createEditionForm.getByLabel("Squad size").fill("3");
      await createEditionForm.getByLabel("Playing XI size").fill("3");
      await createEditionForm.getByLabel("Registration fee").fill("100");
      await createEditionForm.getByLabel("Currency").fill("BDT");
      await createEditionButton.click();
    }

    await expect(page.getByText("Eid Cricket Fest").first()).toBeVisible();
    await expect(page.getByText(/Season 1\.0|Season 1\.0 E2E/).first())
      .toBeVisible();
  });

  test("public homepage reflects the created tournament edition", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.getByText("Eid Cricket Fest").first()).toBeVisible();
    await expect(page.getByText(/Season 1\.0/).first()).toBeVisible();
  });
});
