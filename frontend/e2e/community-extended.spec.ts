import { test, expect } from "@playwright/test";
import { session, postData } from "./extended-support";

test("API: 게시글 수정, 댓글 CRUD, 투표 변경 및 취소, 공감", async ({
  page,
}) => {
  const api = await session(page);

  const title = "오늘 점심 메뉴로 무엇이 좋을까요?";
  const updatedTitle = "친구와 함께 먹을 점심 메뉴를 추천해 주세요";

  const content = "채소를 좋아한다면 비빔밥이 좋을 것 같아요.";
  const updatedContent = "따뜻한 국물이 먹고 싶다면 국밥도 좋을 것 같아요.";

  // 1. 게시글 생성
  const { id } = await api(
    "POST",
    "/community/posts",
    {
      ...postData(title),
      title,
      content:
        "오늘 친구와 점심을 먹으려고 하는데 비빔밥과 국밥 중에서 고민하고 있습니다.",
    },
    201,
  );

  const path = `/community/posts/${id}`;

  try {
    // 2. 게시글 수정
    await api("PUT", path, {
      ...postData(updatedTitle),
      title: updatedTitle,
      content:
        "친구와 함께 점심을 먹으려고 합니다. 부담 없이 먹을 수 있는 메뉴를 추천해 주세요.",
    });

    let post = await api("GET", path);

    expect(post.title).toBe(updatedTitle);
    expect(post.mine).toBe(true);

    const [a, b] = post.options;

    // 3. 첫 번째 선택지 투표
    await api("POST", `${path}/votes/${a.id}`);

    post = await api("GET", path);

    expect(post.myOptionId).toBe(a.id);
    expect(post.totalVotes).toBe(1);

    // 4. 두 번째 선택지로 투표 변경
    await api("POST", `${path}/votes/${b.id}`);

    post = await api("GET", path);

    expect(post.myOptionId).toBe(b.id);
    expect(post.totalVotes).toBe(1);

    // 5. 투표 취소
    await api("DELETE", `${path}/votes`);

    post = await api("GET", path);

    expect(post.myOptionId).toBeNull();
    expect(post.totalVotes).toBe(0);

    // 6. 공감 등록
    expect((await api("POST", `${path}/like`)).liked).toBe(true);

    expect((await api("GET", path)).likeCount).toBe(1);

    // 7. 공감 취소
    expect((await api("POST", `${path}/like`)).liked).toBe(false);

    expect((await api("GET", path)).likeCount).toBe(0);

    // 8. 댓글 작성
    await api("POST", `${path}/comments`, { content }, 201);

    post = await api("GET", path);

    const comment = post.comments.find((c: any) => c.content === content);

    expect(comment).toBeDefined();
    expect(comment.mine).toBe(true);

    const cp = `${path}/comments/${comment.id}`;

    // 9. 댓글 수정
    await api("PUT", cp, {
      content: updatedContent,
    });

    expect(
      (await api("GET", path)).comments.find((c: any) => c.id === comment.id)
        .content,
    ).toBe(updatedContent);

    // 10. 댓글 삭제
    await api("DELETE", cp);

    expect(
      (await api("GET", path)).comments.some((c: any) => c.id === comment.id),
    ).toBe(false);
  } finally {
    // 11. 테스트용 게시글 삭제
    await api("DELETE", path);
  }
});
