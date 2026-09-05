import { expect, request, test } from "@playwright/test";

export const e2eAdmin = {
  displayName: process.env.ADMIN_BOOTSTRAP_DISPLAY_NAME ?? "E2E Admin",
  email: process.env.ADMIN_BOOTSTRAP_EMAIL ?? "e2e-admin@example.com",
  password: process.env.ADMIN_BOOTSTRAP_PASSWORD ?? "StrongE2EPassword123",
};

export const backendBaseURL =
  process.env.E2E_BACKEND_URL ?? process.env.API_BASE_URL ?? "http://localhost:8080";

export const frontendBaseURL =
  process.env.E2E_FRONTEND_URL ?? "http://localhost:3000";

export function uniqueE2EName(prefix: string) {
  const stamp =
    process.env.E2E_RUN_ID ??
    new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);

  return `${prefix} ${stamp}`;
}

export function uniqueE2EEmail(prefix: string) {
  const stamp =
    process.env.E2E_RUN_ID ??
    new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);

  return `${prefix}-${stamp}@example.com`;
}

export async function requireLiveE2EEnvironment() {
  const context = await request.newContext();

  try {
    const [backend, frontend] = await Promise.allSettled([
      context.get(`${backendBaseURL}/actuator/health/readiness`, {
        timeout: 5_000,
      }),
      context.get(frontendBaseURL, { timeout: 5_000 }),
    ]);

    const backendReady =
      backend.status === "fulfilled" && backend.value.ok();
    const frontendReady =
      frontend.status === "fulfilled" && frontend.value.ok();

    if (backend.status === "fulfilled") {
      await backend.value.dispose();
    }

    if (frontend.status === "fulfilled") {
      await frontend.value.dispose();
    }

    test.skip(
      !backendReady || !frontendReady,
      `E2E requires backend ${backendBaseURL} and frontend ${frontendBaseURL}`
    );
  } finally {
    await context.dispose();
  }
}

export async function assertCurrentEditionNaming() {
  const context = await request.newContext();

  try {
    const response = await context.get(frontendBaseURL);
    await expect(response).toBeOK();
    const html = await response.text();

    expect(html).toContain("Eid Cricket Fest");
    expect(html).not.toContain("Kandapara Eid Cricket Fest");
  } finally {
    await context.dispose();
  }
}
