import { expect, type Page } from "@playwright/test";

import { e2eAdmin, uniqueE2EEmail } from "./data";

export async function loginViaUi(
  page: Page,
  identifier = e2eAdmin.email,
  password = e2eAdmin.password,
  returnTo = "/dashboard"
) {
  await page.goto(`/login?returnTo=${encodeURIComponent(returnTo)}`);
  const emailField = page.getByLabel("Email or phone");

  if (!(await emailField.isVisible({ timeout: 1000 }))) {
    await expect(page).toHaveURL(new RegExp(`${escapeRegExp(returnTo)}(?:$|[?#])`));
    return;
  }

  await emailField.fill(identifier);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Login" }).click();
  await expect(page).toHaveURL(new RegExp(`${escapeRegExp(returnTo)}(?:$|[?#])`));
}

export async function registerPlayerViaUi(page: Page, displayName: string) {
  const email = uniqueE2EEmail(displayName.toLowerCase().replaceAll(" ", "-"));
  const password = "StrongPassword123";

  await page.goto("/");
  const logoutButton = page.getByRole("button", { name: "Logout" });

  if (await logoutButton.isVisible({ timeout: 1000 })) {
    await logoutButton.click();
    await expect(page).toHaveURL(/\/(?:$|[?#])/);
  }

  await page.goto("/register");
  await page.getByLabel("Display name").fill(displayName);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Register" }).click();
  await expect(page).toHaveURL(/\/account(?:$|[?#])/);

  return { displayName, email, password };
}

export async function logoutViaUi(page: Page) {
  await page.getByRole("button", { name: "Logout" }).click();
  await expect(page).toHaveURL(/\/(?:$|[?#])/);
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
