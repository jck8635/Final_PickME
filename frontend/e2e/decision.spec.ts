import { test, expect } from "@playwright/test";
import { login, response } from "./support";

// Markdown 문법 및 공백 정규화
function normalizeText(text: string): string {
  return (
    text
      // Markdown 이미지 문법 제거
      .replace(/!\[([^\]]*)\]\([^)]+\)/g, "$1")

      // Markdown 링크 문법 제거
      .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1")

      // 제목 문법 제거
      .replace(/^\s{0,3}#{1,6}\s+/gm, "")

      // 인용문 문법 제거
      .replace(/^\s*>\s?/gm, "")

      // 목록 문법 제거
      .replace(/^\s*[-+*]\s+/gm, "")
      .replace(/^\s*\d+\.\s+/gm, "")

      // 강조 및 코드 문법 제거
      .replace(/[*_`~]/g, "")

      // 공백 및 줄바꿈 정규화
      .replace(/\s+/g, "")

      .trim()
  );
}

test("실제 AI 상담 응답", async ({ page }) => {
  test.skip(process.env.E2E_AI !== "yes", "실제 AI 호출은 선택 실행");

  // AI 응답 시간을 고려한 테스트 제한 시간
  test.setTimeout(360000);

  const question = "오늘 점심에 비빔밥과 국수 중 무엇을 먹을지 고민이에요.";

  // 1. 일반 회원 로그인
  await test.step("일반 회원 로그인", async () => {
    await login(page);

    await expect(page).not.toHaveURL(/\/login(?:[/?#]|$)/);
  });

  // 2. AI 상담 페이지 이동
  await test.step("AI 상담 페이지 이동", async () => {
    await page.goto("/chat", {
      waitUntil: "domcontentloaded",
    });

    await expect(page).toHaveURL(/\/chat(?:\?.*)?$/);

    // 로그인 정보 복구 대기
    await expect(
      page.getByRole("link", {
        name: "마이페이지",
        exact: true,
      }),
    ).toBeVisible({
      timeout: 30000,
    });

    await expect(page.locator("#user-input")).toBeVisible();
  });

  // 기존 AI 메시지 개수 확인
  const aiMessages = page.locator(".chat-message.ai");

  const beforeCount = await aiMessages.count();

  let reply = "";

  // 3. 실제 AI 상담 요청
  await test.step("AI 상담 요청 및 API 응답 확인", async () => {
    await page.locator("#user-input").fill(question);

    const pending = response(page, "/chat", "POST");

    await page.locator("#send-btn").click();

    const result = await pending;

    // HTTP 응답 성공 확인
    expect(result.ok(), `AI 상담 API status: ${result.status()}`).toBeTruthy();

    const data = await result.json();

    // 응답 데이터 구조 확인
    expect(typeof data.reply).toBe("string");

    // 빈 응답 여부 확인
    expect(data.reply.trim().length).toBeGreaterThan(0);

    reply = data.reply.trim();
  });

  // 4. 새로운 AI 메시지 표시 확인
  await test.step("AI 응답 화면 표시 확인", async () => {
    await expect
      .poll(async () => await aiMessages.count(), {
        timeout: 30000,
      })
      .toBeGreaterThan(beforeCount);

    await expect(aiMessages.last()).toBeVisible({
      timeout: 30000,
    });
  });

  // 5. API 응답과 실제 화면 내용 비교
  await test.step("AI 응답 내용 일치 확인", async () => {
    const expectedText = normalizeText(reply);

    expect(expectedText.length).toBeGreaterThan(0);

    await expect
      .poll(
        async () => {
          const renderedText = await aiMessages.last().innerText();

          return normalizeText(renderedText);
        },
        {
          timeout: 30000,
          message: "AI API 응답과 화면 출력 내용 비교",
        },
      )
      .toContain(expectedText);
  });
});
