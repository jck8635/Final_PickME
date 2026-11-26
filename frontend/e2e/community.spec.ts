import { test, expect } from "@playwright/test";
import { login, response } from "./support";

test("게시글 작성 → 상세 조회 → 삭제", async ({ page }) => {
  // 1. 일반 회원 로그인
  await test.step("일반 회원 로그인", async () => {
    await login(page);

    await expect(page).not.toHaveURL(/\/login(?:[/?#]|$)/);

    await expect(
      page.getByRole("link", {
        name: "마이페이지",
        exact: true,
      }),
    ).toBeVisible();
  });

  // 2. 커뮤니티 페이지 이동 및 인증 복구
  await test.step("커뮤니티 이동 및 인증 확인", async () => {
    await page.goto("/community", {
      waitUntil: "domcontentloaded",
    });

    await expect(page).toHaveURL(/\/community(?:\?.*)?$/);

    await expect(
      page.getByRole("link", {
        name: "마이페이지",
        exact: true,
      }),
    ).toBeVisible({
      timeout: 30000,
    });

    await expect(
      page.getByRole("button", {
        name: /고민 투표 올리기/,
      }),
    ).toBeEnabled();
  });

  // 테스트 게시글 제목
  const title = `E2E 점심 메뉴 ${Date.now()}`;

  const dialog = page.locator("dialog.post-dialog");

  // 3. 게시글 작성
  await test.step("게시글 작성", async () => {
    await page
      .getByRole("button", {
        name: /고민 투표 올리기/,
      })
      .click();

    await expect(page).toHaveURL(/\/community(?:\?.*)?$/);

    await expect(dialog).toBeVisible({
      timeout: 15000,
    });

    await expect(dialog).toHaveAttribute("open", "");

    await dialog.getByPlaceholder("고민을 한 줄로 표현해보세요").fill(title);

    await dialog
      .getByPlaceholder("상황과 배경을 적어주세요")
      .fill("점심에 비빔밥과 국수 중 무엇을 먹을지 고민입니다.");

    await dialog
      .getByPlaceholder("선택지 1", {
        exact: true,
      })
      .fill("비빔밥");

    await dialog
      .getByPlaceholder("선택지 2", {
        exact: true,
      })
      .fill("국수");

    const created = response(page, "/community/posts", "POST");

    await dialog
      .getByRole("button", {
        name: "투표 올리기",
        exact: true,
      })
      .click();

    const createdResult = await created;

    expect(
      createdResult.ok(),
      `게시글 작성 API: ${createdResult.status()}`,
    ).toBeTruthy();

    await expect(dialog).not.toBeVisible();
  });

  // 4. 게시글 상세 조회
  await test.step("게시글 상세 조회", async () => {
    const postLink = page.locator(".community-feed").getByRole("link", {
      name: title,
      exact: true,
    });

    await expect(postLink).toBeVisible();

    await postLink.click();

    await expect(
      page.getByRole("heading", {
        name: title,
        exact: true,
      }),
    ).toBeVisible();

    await expect(page).toHaveURL(/\/community\/posts\/\d+/);
  });

  // 5. 게시글 ID 확인
  const id = new URL(page.url()).pathname.split("/").pop();

  expect(id).toBeTruthy();

  // 6. 게시글 삭제
  await test.step("게시글 삭제", async () => {
    page.once("dialog", async (confirmDialog) => {
      await confirmDialog.accept();
    });

    const deleted = response(page, `/community/posts/${id}`, "DELETE");

    await page
      .locator(".post-owner-actions")
      .getByRole("button", {
        name: "삭제",
        exact: true,
      })
      .click();

    const deletedResult = await deleted;

    expect(
      deletedResult.ok(),
      `게시글 삭제 API: ${deletedResult.status()}`,
    ).toBeTruthy();

    await expect(page).toHaveURL(/\/community(?:\?.*)?$/);
  });

  // 7. 게시글 삭제 결과 확인
  await test.step("게시글 삭제 결과 확인", async () => {
    const feed = page.locator(".community-feed");

    // 목록이 비어 있어도 DOM에 존재하면 정상
    await expect(feed).toBeAttached();

    // 삭제된 게시글이 목록에 없는지 확인
    await expect(
      feed.getByRole("link", {
        name: title,
        exact: true,
      }),
    ).toHaveCount(0);

    // 삭제된 게시글의 API 직접 조회
    const deletedPost = await page.request.get(`/api/community/posts/${id}`);

    expect(
      deletedPost.status(),
      "삭제된 게시글은 다시 조회할 수 없어야 합니다.",
    ).toBe(404);
  });
});
