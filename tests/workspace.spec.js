import { test, expect } from "@playwright/test";
import { env } from "node:process";

const user = { id: "10000000-0000-4000-8000-000000000001", name: "Alex Morgan", email: "alex@example.test" };
const teammate = { id: "10000000-0000-4000-8000-000000000002", name: "Jordan Lee", email: "jordan@example.test" };
const teamId = "20000000-0000-4000-8000-000000000001";
const projectId = "30000000-0000-4000-8000-000000000001";
const boardPath = `/teams/${teamId}/projects/${projectId}/board`;
const timestamp = "2026-09-22T10:00:00Z";

function overview(tasks) {
  const open = tasks.filter((task) => task.status !== "DONE");
  return { total: tasks.length, open: open.length, completed: tasks.length - open.length,
    highPriorityOpen: open.filter((task) => task.priority === "HIGH").length,
    unassignedOpen: open.filter((task) => !task.assigneeId).length, staleOpen: 0,
    byStatus: Object.fromEntries(["TODO", "IN_PROGRESS", "IN_REVIEW", "DONE"].map((status) => [status, tasks.filter((task) => task.status === status).length])),
    openByPriority: Object.fromEntries(["LOW", "MEDIUM", "HIGH"].map((priority) => [priority, open.filter((task) => task.priority === priority).length])),
  };
}

async function fixture(page, role = "OWNER") {
  const teams = [{ id: teamId, name: "Product engineering", role, createdAt: timestamp }];
  const projects = [{ id: projectId, teamId, name: "Developer experience", description: "A smoother path from the first commit to production.", createdAt: timestamp, updatedAt: timestamp, archivedAt: null },
    { id: "30000000-0000-4000-8000-000000000002", teamId, name: "Design system", description: "Shared foundations for a consistent product.", createdAt: timestamp, updatedAt: timestamp, archivedAt: null },
    { id: "30000000-0000-4000-8000-000000000003", teamId, name: "Previous release", createdAt: timestamp, updatedAt: timestamp, archivedAt: timestamp }];
  const members = [user, teammate].map((member, index) => ({ userId: member.id, name: member.name, email: member.email, role: index ? "MEMBER" : "OWNER", joinedAt: timestamp }));
  const tasks = [
    { title: "Polish the onboarding checklist", description: "Simplify first-run setup", status: "TODO", priority: "HIGH", assigneeId: user.id },
    { title: "Improve build feedback", description: "Surface actionable errors in CI", status: "IN_PROGRESS", priority: "HIGH", assigneeId: user.id },
    { title: "Review component documentation", description: "Check accessibility examples", status: "IN_REVIEW", priority: "MEDIUM", assigneeId: teammate.id },
    { title: "Ship workspace navigation", description: "Release the updated navigation", status: "DONE", priority: "LOW", assigneeId: user.id },
    { title: "Add keyboard focus tests", description: "Cover project dialogs", status: "TODO", priority: "MEDIUM", assigneeId: null },
  ].map((task, index) => ({ ...task, id: `40000000-0000-4000-8000-00000000000${index}`, projectId, createdBy: user.id, createdAt: timestamp, updatedAt: timestamp }));
  const state = { teams, projects, tasks, members, fail: "", writes: [] };
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const body = request.postDataJSON();
    if (state.fail && path.endsWith(state.fail)) return route.fulfill({ status: 503, json: { detail: "Temporarily unavailable" } });
    if (method !== "GET") state.writes.push({ path, method, body });
    let result;
    if (path.endsWith("/auth/login")) result = { user, token: "ui-fixture-only", expiresAt: new Date(Date.now() + 3600000).toISOString() };
    else if (path.endsWith("/auth/me")) result = { id: user.id };
    else if (path.endsWith("/auth/signup")) result = { ...user, email: body.email };
    else if (path.endsWith("/dashboard")) {
      const active = projects.filter((project) => !project.archivedAt);
      const scoped = tasks.filter((task) => active.some((project) => project.id === task.projectId) && (!url.searchParams.get("projectId") || task.projectId === url.searchParams.get("projectId")));
      const personal = scoped.filter((task) => task.assigneeId === user.id);
      result = { teamId, generatedAt: timestamp, archivedProjects: projects.length - active.length, personal: overview(personal), team: overview(scoped),
        projects: active.map((project) => ({ ...project, ...overview(tasks.filter((task) => task.projectId === project.id)) })),
        workload: members.map((member) => ({ ...member, open: 2, inProgress: 1, inReview: 0 })),
        myOpenTasks: personal.filter((task) => task.status !== "DONE").map((task) => ({ ...task, projectName: projects[0].name })) };
    } else if (path.endsWith("/members")) result = members;
    else if (path.endsWith("/archive")) { result = projects.find((project) => path.includes(project.id)); result.archivedAt = body.archived ? timestamp : null; }
    else if (path.includes("/tasks")) {
      if (method === "GET") result = tasks;
      else if (method === "POST") { result = { ...tasks[0], ...body, id: "40000000-0000-4000-8000-000000000099" }; tasks.unshift(result); }
      else { result = tasks.find((task) => path.includes(task.id)); Object.assign(result, body); }
    } else if (path.endsWith("/projects")) {
      if (method === "POST") { result = { ...projects[0], ...body, id: "30000000-0000-4000-8000-000000000099" }; projects.unshift(result); }
      else result = projects;
    } else if (method === "PUT" && path.includes("/projects/")) { result = projects.find((project) => path.includes(project.id)); Object.assign(result, body); }
    else if (path.endsWith("/teams")) {
      if (method === "POST") { result = { ...teams[0], ...body, id: "20000000-0000-4000-8000-000000000099" }; teams.unshift(result); }
      else result = teams;
    } else return route.fulfill({ status: 404, json: { detail: "Unconfigured UI fixture" } });
    await route.fulfill({ status: method === "POST" ? 201 : 200, json: result });
  });
  return state;
}

async function signIn(page, path = "/login") {
  await page.goto(path);
  await page.getByLabel("Email address").fill(user.email);
  await page.getByLabel("Password", { exact: true }).fill("fixture password only");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}

async function navigateClient(page, path) {
  await page.evaluate((destination) => { history.pushState({}, "", destination); dispatchEvent(new PopStateEvent("popstate")); }, path);
}

async function noOverflow(page) {
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  const outside = await page.locator("button, input, select, h1, h2, h3").evaluateAll((elements) => elements.filter((element) => {
    const rect = element.getBoundingClientRect();
    return rect.width && rect.height && (rect.left < -1 || rect.right > innerWidth + 1);
  }).map((element) => element.textContent || element.getAttribute("aria-label")));
  expect(outside).toEqual([]);
}

test("public landing, login default, legacy board and signed-in home", async ({ page }) => {
  await fixture(page);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "DevPulse.", exact: true })).toBeVisible();
  await signIn(page);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Work by phase" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Primary navigation" }).getByRole("link")).toHaveCount(2);
  if (env.CAPTURE_PREVIEW === "1") {
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: "public/workspace-preview.png" });
  }
  for (const path of ["/", "/home", "/board", "/login", "/signup"]) {
    await navigateClient(page, path);
    await expect(page).toHaveURL(/\/dashboard$/);
  }
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login$/);
});

test("signup validation, password visibility and invitation return", async ({ page }) => {
  const state = await fixture(page);
  const invitation = `/invite#${"a".repeat(43)}`;
  await page.goto(invitation);
  await page.getByRole("link", { name: "Create account", exact: true }).click();
  await page.getByLabel("Full name").fill("Test User");
  await page.getByLabel("Email address").fill(user.email);
  await page.getByLabel("Password", { exact: true }).fill("fixture password only");
  await page.getByLabel("Confirm password", { exact: true }).fill("mismatched password");
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText("Passwords do not match.");
  expect(state.writes).toHaveLength(0);
  await page.getByLabel("Confirm password", { exact: true }).fill("fixture password only");
  await page.getByRole("button", { name: "Show password", exact: true }).click();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("type", "text");
  await page.getByRole("button", { name: "Hide password", exact: true }).click();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("type", "password");
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Account created successfully.");
  await expect(page.getByLabel("Email address")).toHaveValue(user.email);
  await page.getByLabel("Password", { exact: true }).fill("fixture password only");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/invite#${"a".repeat(43)}$`));
  await expect(page.getByRole("button", { name: "Join team", exact: true })).toBeVisible();
});

test("team and project creation/search lead to the correct board", async ({ page }) => {
  await fixture(page);
  await signIn(page, "/team");
  await page.locator("summary").click();
  await page.getByLabel("Team name").fill("Interface team");
  await page.getByRole("button", { name: "Create team", exact: true }).click();
  await expect(page.locator(".team-row")).toHaveCount(2);
  await page.getByRole("searchbox", { name: "Search teams" }).fill("interface");
  await expect(page.locator(".team-row")).toHaveCount(1);
  await page.getByRole("searchbox", { name: "Search teams" }).fill("");
  await page.getByRole("link", { name: "View projects for Product engineering" }).click();
  await page.locator("summary").click();
  await page.getByLabel("Project name").fill("Release planning");
  await page.getByLabel("Description (optional)").fill("A focused delivery plan");
  await page.getByRole("button", { name: "Create project", exact: true }).click();
  await expect(page.locator(".project-row")).toHaveCount(3);
  await page.getByRole("searchbox", { name: "Search projects" }).fill("shared foundations");
  await expect(page.locator(".project-row")).toHaveCount(1);
  await page.getByRole("searchbox", { name: "Search projects" }).fill("");
  await page.getByRole("link", { name: "Developer experience", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`${boardPath}$`));
  await expect(page.getByRole("link", { name: "Teams & projects", exact: true })).toHaveAttribute("aria-current", "page");
});

test("board filters compose, reset, and preserve task writes", async ({ page }) => {
  const state = await fixture(page);
  await signIn(page, boardPath);
  await expect(page.locator(".task-card")).toHaveCount(5);
  await page.getByRole("searchbox", { name: "Search tasks" }).fill("FIRST-run");
  await expect(page.locator(".task-card")).toHaveCount(1);
  await page.getByRole("button", { name: "Clear task filters" }).click();
  await page.getByRole("combobox", { name: "Filter by assignee" }).selectOption("me");
  await expect(page.locator(".task-card")).toHaveCount(3);
  await page.getByRole("combobox", { name: "Filter by priority" }).selectOption("HIGH");
  await expect(page.locator(".task-card")).toHaveCount(2);
  await page.getByRole("combobox", { name: "Filter by assignee" }).selectOption("unassigned");
  await expect(page.locator(".task-card")).toHaveCount(0);
  await expect(page.getByText("No matching tasks", { exact: true })).toHaveCount(4);
  await page.getByRole("button", { name: "Clear task filters" }).click();
  const status = page.getByRole("combobox", { name: "Status for Improve build feedback", exact: true });
  await status.selectOption("IN_REVIEW");
  await expect(status).toBeFocused();
  await expect(page.getByRole("region", { name: "In Review", exact: true }).locator(".task-card")).toHaveCount(2);
  await page.getByRole("button", { name: "New task", exact: true }).click();
  await page.getByLabel("Title", { exact: true }).fill("Check responsive layouts");
  await page.getByRole("button", { name: "Create task", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator(".task-card")).toHaveCount(6);
  expect(state.writes.filter((write) => write.path.includes("/tasks"))).toHaveLength(2);
});

test("member controls, archived read-only and retry states", async ({ page }) => {
  const state = await fixture(page, "MEMBER");
  await signIn(page, `/teams/${teamId}/projects`);
  await expect(page.getByRole("heading", { name: "Projects", exact: true })).toBeVisible();
  await expect(page.locator("summary")).toHaveCount(0);
  await expect(page.getByRole("button", { name: /Settings for/ })).toHaveCount(0);
  await page.getByRole("combobox", { name: "Project state" }).selectOption("archived");
  await page.getByRole("link", { name: "Previous release", exact: true }).click();
  await expect(page.getByRole("button", { name: "New task", exact: true })).toBeDisabled();
  await expect(page.getByRole("combobox", { name: /Status for/ }).first()).toBeDisabled();
  state.fail = "/tasks";
  await page.getByRole("button", { name: "Refresh board" }).click();
  await expect(page.getByRole("alert")).toHaveText("Temporarily unavailable");
  state.fail = "";
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByRole("button", { name: "New task", exact: true })).toBeDisabled();
});

for (const width of [1440, 1024, 390, 320]) {
  test(`responsive screens at ${width}px`, async ({ page }, testInfo) => {
    const height = width === 320 ? 568 : 900;
    await page.setViewportSize({ width, height });
    await fixture(page);
    for (const path of ["/", "/login", "/signup"]) {
      await page.goto(path);
      await page.evaluate(() => document.fonts.ready);
      await noOverflow(page);
      await expect(page.locator(".brand img").first()).toHaveJSProperty("naturalWidth", 48);
      if (path === "/") {
        await expect.poll(() => page.locator(".landing-product-background").evaluate((image) => image.naturalWidth)).toBeGreaterThan(0);
        const nextSection = await page.locator(".landing-principles").boundingBox();
        expect(nextSection.y).toBeLessThan(height);
        await page.screenshot({ path: testInfo.outputPath("landing.png") });
      }
    }
    await page.screenshot({ path: testInfo.outputPath("signup.png") });
    await signIn(page);
    await expect(page.getByRole("heading", { name: "Work by phase" })).toBeVisible();
    await noOverflow(page);
    await page.screenshot({ path: testInfo.outputPath("dashboard.png"), fullPage: true });
    await page.getByRole("link", { name: "Teams & projects", exact: true }).click();
    await expect(page.locator(".team-row")).toHaveCount(1);
    await noOverflow(page);
    await page.getByRole("button", { name: "View members of Product engineering" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await noOverflow(page);
    await page.keyboard.press("Escape");
    await page.getByRole("link", { name: "View projects for Product engineering" }).click();
    await expect(page.locator(".project-row")).toHaveCount(2);
    await noOverflow(page);
    await page.getByRole("link", { name: "Developer experience", exact: true }).click();
    await expect(page.locator(".task-card")).toHaveCount(5);
    await noOverflow(page);
    await page.screenshot({ path: testInfo.outputPath("board.png"), fullPage: true });
    await page.getByRole("button", { name: "New task", exact: true }).click();
    await page.getByLabel("Title", { exact: true }).fill("Long".repeat(50));
    await noOverflow(page);
    await page.getByRole("button", { name: "Create task", exact: true }).click();
    await expect(page.locator(".task-card")).toHaveCount(6);
    await noOverflow(page);
  });
}