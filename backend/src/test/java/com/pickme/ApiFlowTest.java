package com.pickme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.pickme.ai.AiClient;
import com.pickme.member.LegacyAwarePasswordEncoderTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AiClient aiClient;

    // ---------- helpers ----------

    private MvcResult postJson(String url, Object body, MockHttpSession session) throws Exception {
        var req = post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (session != null) {
            req.session(session);
        }
        return mvc.perform(req).andReturn();
    }

    private MockHttpSession signupAndLogin(String email, String nickname) throws Exception {
        MvcResult signup = postJson("/api/auth/signup",
                new Signup(email, nickname, "password123"), null);
        assertThat(signup.getResponse().getStatus()).isEqualTo(201);
        return login(email, "password123");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult res = postJson("/api/auth/login", new Login(email, password), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    private JsonNode json(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    record Signup(String email, String nickname, String password) {
    }

    record Login(String email, String password) {
    }

    // ---------- 회원 ----------

    @Test
    void signupLoginMeLogout() throws Exception {
        MockHttpSession session = signupAndLogin("Member@Example.com", "멤버1");

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("member@example.com"))
                .andExpect(jsonPath("$.user.nickname").value("멤버1"));

        // 중복 가입
        MvcResult dup = postJson("/api/auth/signup", new Signup("member@example.com", "다른닉", "password123"), null);
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(dup).get("message").asString()).isEqualTo("이미 가입된 이메일입니다.");

        // 잘못된 비밀번호
        MvcResult bad = postJson("/api/auth/login", new Login("member@example.com", "wrong-pass"), null);
        assertThat(bad.getResponse().getStatus()).isEqualTo(401);

        postJson("/api/auth/logout", null, session);
        mvc.perform(get("/api/auth/me")).andExpect(jsonPath("$.user").isEmpty());
    }

    @Test
    void validationMessagesMatchFlask() throws Exception {
        MvcResult res = postJson("/api/auth/signup", new Signup("not-an-email", "닉", "password123"), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(res).get("message").asString()).isEqualTo("올바른 이메일 형식으로 입력해주세요.");

        res = postJson("/api/auth/signup", new Signup("a@b.co", "닉", "short"), null);
        assertThat(json(res).get("message").asString())
                .isEqualTo("비밀번호는 공백만 사용하지 않고 8자 이상 입력해주세요.");
    }

    @Test
    void legacyFlaskUserCanLogIn() throws Exception {
        jdbc.update("insert into \"user\" (email, password_hash, nickname) values (?, ?, ?)",
                "legacy@example.com", LegacyAwarePasswordEncoderTest.SCRYPT, "레거시");
        MockHttpSession session = login("legacy@example.com", "pickme-test-pw");
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.user.nickname").value("레거시"));
    }

    @Test
    void changeNicknameAndPassword() throws Exception {
        MockHttpSession session = signupAndLogin("mypage@example.com", "마이페이지");

        mvc.perform(patch("/api/members/me/nickname").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"새닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임"));

        mvc.perform(patch("/api/members/me/password").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpass456\","
                        + "\"passwordConfirmation\":\"newpass456\"}"))
                .andExpect(status().isOk());

        login("mypage@example.com", "newpass456");
    }

    // ---------- 보안 ----------

    @Test
    void protectedApisRequireLoginAndCsrf() throws Exception {
        mvc.perform(get("/api/history")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        // CSRF 토큰 없이 POST → 403
        // (XSRF-TOKEN 쿠키 발급은 spring-security-test 의 csrf() 가 저장소를 바꿔치기해서 실제 서버로 확인)
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // ---------- AI 상담 ----------

    @Test
    void chatForwardsToAiServer() throws Exception {
        when(aiClient.chat(eq("짬뽕 vs 짜장면"), eq("logic"))).thenReturn(new AiClient.ChatResult(
                "정리했어요.", new AiClient.ParsedDecision("점심", "음식/식사", List.of("짬뽕", "짜장면")), false));

        mvc.perform(post("/api/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"짬뽕 vs 짜장면\",\"tone\":\"logic\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("정리했어요."))
                .andExpect(jsonPath("$.parsedData.options[1]").value("짜장면"))
                .andExpect(jsonPath("$.parsedData.content").value("짬뽕 vs 짜장면"))
                .andExpect(jsonPath("$.isCrisis").value(false));
    }

    // ---------- 결정 도우미 + 고민 기록 ----------

    @Test
    void consultSavesHistoryForLoggedInUser() throws Exception {
        when(aiClient.consult(any())).thenReturn("짬뽕을 추천해요.");
        MockHttpSession session = signupAndLogin("history@example.com", "기록왕");

        String body = """
                {"categories":["음식/식사","없는카테고리"],"style":"fact","title":"점심 고민",
                 "content":"점심 뭐 먹지","conditions":["시간이 급함"],"conditionsFree":"1만원 이내",
                 "options":[{"title":"짬뽕","note":"얼큰"},{"title":"짜장면","note":""},{"title":"  ","note":""}],
                 "criteria":[{"name":"비용 (예산)","weight":"5"},{"name":"시간 효율","weight":"보류"}]}
                """;
        MvcResult res = mvc.perform(post("/api/decisions/consult").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(res).get("result").asString()).isEqualTo("짬뽕을 추천해요.");
        int id = json(res).get("decisionId").asInt();

        ArgumentCaptor<AiClient.DecisionRequest> captor = ArgumentCaptor.forClass(AiClient.DecisionRequest.class);
        Mockito.verify(aiClient).consult(captor.capture());
        AiClient.DecisionRequest sent = captor.getValue();
        assertThat(sent.categories()).containsExactly("음식/식사");
        assertThat(sent.conditions()).isEqualTo("시간이 급함, 1만원 이내");
        assertThat(sent.options()).hasSize(2);
        assertThat(sent.criteria()).isEqualTo("비용 (예산)(5점), 시간 효율(보류)");

        // 기록 목록
        MvcResult list = mvc.perform(get("/api/history").session(session)).andReturn();
        JsonNode item = json(list).get(0);
        assertThat(item.get("id").asInt()).isEqualTo(id);
        assertThat(item.get("title").asString()).isEqualTo("점심 고민");
        assertThat(item.get("category").asString()).isEqualTo("음식/식사");
        assertThat(item.get("result").asString()).isEqualTo("짬뽕을 추천해요.");
        assertThat(item.get("options").get(0).get("title").asString()).isEqualTo("짬뽕");

        // 검색/필터
        assertThat(json(mvc.perform(get("/api/history").param("q", "점심").session(session)).andReturn())).hasSize(1);
        assertThat(json(mvc.perform(get("/api/history").param("q", "없는말").session(session)).andReturn())).isEmpty();
        assertThat(json(mvc.perform(get("/api/history").param("category", "진로/직업").session(session))
                .andReturn())).isEmpty();

        // 북마크 / 회고
        mvc.perform(post("/api/history/" + id + "/bookmark").with(csrf()).session(session))
                .andExpect(jsonPath("$.bookmarked").value(true));
        mvc.perform(put("/api/history/" + id + "/retrospective").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actualChoice\":\"짬뽕\",\"satisfactionScore\":\"6\",\"retrospectiveNote\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("만족도 점수는 1점에서 5점 사이여야 합니다."));
        mvc.perform(put("/api/history/" + id + "/retrospective").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actualChoice\":\"짬뽕\",\"satisfactionScore\":\"5\",\"retrospectiveNote\":\"좋았음\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.satisfactionScore").value(5));

        // 다른 사람은 내 기록을 못 지움
        MockHttpSession other = signupAndLogin("other@example.com", "남");
        mvc.perform(delete("/api/history/" + id).with(csrf()).session(other)).andExpect(status().isNotFound());

        mvc.perform(delete("/api/history/" + id).with(csrf()).session(session)).andExpect(status().isOk());
        assertThat(json(mvc.perform(get("/api/history").session(session)).andReturn())).isEmpty();
    }

    @Test
    void guestConsultIsNotSaved() throws Exception {
        when(aiClient.consult(any())).thenReturn("결과");
        MvcResult res = mvc.perform(post("/api/decisions/consult").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"고민\"}")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(res).get("decisionId").isNull()).isTrue();

        mvc.perform(post("/api/decisions/consult").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("고민 내용을 입력해 주세요."));
    }

    // ---------- 커뮤니티 ----------

    @Test
    void communityFlow() throws Exception {
        MockHttpSession alice = signupAndLogin("alice@example.com", "앨리스");
        MockHttpSession bob = signupAndLogin("bob@example.com", "밥");

        // 선택지 1개 → 거절
        MvcResult bad = postJson("/api/community/posts",
                new Post("제목", "내용", "음식/식사", List.of("하나")), alice);
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);

        MvcResult created = postJson("/api/community/posts",
                new Post("점심 메뉴", "뭐 먹지", "음식/식사", List.of("짬뽕", "짜장면", " ")), alice);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        int postId = json(created).get("id").asInt();

        JsonNode feed = json(mvc.perform(get("/api/community/posts").session(bob)).andReturn());
        JsonNode post = feed.get("posts").get(0);
        assertThat(post.get("options")).hasSize(2);
        int optionA = post.get("options").get(0).get("id").asInt();
        int optionB = post.get("options").get(1).get("id").asInt();

        // 투표 → 표 옮기기 → 공감 → 댓글
        postJson("/api/community/posts/" + postId + "/votes/" + optionA, null, bob);
        postJson("/api/community/posts/" + postId + "/votes/" + optionB, null, bob);
        postJson("/api/community/posts/" + postId + "/votes/" + optionB, null, alice);
        postJson("/api/community/posts/" + postId + "/like", null, bob);
        postJson("/api/community/posts/" + postId + "/comments", new Comment("짜장면 추천"), bob);

        post = json(mvc.perform(get("/api/community/posts").param("category", "음식/식사").session(bob))
                .andReturn()).get("posts").get(0);
        assertThat(post.get("totalVotes").asLong()).isEqualTo(2);
        assertThat(post.get("options").get(1).get("count").asLong()).isEqualTo(2);
        assertThat(post.get("options").get(1).get("percent").asInt()).isEqualTo(100);
        assertThat(post.get("myOptionId").asInt()).isEqualTo(optionB);
        assertThat(post.get("liked").asBoolean()).isTrue();
        assertThat(post.get("likeCount").asLong()).isEqualTo(1);
        assertThat(post.get("mine").asBoolean()).isFalse();
        JsonNode comment = post.get("comments").get(0);
        assertThat(comment.get("authorNickname").asString()).isEqualTo("밥");
        assertThat(comment.get("mine").asBoolean()).isTrue();

        // 검색: 선택지 이름으로도 찾기
        assertThat(json(mvc.perform(get("/api/community/posts").param("q", "짜장")).andReturn())
                .get("posts")).hasSize(1);

        // 남의 글은 수정 불가
        MvcResult forbidden = mvc.perform(put("/api/community/posts/" + postId).with(csrf()).session(bob)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Post("x", "y", "기타", null)))).andReturn();
        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);

        // 댓글 수정/삭제, 투표 취소
        int commentId = comment.get("id").asInt();
        mvc.perform(put("/api/community/posts/" + postId + "/comments/" + commentId).with(csrf()).session(bob)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"수정된 댓글\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/community/posts/" + postId + "/votes").with(csrf()).session(bob))
                .andExpect(jsonPath("$.message").value("투표를 취소했습니다."));

        // 글 삭제 → 선택지/투표/공감/댓글 함께 삭제
        mvc.perform(delete("/api/community/posts/" + postId).with(csrf()).session(alice))
                .andExpect(status().isOk());
        assertThat(json(mvc.perform(get("/api/community/posts")).andReturn()).get("posts")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from community_votes", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from comment", Integer.class)).isZero();

        // 로그인 없이 작성 → 401
        mvc.perform(post("/api/community/posts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    record Post(String title, String content, String category, List<String> options) {
    }

    record Comment(String content) {
    }
}
