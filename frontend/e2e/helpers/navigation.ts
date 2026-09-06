import { expect, type Page } from "@playwright/test";

export async function expectProtectedRedirect(page: Page, path: string) {
  await page.goto(path);
  await expect(page).toHaveURL(/\/login\?returnTo=/);
}

export async function expectPageHeading(page: Page, name: string | RegExp) {
  await expect(
    page.getByRole("heading", {
      name,
      exact: typeof name === "string",
    })
  ).toBeVisible();
}
