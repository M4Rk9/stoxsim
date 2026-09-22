import { expect, test, type Page, type Route } from "@playwright/test";
const account = { accessToken: "test-token", expiresInSeconds: 900, user: { id: "learner", email: "learner@example.test", displayName: "Learner", emailVerified: true, platformAdmin: false, accounts: [] } };
const institution = { id: "campus-a", name: "First University", emailDomain: "first.edu", suspended: false };
const competition = { id: "challenge", institutionId: institution.id, title: "Campus challenge", startsAt: "2026-09-21T00:00:00Z", endsAt: "2026-09-28T00:00:00Z", status: "OPEN", capacity: 50, participants: 0, enrolled: false, withdrawn: false };
const note = "Campus standings show percentage change since each learner enrolled. Only your own score refreshes. After the end, last observed values remain frozen, not official closing-price results.";
async function respond(route: Route, status: number, body: unknown) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: options || status === 204 || body === null ? undefined : JSON.stringify(body) });
}
async function setup(page: Page, role = "MEMBER") {
  const state = { profile: { platformAdmin: role === "ADMIN", emailVerified: true, membership: role === "NONE" || role === "ADMIN" ? null : { institutionId: institution.id, institutionName: institution.name, role } },
    latest: null as Record<string, unknown> | null,
    workspace: { institution: { ...institution }, viewerRole: role, competitions: [{ ...competition }] },
    management: { members: [{ userId: "learner", displayName: "Learner", role }], requests: [] as Record<string, unknown>[], audit: [] },
    board: { competition: { ...competition }, standings: [] as Record<string, unknown>[], yourBaselineValue: null as number | null, yourLatestValue: null as number | null, refreshUnavailable: false, comparisonNote: note },
    failBoard: false, failManage: false, denied: false, refreshCalls: 0, posts: [] as { path: string; body: unknown }[] };
  await page.addInitScript(value => sessionStorage.setItem("stoxsim-session", JSON.stringify(value)), account);
  await page.route("**/api/v1/campus**", async route => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1/campus", "");
    if (route.request().method() === "OPTIONS") return respond(route, 204, null);
    if (route.request().method() === "POST") {
      let body: Record<string, unknown> | null = null; try { body = route.request().postDataJSON(); } catch { /* bodyless action */ }
      state.posts.push({ path, body });
      if (path.endsWith("/enroll")) { state.board.competition.enrolled = true; state.board.competition.participants = 1; state.board.yourBaselineValue = 500000; state.board.yourLatestValue = 500000; state.board.standings = [{ rank: 1, displayName: "Learner", returnPercent: 0, dataStatus: "UNAVAILABLE", joinedAt: "2026-09-21T12:00:00Z", valuedAt: "2026-09-21T12:00:00Z", currentUser: true }]; return respond(route, 200, state.board); }
      if (path.endsWith("/withdraw")) { state.board.competition.enrolled = false; state.board.competition.withdrawn = true; state.board.standings = []; }
      if (path.endsWith("/membership-requests")) { state.latest = { id: "request", institutionId: institution.id, institutionName: institution.name, status: "PENDING", ...body }; return respond(route, 200, state.latest); }
      if (path.endsWith("/review")) state.management.requests = [];
      if (path.endsWith("/cancel") && path.includes("membership-requests")) state.latest = { ...state.latest, status: "CANCELLED" };
      return respond(route, 200, null);
    }
    if (path === "") return respond(route, 200, state.profile);
    if (path === "/membership-request") return respond(route, 200, state.latest);
    if (path === "/institutions") return respond(route, 200, [institution, { ...institution, id: "campus-b", name: "Other University" }]);
    if (path.endsWith("/manage")) return respond(route, state.failManage ? 404 : 200, state.failManage ? { message: "Campus resource not found" } : state.management);
    if (state.denied) return respond(route, 404, { message: "Campus resource not found" });
    if (path.includes("/competitions/")) { state.refreshCalls++; return respond(route, state.failBoard ? 503 : 200, state.failBoard ? { message: "Board unavailable" } : state.board); }
    return respond(route, 200, state.workspace);
  });
  return state;
}

test("learner requests membership with privacy notice and can cancel pending request", async ({ page }) => {
  const state = await setup(page, "NONE"); await page.goto("/campus");
  await page.getByRole("button", { name: /First University/ }).click();
  await expect(page.getByText("An organizer reviews your name, email and affiliation note.", { exact: false })).toBeVisible();
  await page.getByLabel("Affiliation note").fill("Finance club, final year");
  await page.getByRole("button", { name: "Request membership" }).click();
  await expect(page.getByText("First University · PENDING")).toBeVisible();
  expect(state.posts[0].body).toEqual({ note: "Finance club, final year" });
  await page.getByRole("button", { name: "Cancel request" }).click();
  await expect(page.getByText("First University · CANCELLED")).toBeVisible();
});

test("campus enrollment needs explicit disclosure and withdrawal cannot reset the baseline", async ({ page }) => {
  const state = await setup(page); await page.goto("/campus");
  await page.getByRole("button", { name: /Campus challenge/ }).click();
  await expect(page.getByRole("button", { name: "Join campus competition" })).toBeDisabled();
  await page.getByRole("checkbox").check(); await page.getByRole("button", { name: "Join campus competition" }).click();
  await expect(page.getByText("Your entry baseline")).toContainText("5,00,000");
  expect(state.posts.map(p => p.path)).toEqual(["/institutions/campus-a/competitions/challenge/enroll"]);
  page.once("dialog", dialog => dialog.accept()); await page.getByRole("button", { name: "Withdraw", exact: true }).click();
  await expect(page.getByText("You withdrew from this competition. Re-enrollment is disabled.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Join campus competition" })).toHaveCount(0);
});

test("organizer reviews membership with an affiliation note", async ({ page }) => {
  const state = await setup(page, "ORGANIZER");
  state.management.requests = [{ id: "request", displayName: "Applicant", email: "applicant@example.test", note: "Economics club" }];
  await page.goto("/campus"); await expect(page.getByText("applicant@example.test")).toBeVisible();
  page.once("dialog", dialog => dialog.accept("Affiliation confirmed with club"));
  await page.getByRole("button", { name: "Approve Applicant" }).click();
  await expect(page.getByText("No pending membership requests.")).toBeVisible();
  expect(state.posts[0].body).toEqual({ approve: true, note: "Affiliation confirmed with club" });
  await expect(page.getByRole("heading", { name: "Create a competition" })).toBeVisible();
});

test("revoked organizer permission clears private moderation data", async ({ page }) => {
  const state = await setup(page, "ORGANIZER");
  state.management.requests = [{ id: "request", displayName: "Applicant", email: "private@example.test", note: "Club" }];
  await page.goto("/campus"); await expect(page.getByText("private@example.test")).toBeVisible();
  state.failManage = true; await page.getByRole("button", { name: "Refresh campus" }).click();
  await expect(page.getByRole("alert")).toContainText("Campus resource not found");
  await expect(page.getByText("private@example.test")).toHaveCount(0);
});

test("failed score reload clears stale standings and retries", async ({ page }) => {
  const state = await setup(page); await page.goto("/campus");
  await page.getByRole("button", { name: /Campus challenge/ }).click();
  await expect(page.getByRole("table", { name: "Campus leaderboard" })).toBeVisible();
  state.failBoard = true; await page.getByRole("button", { name: "Refresh my score" }).click();
  await expect(page.getByRole("alert")).toContainText("Board unavailable");
  await expect(page.getByRole("table", { name: "Campus leaderboard" })).toHaveCount(0);
  state.failBoard = false; await page.getByRole("button", { name: /Campus challenge/ }).click();
  await expect(page.getByRole("table", { name: "Campus leaderboard" })).toBeVisible();
});

test("suspension disables competition creation and enrollment", async ({ page }) => {
  const state = await setup(page, "ORGANIZER"); state.workspace.institution.suspended = true;
  await page.goto("/campus"); await expect(page.getByRole("button", { name: "Create competition", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: /Campus challenge/ }).click(); await page.getByRole("checkbox").check();
  await expect(page.getByRole("button", { name: "Join campus competition" })).toBeDisabled();
});

for (const theme of ["light", "dark"]) test(`campus fits mobile in ${theme} mode and is noindex`, async ({ page }) => {
  await setup(page, "ORGANIZER"); await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(value => localStorage.setItem("stoxsim-theme:learner", value), theme);
  await page.goto("/campus"); await page.getByRole("button", { name: /Campus challenge/ }).click();
  await expect(page.getByRole("table", { name: "Campus leaderboard" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-theme", theme);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
});
