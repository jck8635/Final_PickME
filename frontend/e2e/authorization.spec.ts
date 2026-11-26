import { test, expect, type Page } from "@playwright/test";
import { session, unique, postData, decisionData } from "./extended-support";
import { guard, env, response } from "./support";

// 테스트 계정 B 로그인 및 API 요청 함수
async function sessionB(page: Page) {
  guard();

  await page.goto("/login");

  await page.locator("#email").fill(env("E2E_EMAIL_B"));
  await page.locator("#password").fill(env("E2E_PASSWORD_B"));

  const pending = response(page, "/auth/login", "POST");

  await page
    .getByRole("button", {
      name: "로그인",
      exact: true,
    })
    .click();

  const r = await pending;

  expect(r.ok(), "계정 B 로그인 실패").toBeTruthy();

  await expect(
    page.getByRole("link", {
      name: "마이페이지",
      exact: true,
    }),
  ).toBeVisible();

  const token = (await r.json()).accessToken;

  expect(typeof token).toBe("string");

  return async (method: string, path: string, data?: unknown, status = 200) => {
    const cookie = (await page.context().cookies()).find(
      (c) => c.name === "XSRF-TOKEN",
    );

    const headers: Record<string, string> = {
      Authorization: `Bearer ${token}`,
    };

    if (cookie) {
      headers["X-XSRF-TOKEN"] = decodeURIComponent(cookie.value);
    }

    const res = await page.request.fetch(`/api${path}`, {
      method,
      headers,
      ...(data === undefined ? {} : { data }),
      timeout: 320000,
    });

    const body = await res.text();

    expect(res.status(), `계정 B: ${method} ${path}\n${body}`).toBe(status);

    if (!body) return null;

    return JSON.parse(body);
  };
}

// 각 테스트에서 계정 A와 B를 독립적으로 사용
test.beforeEach(() => {
  guard();

  expect(env("E2E_EMAIL").toLowerCase()).not.toBe(
    env("E2E_EMAIL_B").toLowerCase(),
  );
});

// ==================================================
// 1. 다른 사용자의 게시글 및 댓글 권한 검증
// ==================================================

test("권한: 다른 사용자의 게시글 및 댓글 수정·삭제 차단", async ({
  page,
  browser,
}) => {
  const apiA = await session(page);

  const contextB = await browser.newContext({
    baseURL: "http://localhost:5173",
  });

  const pageB = await contextB.newPage();

  try {
    const apiB = await sessionB(pageB);

    const title = "친구와 함께 먹을 점심 메뉴가 고민입니다";

    const { id } = await apiA("POST", "/community/posts", postData(title), 201);

    const path = `/community/posts/${id}`;

    try {
      const content = "저는 따뜻한 국수가 좋을 것 같아요.";

      await apiA("POST", `${path}/comments`, { content }, 201);

      const postA = await apiA("GET", path);

      const comment = postA.comments.find((c: any) => c.content === content);

      expect(postA.mine).toBe(true);
      expect(comment).toBeDefined();
      expect(comment.mine).toBe(true);

      const cp = `${path}/comments/${comment.id}`;

      // B는 공개 게시글을 조회할 수 있음
      const postB = await apiB("GET", path);

      expect(postB.title).toBe(title);
      expect(postB.mine).toBe(false);

      expect(postB.comments.find((c: any) => c.id === comment.id).mine).toBe(
        false,
      );

      // B의 게시글 수정 차단
      await apiB("PUT", path, postData("오늘 저녁에는 무엇을 먹을까요?"), 403);

      // B의 게시글 삭제 차단
      await apiB("DELETE", path, undefined, 403);

      // B의 댓글 수정 차단
      await apiB("PUT", cp, { content: "비빔밥도 맛있을 것 같아요." }, 403);

      // B의 댓글 삭제 차단
      await apiB("DELETE", cp, undefined, 403);

      // 원본 데이터가 변경되지 않았는지 확인
      const original = await apiA("GET", path);

      expect(original.title).toBe(title);

      expect(
        original.comments.find((c: any) => c.id === comment.id).content,
      ).toBe(content);
    } finally {
      await apiA("DELETE", path);
    }
  } finally {
    await contextB.close();
  }
});

// ==================================================
// 2. 다른 사용자의 고민기록 및 회고 권한 검증
// ==================================================

test("권한: 다른 사용자의 고민기록 조회·삭제·북마크·회고 변경 차단", async ({
  page,
  browser,
}) => {
  const apiA = await session(page);

  const contextB = await browser.newContext({
    baseURL: "http://localhost:5173",
  });

  const pageB = await contextB.newPage();

  try {
    const apiB = await sessionB(pageB);

    const title = unique();

    const { decisionId: id } = await apiA(
      "POST",
      "/decisions/save",
      decisionData(title),
    );

    const path = `/history/${id}`;

    try {
      // A의 회고 작성
      await apiA("PUT", `${path}/retrospective`, {
        actualChoice: "비빔밥",
        satisfactionScore: 5,
        retrospectiveNote: "만족스러운 선택이었습니다.",
      });

      const original = await apiA("GET", path);

      expect(original.title).toBe(title);

      // B의 고민기록 조회 차단
      await apiB("GET", path, undefined, 404);

      // B의 고민기록 삭제 차단
      await apiB("DELETE", path, undefined, 404);

      // B의 북마크 변경 차단
      await apiB("POST", `${path}/bookmark`, undefined, 404);

      // B의 회고 수정 차단
      await apiB(
        "PUT",
        `${path}/retrospective`,
        {
          actualChoice: "국수",
          satisfactionScore: 1,
          retrospectiveNote: "다른 사용자의 수정 요청",
        },
        404,
      );

      // B의 회고 초기화 차단
      await apiB(
        "PUT",
        `${path}/retrospective`,
        {
          actualChoice: "",
          satisfactionScore: null,
          retrospectiveNote: "",
        },
        404,
      );

      // 원본 데이터 유지 확인
      const after = await apiA("GET", path);

      expect(after.title).toBe(original.title);

      expect(after.bookmarked).toBe(original.bookmarked);

      expect(after.actualChoice).toBe("비빔밥");
      expect(after.satisfactionScore).toBe(5);

      expect(after.retrospectiveNote).toBe("만족스러운 선택이었습니다.");
    } finally {
      await apiA("DELETE", path);
    }
  } finally {
    await contextB.close();
  }
});

// ==================================================
// 3. 사용자별 고민기록 키워드 검색 격리
// ==================================================

test("권한: 다른 사용자의 고민기록 키워드 검색 결과 미노출", async ({
  page,
  browser,
}) => {
  const apiA = await session(page);

  const contextB = await browser.newContext({
    baseURL: "http://localhost:5173",
  });

  const pageB = await contextB.newPage();

  try {
    const apiB = await sessionB(pageB);

    const title = unique();

    const { decisionId: id } = await apiA(
      "POST",
      "/decisions/save",
      decisionData(title),
    );

    try {
      const searchPath = `/history/page?q=${encodeURIComponent(title)}`;

      // A는 자신의 기록을 검색할 수 있음
      const resultA = await apiA("GET", searchPath);

      expect(resultA.items.some((x: any) => x.id === id)).toBe(true);

      // B는 A의 기록을 검색할 수 없음
      const resultB = await apiB("GET", searchPath);

      expect(resultB.items.some((x: any) => x.id === id)).toBe(false);

      expect(resultB.items.some((x: any) => x.title === title)).toBe(false);
    } finally {
      await apiA("DELETE", `/history/${id}`);
    }
  } finally {
    await contextB.close();
  }
});

// ==================================================
// 4. 사용자별 RAG 유사 결정 검색 격리
// ==================================================

test("권한: 다른 사용자의 RAG 유사 결정 검색 결과 미노출", async ({
  page,
  browser,
}) => {
  test.skip(process.env.E2E_AI !== "yes", "실제 AI 호출은 선택 실행");

  test.setTimeout(600000);

  const apiA = await session(page);

  const contextB = await browser.newContext({
    baseURL: "http://localhost:5173",
  });

  const pageB = await contextB.newPage();

  try {
    const apiB = await sessionB(pageB);

    const title = unique() + " 비빔밥과 국수 중 점심 메뉴 선택";

    const { decisionId: id } = await apiA(
      "POST",
      "/decisions/save",
      decisionData(title),
    );

    try {
      // A의 기록에 실제 임베딩 생성
      const embedding = await apiA("POST", `/history/${id}/embedding`);

      expect(embedding.decisionId).toBe(id);
      expect(embedding.dimension).toBe(768);
      expect(embedding.status).toBe("saved");

      // A는 자신의 기록을 유사 검색할 수 있음
      const resultA = await apiA("POST", "/history/similar", { text: title });

      expect(resultA.results.some((x: any) => x.decisionId === id)).toBe(true);

      // B는 동일한 검색어로 검색해도
      // A의 기록을 조회할 수 없어야 함
      const resultB = await apiB("POST", "/history/similar", { text: title });

      expect(Array.isArray(resultB.results)).toBe(true);

      expect(resultB.results.some((x: any) => x.decisionId === id)).toBe(false);

      // B에게 반환된 모든 검색 결과는
      // B가 실제로 접근 가능한 기록이어야 함
      for (const row of resultB.results) {
        await apiB("GET", `/history/${row.decisionId}`);
      }

      // A의 고유 제목이 요약에 노출되지 않는지 확인
      expect(String(resultB.summary ?? "")).not.toContain(title);
    } finally {
      await apiA("DELETE", `/history/${id}`);
    }
  } finally {
    await contextB.close();
  }
});

// ==================================================
// 5. 일반 회원의 관리자 API 접근 차단
// ==================================================

test("권한: 일반 회원의 관리자 API 접근 차단", async ({ page, browser }) => {
  const apiA = await session(page);

  const contextB = await browser.newContext({
    baseURL: "http://localhost:5173",
  });

  const pageB = await contextB.newPage();

  try {
    const apiB = await sessionB(pageB);

    const adminPaths = [
      "/admin/overview",
      "/admin/members",
      "/admin/posts",
      "/admin/reports",
    ];

    for (const path of adminPaths) {
      // 일반 회원 A 접근 차단
      await apiA("GET", path, undefined, 403);

      // 일반 회원 B 접근 차단
      await apiB("GET", path, undefined, 403);
    }
  } finally {
    await contextB.close();
  }
});
