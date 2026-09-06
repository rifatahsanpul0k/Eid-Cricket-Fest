import { expect, type Locator, type Page, test } from "@playwright/test";

import { loginViaUi, logoutViaUi, registerPlayerViaUi } from "./helpers/auth";
import { requireLiveE2EEnvironment, uniqueE2EName } from "./helpers/data";
import { expectPageHeading } from "./helpers/navigation";

const playerCount = 8;
const teamCount = 4;

test.describe.serial("tournament lifecycle acceptance", () => {
  test.setTimeout(10 * 60 * 1000);

  const runName = uniqueE2EName("Season 1.0 Full E2E");
  const venueName = uniqueE2EName("E2E Oval");
  const playerNames = Array.from(
    { length: playerCount },
    (_, index) => `E2E Player ${index + 1}`
  );
  const teamNames = Array.from(
    { length: teamCount },
    (_, index) => `E2E Team ${index + 1}`
  );

  test.beforeEach(async () => {
    await requireLiveE2EEnvironment();
  });

  test("admin completes a minimal tournament season through the browser UI", async ({
    page,
  }) => {
    const playerAccounts: Array<{
      displayName: string;
      email: string;
      password: string;
    }> = [];

    await createTournamentAndEdition(page, runName);
    await transitionEdition(page, "Open Registration", "REGISTRATION_OPEN");

    for (const playerName of playerNames) {
      playerAccounts.push(await registerProfileAndPayment(page, playerName));
    }

    const dedicatedScorer = await registerPlayerViaUi(page, "E2E Season Scorer");

    await logoutViaUi(page);
    await loginViaUi(page, undefined, undefined, "/dashboard/users");
    await grantScorerRole(page, dedicatedScorer.email);
    await page.goto("/dashboard/payments");
    await verifyAllPayments(page);
    await approveAllRegistrations(page);

    await createTeamsAndCaptains(page, teamNames, playerNames);
    await transitionEdition(page, "Close Registration", "REGISTRATION_CLOSED");
    await transitionEdition(page, "Start Drafting", "DRAFTING");
    await completeDraft(page);
    await createVenueAndLeagueFixtures(page, venueName);
    await page.goto("/fixtures");
    await expectPageHeading(page, "Fixtures");
    for (const teamName of teamNames) {
      await expect(page.getByText(teamName).first()).toBeVisible();
    }

    await transitionEdition(page, "Mark Scheduled", "SCHEDULED");
    await transitionEdition(page, "Start Tournament", "ONGOING");

    const leagueMatchIds = await collectMatchIds(page, "LEAGUE", 6);

    for (const [index, matchId] of leagueMatchIds.entries()) {
      await prepareTournamentMatch(
        page,
        matchId,
        venueName,
        index === 0 ? dedicatedScorer.displayName : "E2E Admin"
      );
      if (index === 0) {
        const unassignedMatchId = leagueMatchIds[1];
        if (!unassignedMatchId) throw new Error("Missing unassigned scorer match");
        await logoutViaUi(page);
        await loginViaUi(page, dedicatedScorer.email, dedicatedScorer.password, "/scorer");
        await expect(page.getByText(new RegExp(`Match ${matchId}\\b`)).first()).toBeVisible();
        await expect(page.getByText(new RegExp(`Match ${unassignedMatchId}\\b`)).first()).toHaveCount(0);
        await page.goto(`/scorer/matches/${unassignedMatchId}`);
        await expect(page.getByRole("heading", { name: "Scorer access denied" })).toBeVisible();
        await expect(page.getByText(/backend rejected scorer access/i)).toBeVisible();
        await page.goto(`/scorer/matches/${matchId}`);
        await expect(page.getByRole("button", { name: /Start innings/i })).toBeVisible();
        await logoutViaUi(page);
        await loginViaUi(page, undefined, undefined, `/dashboard/matches/${matchId}`);
      }
      await scoreTinyMatch(page, matchId, index === 0);
    }

    await page.goto("/standings");
    await expectPageHeading(page, "Standings");
    await expect(
      page.getByRole("columnheader", { name: "NRR", exact: true })
    ).toBeVisible();
    await expect(page.getByText(teamNames[0] ?? "E2E Team 1").first())
      .toBeVisible();

    await page.goto("/statistics");
    await expectPageHeading(page, "Statistics");
    await expect(page.getByRole("heading", { name: "Batting Leaders" }))
      .toBeVisible();

    const playerAccount = playerAccounts[0];
    if (!playerAccount) {
      throw new Error("Missing E2E player account");
    }

    await logoutViaUi(page);
    await loginViaUi(
      page,
      playerAccount.email,
      playerAccount.password,
      "/account/team"
    );
    await expectPageHeading(page, "My Team");
    await expect(page.getByText(teamNames[0] ?? "E2E Team 1").first())
      .toBeVisible();
    await page.goto("/account/matches");
    await expectPageHeading(page, "My Matches");
    await expect(page.getByText("Completed", { exact: true }).first())
      .toBeVisible();
    await page.goto("/account/statistics");
    await expectPageHeading(page, "My Statistics");
    await expect(page.getByRole("heading", { name: playerAccount.displayName }))
      .toBeVisible();
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: "Dashboard access denied" })).toBeVisible();
    await page.goto("/scorer");
    await expect(page.getByRole("heading", { name: "Scorer access denied" })).toBeVisible();
    await logoutViaUi(page);
    await loginViaUi(page, undefined, undefined, "/dashboard/fixtures");

    await generateSemiFinals(page);
    const semiFinalIds = await collectMatchIds(page, "SEMI_FINAL", 2);
    await completeKnockoutMatches(page, semiFinalIds, venueName);

    const finalIds = await collectMatchIds(page, "FINAL", 1);
    await completeKnockoutMatches(page, finalIds, venueName);

    await expectCompletedTournament(page, runName);
    await assignAward(page, "E2E Player of the Tournament");

    await page.goto("/awards");
    await expectPageHeading(page, "Awards");
    await expect(page.getByText("E2E Player of the Tournament")).toBeVisible();

    await page.goto("/history");
    await expectPageHeading(page, "History");
    await expect(page.getByText(runName)).toBeVisible();
    await expect(page.getByText("Champion", { exact: true })).toBeVisible();
    await expect(page.getByText("Runner-up", { exact: true })).toBeVisible();
    await expect(page.getByText("E2E Player of the Tournament")).toBeVisible();
  });

  test("public homepage reflects the completed tournament edition", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.getByText("Eid Cricket Fest").first()).toBeVisible();
    await expect(page.getByText(runName).first()).toBeVisible();
    await expect(page.getByText("Completed").first()).toBeVisible();
  });

  test("official final review and rematch replace authoritative finalization", async ({
    page,
  }) => {
    await loginViaUi(page, undefined, undefined, "/dashboard/matches");
    const [originalFinalId] = await collectMatchIds(page, "FINAL", 1);

    if (!originalFinalId) {
      throw new Error("Missing completed official final");
    }

    await runMatchOperation(page, originalFinalId, "Mark Under Review");
    await expect(page.getByText("Result Under Review", { exact: true }))
      .toBeVisible();

    await page.goto("/history");
    await expect(page.getByText(runName, { exact: true })).toHaveCount(0);

    const replacementFinalId = await runMatchOperation(
      page,
      originalFinalId,
      "Order Rematch",
      venueName
    );

    if (!replacementFinalId) {
      throw new Error("Missing replacement official final");
    }

    await prepareTournamentMatch(page, replacementFinalId, venueName);
    await scoreTinyMatch(page, replacementFinalId);
    await expectCompletedTournament(page, runName);

    await page.goto("/history");
    await expect(page.getByText(runName, { exact: true })).toBeVisible();
    await expect(page.getByText("Champion", { exact: true })).toBeVisible();
    await expect(page.getByText("Runner-up", { exact: true })).toBeVisible();
  });

  test("completed friendly match remains isolated from tournament aggregates", async ({
    page,
  }) => {
    await loginViaUi(page);

    const standingsBefore = await visiblePageText(page, "/standings", "Standings");
    const statisticsBefore = await visiblePageText(page, "/statistics", "Statistics");
    const knockoutBefore = await visiblePageText(page, "/knockout", "Knockout");
    const historyBefore = await visiblePageText(page, "/history", "History");

    const friendlyMatchId = await createFriendlyMatchViaUi(page, venueName);
    await prepareFriendlyMatch(page, friendlyMatchId);
    await scoreTinyMatch(page, friendlyMatchId);

    await page.goto("/live");
    await expectPageHeading(page, "Live Matches");
    await expect(page.getByText("E2E Friendly A vs E2E Friendly B").first())
      .toBeVisible();

    expect(await visiblePageText(page, "/standings", "Standings"))
      .toBe(standingsBefore);
    expect(await visiblePageText(page, "/statistics", "Statistics"))
      .toBe(statisticsBefore);
    expect(await visiblePageText(page, "/knockout", "Knockout"))
      .toBe(knockoutBefore);
    expect(await visiblePageText(page, "/history", "History"))
      .toBe(historyBefore);
  });
});

async function createTournamentAndEdition(page: Page, editionName: string) {
  await loginViaUi(page, undefined, undefined, "/dashboard/tournament");
  await expectPageHeading(page, "Tournament Setup");

  const createTournamentButton =
    page.getByRole("button", { name: "Create tournament" });

  if (await createTournamentButton.isVisible()) {
    await page.getByLabel("Name").fill("Eid Cricket Fest");
    await createTournamentButton.click();
    await expect(page.getByText("Eid Cricket Fest").first()).toBeVisible();
  }

  const createEditionButton =
    page.getByRole("button", { name: "Create edition" }).first();
  const createEditionForm =
    page.locator("form").filter({ has: createEditionButton });

  await createEditionForm.getByLabel("Name").fill(editionName);
  await createEditionForm.getByLabel("Overs per innings").fill("1");
  await createEditionForm.getByLabel("Squad size").fill("2");
  await createEditionForm.getByLabel("Playing XI size").fill("2");
  await createEditionForm.getByLabel("Registration fee").fill("1");
  await createEditionForm.getByLabel("Currency").fill("BDT");
  await createEditionButton.click();
  await expect(page.getByText(editionName)).toBeVisible();
}

async function transitionEdition(
  page: Page,
  actionLabel: string,
  expectedStatus: string
) {
  await loginViaUi(page, undefined, undefined, "/dashboard/tournament");
  await page.getByRole("button", { name: actionLabel }).first().click();
  await expect(page.getByText(editionStatusLabel(expectedStatus)).first())
    .toBeVisible();
}

async function registerProfileAndPayment(page: Page, playerName: string) {
  const account = await registerPlayerViaUi(page, playerName);

  await page.goto("/account/profile");
  await expectPageHeading(page, "Profile");
  await page.getByLabel("Full name").fill(playerName);
  await page.locator('select[name="primaryCategoryId"]').selectOption({ index: 1 });
  await page.getByLabel("Date of birth").fill("2000-01-01");
  await page.getByLabel("Batting style").fill("Right hand bat");
  await page.getByLabel("Bowling style").fill("Right arm medium");
  await page.getByRole("button", { name: "Create profile" }).click();
  await expect(page.getByRole("heading", { name: playerName })).toBeVisible();

  await page.goto("/account/registration");
  await expectPageHeading(page, "Registration");
  await page.getByRole("button", { name: "Submit registration" }).click();
  await expect(page.getByText("PENDING").first()).toBeVisible();
  await page.getByRole("button", { name: "Submit payment" }).click();
  await expect(page.getByText("CASH").first()).toBeVisible();

  await logoutViaUi(page);

  return account;
}

async function verifyAllPayments(page: Page) {
  await expectPageHeading(page, "Payments");

  while (await page.getByRole("button", { name: "Verify" }).count()) {
    await page.getByRole("button", { name: "Verify" }).first().click();
    await expect(page).toHaveURL(/\/dashboard\/payments(?:$|[?#])/);
  }

  const rows = page.getByRole("row").filter({ hasText: /Registration #/ });
  await expect(rows).toHaveCount(playerCount);
  await expect(rows.first().getByText("Verified").first()).toBeVisible();
}

async function approveAllRegistrations(page: Page) {
  await page.goto("/dashboard/registrations");
  await expectPageHeading(page, "Registrations");

  while (await page.getByRole("button", { name: "Approve" }).count()) {
    await page.getByRole("button", { name: "Approve" }).first().click();
    await expect(page).toHaveURL(/\/dashboard\/registrations(?:$|[?#])/);
  }

  const rows = page.getByRole("row").filter({ hasText: /Registration #/ });
  await expect(rows).toHaveCount(playerCount);
  await expect(rows.first().getByText("Approved").first()).toBeVisible();
}

async function createTeamsAndCaptains(
  page: Page,
  teams: string[],
  captains: string[]
) {
  await page.goto("/dashboard/teams");
  await expectPageHeading(page, "Teams");

  for (const [index, teamName] of teams.entries()) {
    const createTeamButton = page.getByRole("button", { name: "Create team" });
    const createTeamForm = page.locator("form").filter({ has: createTeamButton });

    await createTeamForm.getByLabel("Name", { exact: true }).fill(teamName);
    await createTeamForm.getByLabel("Short name").fill(`E2E${index + 1}`);
    await createTeamButton.click();

    const addTeamButton =
      page.getByRole("button", { name: "Add to edition" });
    const addTeamForm = page.locator("form").filter({ has: addTeamButton });
    await selectOptionContaining(
      addTeamForm.locator('select[name="teamId"]'),
      teamName
    );
    await addTeamButton.click();
    await expect(page.getByText(`Tournament team #`).nth(index)).toBeVisible();
  }

  for (const [index, teamName] of teams.entries()) {
    const captainName = captains[index];

    if (!captainName) {
      throw new Error(`Missing captain for ${teamName}`);
    }

    const row = page.getByRole("row").filter({ hasText: teamName });
    await selectOptionContaining(
      row.locator('select[name="registrationId"]'),
      captainName
    );
    await row.getByRole("button", { name: "Save captain" }).click();
    await expect(row.getByText(captainName)).toBeVisible();
  }
}

async function completeDraft(page: Page) {
  await page.goto("/dashboard/draft");
  await expectPageHeading(page, "Draft");
  await page.getByRole("button", { name: "Create draft" }).click();
  await expect(page.getByText("PENDING").first()).toBeVisible();
  await page.getByRole("button", { name: "Generate lottery" }).click();
  await expect(page.getByText("ORDER_GENERATED").first()).toBeVisible();
  await page.getByRole("button", { name: "Start draft" }).click();
  await expect(page.getByText("IN_PROGRESS").first()).toBeVisible();

  while (await page.getByRole("button", { name: "Make pick" }).count()) {
    const pickForm = page.locator("form").filter({
      has: page.getByRole("button", { name: "Make pick" }),
    });

    await pickForm.locator('select[name="registrationId"]').selectOption({ index: 1 });
    await page.getByRole("button", { name: "Make pick" }).click();
  }

  await expect(page.getByText("COMPLETED").first()).toBeVisible();
}

async function createVenueAndLeagueFixtures(page: Page, venue: string) {
  await page.goto("/dashboard/fixtures");
  await expectPageHeading(page, "Fixtures");

  const createVenueButton =
    page.getByRole("button", { name: "Create venue" });
  const venueForm = page.locator("form").filter({ has: createVenueButton });
  await venueForm.getByLabel("Name").fill(venue);
  await venueForm.getByLabel("Address").fill("E2E Ground");
  await createVenueButton.click();

  const fixtureForm = page.locator("form").filter({
    has: page.getByRole("button", { name: "Generate round robin" }),
  });
  await selectOptionContaining(fixtureForm.locator('select[name="venueId"]'), venue);
  await page.getByRole("button", { name: "Generate round robin" }).click();
  await expect(page.getByText("League").first()).toBeVisible();
}

async function collectMatchIds(
  page: Page,
  stage: "LEAGUE" | "SEMI_FINAL" | "FINAL",
  expectedCount: number
) {
  await page.goto(`/dashboard/matches?stage=${stage}&size=20`);
  await expectPageHeading(page, "Matches");

  const links = page.locator('a[href^="/dashboard/matches/"]', {
    hasText: "Manage",
  });
  await expect(links).toHaveCount(expectedCount);

  const hrefs = await links.evaluateAll((anchors) =>
    anchors.map((anchor) => anchor.getAttribute("href") ?? "")
  );

  return hrefs.map((href) => Number(href.split("/").at(-1)));
}

async function prepareTournamentMatch(
  page: Page,
  matchId: number,
  venue: string,
  scorerName = "E2E Admin"
) {
  await page.goto(`/dashboard/matches/${matchId}`);
  await expect(
    page.getByRole("heading", { name: "Readiness", exact: true })
  ).toBeVisible();

  const scheduleForm = page.locator("form").filter({
    has: page.getByRole("button", { name: "Save schedule" }),
  });
  await scheduleForm
    .getByLabel("Time")
    .fill("2027-01-01T09:00");
  await selectOptionContaining(scheduleForm.locator('select[name="venueId"]'), venue);
  await scheduleForm.getByRole("button", { name: "Save schedule" }).click();

  const scorerForm = page.locator("form").filter({
    has: page.getByRole("button", { name: "Assign scorer" }),
  });
  await selectOptionContaining(
    scorerForm.locator('select[name="scorerUserId"]'),
    scorerName
  );
  await scorerForm.getByRole("button", { name: "Assign scorer" }).click();

  const xiForms = page.locator("form").filter({
    has: page.getByRole("button", { name: "Submit XI" }),
  });
  await expect(xiForms).toHaveCount(2);

  for (let index = 0; index < 2; index += 1) {
    const form = xiForms.nth(index);
    await form.locator('input[name="registrationIds"]').nth(0).check();
    await form.locator('input[name="registrationIds"]').nth(1).check();
    await form
      .locator('select[name="wicketkeeperRegistrationId"]')
      .selectOption({ index: 1 });
    await form.getByRole("button", { name: "Submit XI" }).click();
  }

  const tossForm = page.locator("form").filter({
    has: page.getByRole("button", { name: "Record toss" }),
  });
  await tossForm.locator('select[name="winnerTournamentTeamId"]').selectOption({
    index: 1,
  });
  await tossForm.locator('select[name="decision"]').selectOption("BOWL");
  await tossForm.getByRole("button", { name: "Record toss" }).click();
  await expect(page.getByText("Toss Complete").first()).toBeVisible();
}

async function grantScorerRole(page: Page, email: string) {
  await page.goto(`/dashboard/users?q=${encodeURIComponent(email)}`);
  const form = page.locator("form").filter({ hasText: email });
  await expect(form).toHaveCount(1);
  await form.getByLabel("Scorer", { exact: true }).check();
  await form.getByRole("button", { name: "Save roles" }).click();
  await expect(page.getByRole("status")).toContainText("Roles updated");
}

async function scoreTinyMatch(
  page: Page,
  matchId: number,
  verifyPublicLive = false
) {
  await page.goto(`/scorer/matches/${matchId}`);
  await expect(page.getByText("TOSS_COMPLETED").first()).toBeVisible();

  await startInnings(page);

  if (verifyPublicLive) {
    const publicPage = await page.context().newPage();
    await publicPage.goto("/live");
    await expectPageHeading(publicPage, "Live Matches");
    await expect(publicPage.getByText("Live", { exact: true }).first())
      .toBeVisible();
    await publicPage.goto(`/matches/${matchId}/live`);
    await expect(publicPage.getByText("Live", { exact: true }).first())
      .toBeVisible();
    await publicPage.close();
  }

  for (let ball = 0; ball < 6; ball += 1) {
    await page.getByRole("button", { name: "0", exact: true }).click();
  }

  await expect(page.getByText("INNINGS_BREAK").first()).toBeVisible();
  await startInnings(page);
  await page.getByRole("button", { name: "1", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Match Complete" }))
    .toBeVisible();

  await page.goto(`/matches/${matchId}/scorecard`);
  await expectPageHeading(page, "Scorecard");
  await expect(page.getByText("0/0", { exact: false }).first()).toBeVisible();
}

async function startInnings(page: Page) {
  await choosePlayer(page, "Select striker", 0);
  await choosePlayer(page, "Select non-striker", 0);
  await choosePlayer(page, "Select opening bowler", 0);
  await page.getByRole("button", { name: "Start Innings" }).click();
  await expect(page.getByText("LIVE").first()).toBeVisible();
}

async function generateSemiFinals(page: Page) {
  await page.goto("/dashboard/fixtures");
  await expectPageHeading(page, "Fixtures");
  await page.getByRole("button", { name: "Generate semi-finals" }).click();
  await expect(page.getByText("Semi-final").first()).toBeVisible();
}

async function completeKnockoutMatches(
  page: Page,
  matchIds: number[],
  venue: string
) {
  for (const matchId of matchIds) {
    await prepareTournamentMatch(page, matchId, venue);
    await scoreTinyMatch(page, matchId);
    await page.goto("/knockout");
    await expectPageHeading(page, "Knockout");
  }
}

async function runMatchOperation(
  page: Page,
  matchId: number,
  operation: "Mark Under Review" | "Order Rematch",
  venue?: string
) {
  await page.goto(`/dashboard/matches/${matchId}`);
  const button = page.getByRole("button", { name: operation, exact: true });
  const form = page.locator("form").filter({ has: button });

  await form.getByLabel("Reason").fill(`E2E ${operation.toLowerCase()}`);

  if (operation === "Order Rematch") {
    if (!venue) {
      throw new Error("Venue is required when ordering a rematch");
    }
    await form.getByLabel("Time").fill("2027-01-02T09:00");
    await selectOptionContaining(form.locator('select[name="venueId"]'), venue);
    await form.getByLabel("Overs").fill("1");
  }

  await button.click();
  await expect(page).toHaveURL(/\/dashboard\/matches\/\d+(?:$|[?#])/);

  const resultingMatchId = Number(new URL(page.url()).pathname.split("/").at(-1));

  if (operation !== "Order Rematch" && resultingMatchId !== matchId) {
    throw new Error(`${operation} unexpectedly navigated to another match`);
  }

  return resultingMatchId;
}

async function createFriendlyMatchViaUi(page: Page, venue: string) {
  await page.goto("/dashboard/matches/friendly/new");
  await expectPageHeading(page, "New Friendly Match");
  await page.getByLabel("Team A name").fill("E2E Friendly A");
  await page.getByLabel("Team B name").fill("E2E Friendly B");
  await page.getByLabel("Overs").fill("1");
  await selectOptionContaining(page.locator('select[name="venueId"]'), venue);
  await page.getByLabel("Scheduled time").fill("2027-01-03T09:00");

  const teamAPlayers = page.locator('input[name="teamAPlayerIds"]');
  const teamBPlayers = page.locator('input[name="teamBPlayerIds"]');
  await teamAPlayers.nth(0).check();
  await teamAPlayers.nth(1).check();
  await teamBPlayers.nth(2).check();
  await teamBPlayers.nth(3).check();
  await page.getByRole("button", { name: "Create friendly match" }).click();
  await expect(page).toHaveURL(/\/dashboard\/matches\/\d+(?:$|[?#])/);

  return Number(new URL(page.url()).pathname.split("/").at(-1));
}

async function prepareFriendlyMatch(page: Page, matchId: number) {
  await page.goto(`/dashboard/matches/${matchId}`);

  const scorerForm = page.locator("form").filter({
    has: page.getByRole("button", { name: "Assign scorer" }),
  });
  await selectOptionContaining(
    scorerForm.locator('select[name="scorerUserId"]'),
    "E2E Admin"
  );
  await scorerForm.getByRole("button", { name: "Assign scorer" }).click();

  const tossForm = page.locator("form").filter({
    has: page.getByRole("button", { name: "Record toss" }),
  });
  await tossForm.locator('select[name="winnerMatchSideId"]').selectOption({
    index: 1,
  });
  await tossForm.locator('select[name="decision"]').selectOption("BOWL");
  await tossForm.getByRole("button", { name: "Record toss" }).click();
  await expect(page.getByText("Toss Complete").first()).toBeVisible();
}

async function visiblePageText(page: Page, path: string, heading: string) {
  await page.goto(path);
  await expectPageHeading(page, heading);
  return (await page.locator("main").innerText()).trim();
}

async function expectCompletedTournament(page: Page, editionName: string) {
  await page.goto("/dashboard/tournament");
  await expect(page.getByText(editionName)).toBeVisible();
  await expect(page.getByText("Completed").first()).toBeVisible();
  await expect(page.getByText("Champion").first()).toBeVisible();
  await expect(page.getByText("Runner-up").first()).toBeVisible();
  await expect(page.getByText("Tournament Complete")).toBeVisible();
}

async function assignAward(page: Page, title: string) {
  await page.goto("/dashboard/tournament");
  const form = page.locator("form").filter({
    has: page.getByRole("button", { name: "Assign award" }),
  });

  await form.locator('select[name="registrationId"]').selectOption({ index: 1 });
  await form.getByLabel("Custom title").fill(title);
  await form.getByRole("button", { name: "Assign award" }).click();
  await expect(page.getByText(title)).toBeVisible();
}

async function choosePlayer(page: Page, label: string, index: number) {
  const group = page.getByText(label, { exact: true }).locator("..");
  await group.getByRole("button").nth(index).click();
}

async function selectOptionContaining(select: Locator, text: string) {
  const options = select.locator("option");
  const count = await options.count();

  for (let index = 0; index < count; index += 1) {
    const option = options.nth(index);
    const label = (await option.textContent()) ?? "";

    if (label.includes(text)) {
      await select.selectOption({ index });
      return;
    }
  }

  throw new Error(`No option containing "${text}"`);
}

function editionStatusLabel(status: string) {
  const productLabels: Record<string, string> = {
    DRAFTING: "Draft In Progress",
    SCHEDULED: "Upcoming",
    ONGOING: "Live Tournament",
  };

  if (productLabels[status]) {
    return productLabels[status];
  }

  return status
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}
