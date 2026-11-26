import { test, expect } from "@playwright/test";
import { login, response, env, guard } from "./support";

test("로그인 → 새로고침 → 로그아웃 → 보호 화면 차단", async ({ page }) => {
  // 1. 일반 회원 로그인
  await login(page);

  // 2. 마이페이지 이동
  await page.goto("/mypage");

  // 3. 회원 정보 영역의 이메일 확인
  const email = page.locator("dd").filter({
    hasText: env("E2E_EMAIL"),
  });

  await expect(email).toHaveText(env("E2E_EMAIL"));
  await expect(email).toBeVisible();

  // 4. 기존 Refresh Token 확인
  const before = (await page.context().cookies()).find(
    (c) => c.name === "PICKME_REFRESH",
  );

  expect(before).toBeDefined();
  expect(before?.httpOnly).toBe(true);
  expect(before?.secure).toBe(true);

  // 5. 새로고침 및 토큰 재발급 확인
  const refresh = response(page, "/auth/refresh", "POST");

  await page.reload();

  const refreshResult = await refresh;

  expect(
    refreshResult.ok(),
    `Refresh API status: ${refreshResult.status()}`,
  ).toBeTruthy();

  // 6. 새로고침 후 로그인 유지 확인
  await expect(email).toHaveText(env("E2E_EMAIL"));
  await expect(email).toBeVisible();

  // 7. Refresh Token Rotation 확인
  const after = (await page.context().cookies()).find(
    (c) => c.name === "PICKME_REFRESH",
  );

  expect(after).toBeDefined();

  expect(Boolean(after && before && after.value !== before.value)).toBe(true);

  // 8. 로그아웃
  const logout = response(page, "/auth/logout", "POST");

  await page
    .getByRole("button", {
      name: "로그아웃",
      exact: true,
    })
    .click();

  const logoutResult = await logout;

  expect(
    logoutResult.ok(),
    `Logout API status: ${logoutResult.status()}`,
  ).toBeTruthy();

  // 9. 로그아웃 후 보호 페이지 접근 차단
  await page.goto("/mypage");

  await expect(page).toHaveURL(/\/login/);

  // 10. Refresh Token 쿠키 삭제 확인
  const cookies = await page.context().cookies();

  expect(cookies.some((c) => c.name === "PICKME_REFRESH")).toBe(false);
});

test("비로그인 마이페이지 차단", async ({ page }) => {
  guard();

  await page.goto("/mypage");

  await expect(page).toHaveURL(/\/login/);
});
