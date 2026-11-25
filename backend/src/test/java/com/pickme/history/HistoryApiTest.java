package com.pickme.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.pickme.member.LoginUser;
import com.pickme.member.User;
import com.pickme.member.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 서버 없이 실제 Security/MVC/JPA/H2를 사용하는
 * 고민 기록 API 통합 테스트입니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HistoryApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    DecisionRepository decisions;

    @Autowired
    UserRepository users;

    private User owner;
    private User other;

    private static final Instant SAME_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();

        owner = users.saveAndFlush(new User(
                "owner-" + suffix + "@example.com",
                "owner-" + suffix,
                "unused"));

        other = users.saveAndFlush(new User(
                "other-" + suffix + "@example.com",
                "other-" + suffix,
                "unused"));
    }

    private RequestPostProcessor as(User user) {
        return authentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(user.getId()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Decision record(
            User user,
            String title,
            String category,
            boolean bookmarked) {

        Decision decision = new Decision(
                user,
                title,
                "고민 내용: " + title,
                category,
                "{\"options\":[],\"result\":\"분석 결과\"}");

        decision.setCreatedAt(SAME_TIME);
        decision.setBookmarked(bookmarked);

        return decisions.saveAndFlush(decision);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(
                result.getResponse().getContentAsString());
    }

    private JsonNode page(
            int number,
            int size,
            String sort) throws Exception {

        return body(mvc.perform(
                get("/api/history/page")
                        .with(as(owner))
                        .param("page", String.valueOf(number))
                        .param("size", String.valueOf(size))
                        .param("sort", sort))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void anonymousCannotReadOrModifyHistory() throws Exception {
        Decision decision = record(owner, "개인 기록", "기타", false);
        String root = "/api/history/" + decision.getId();

        mvc.perform(get("/api/history/page"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/history"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post(root + "/bookmark").with(csrf()))
                .andExpect(status().isUnauthorized());

        mvc.perform(put(root + "/retrospective")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete(root).with(csrf()))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/history").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void modificationRequiresCsrfToken() throws Exception {
        Decision decision = record(owner, "개인 기록", "기타", false);

        mvc.perform(post(
                "/api/history/" + decision.getId() + "/bookmark")
                .with(as(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void defaultPageContainsTenOwnedRecordsAndCorrectTotals()
            throws Exception {

        for (int i = 0; i < 12; i++) {
            record(owner, "내 기록 " + i, "기타", false);
        }

        record(other, "타인 기록", "기타", false);

        mvc.perform(get("/api/history/page").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void tiedTimestampsDoNotCauseDuplicatesOrMissingRecordsAcrossPages()
            throws Exception {

        List<Integer> expected = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            expected.add(
                    record(owner, "기록 " + i, "기타", false).getId());
        }

        List<Integer> actual = new ArrayList<>();

        for (int number = 0; number < 3; number++) {
            JsonNode response = page(number, 3, "oldest");

            assertThat(response.get("totalElements").asLong())
                    .isEqualTo(7);

            for (JsonNode item : response.get("items")) {
                actual.add(item.get("id").asInt());
            }
        }

        assertThat(actual)
                .containsExactlyElementsOf(expected)
                .doesNotHaveDuplicates();
    }

    @Test
    void latestSortKeepsBookmarksFirstThenUsesDescendingIdForTies()
            throws Exception {

        Decision first = record(owner, "일반1", "기타", false);
        Decision bookmark = record(owner, "즐겨찾기", "기타", true);
        Decision last = record(owner, "일반2", "기타", false);

        JsonNode items = page(0, 10, "latest").get("items");

        assertThat(items.get(0).get("id").asInt())
                .isEqualTo(bookmark.getId());

        assertThat(items.get(1).get("id").asInt())
                .isEqualTo(last.getId());

        assertThat(items.get(2).get("id").asInt())
                .isEqualTo(first.getId());
    }

    @Test
    void searchCategoryAndBookmarkFiltersApplyBeforeCountingAndPaging()
            throws Exception {

        Decision match = record(owner, "이직 선택", "진로/직업", true);

        record(owner, "이직 미정", "진로/직업", false);
        record(owner, "이직 선물", "소비/쇼핑", true);
        record(owner, "취업 준비", "진로/직업", true);
        record(other, "이직 선택", "진로/직업", true);

        mvc.perform(get("/api/history/page")
                .with(as(owner))
                .param("q", "이직")
                .param("category", "진로/직업")
                .param("bookmarked", "true")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(
                        jsonPath("$.items[0].id").value(match.getId()));
    }

    @Test
    void searchTreatsSqlWildcardsAsLiteralCharacters()
            throws Exception {

        record(owner, "50% 할인", "기타", false);
        record(owner, "할인 없음", "기타", false);

        mvc.perform(get("/api/history/page")
                .with(as(owner))
                .param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void emptyAndOutOfRangePagesHaveConsistentMetadata()
            throws Exception {

        JsonNode empty = page(0, 10, "latest");

        assertThat(empty.get("items")).isEmpty();
        assertThat(empty.get("totalPages").asInt()).isZero();

        record(owner, "기록", "기타", false);

        JsonNode beyond = page(5, 10, "latest");

        assertThat(beyond.get("items")).isEmpty();
        assertThat(beyond.get("page").asInt()).isEqualTo(5);
        assertThat(beyond.get("totalElements").asLong()).isEqualTo(1);
        assertThat(beyond.get("totalPages").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "-1,10",
            "0,0",
            "0,-1",
            "0,101",
            "2147483647,100",
            "abc,10",
            "0,abc"
    })
    void invalidPaginationIsRejected(String page, String size)
            throws Exception {

        mvc.perform(get("/api/history/page")
                .with(as(owner))
                .param("page", page)
                .param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void legacyArrayResponseRemainsAvailable() throws Exception {
        Decision mine = record(owner, "내 기록", "기타", false);
        record(other, "다른 사람", "기타", false);

        mvc.perform(get("/api/history").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.getId()));
    }

    @Test
    void ownerCanToggleBookmarkAndSaveRetrospective()
            throws Exception {

        Decision decision = record(owner, "내 기록", "기타", false);
        String root = "/api/history/" + decision.getId();

        mvc.perform(post(root + "/bookmark")
                .with(as(owner))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(true));

        mvc.perform(post(root + "/bookmark")
                .with(as(owner))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(false));

        mvc.perform(put(root + "/retrospective")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "actualChoice", "선택 A",
                        "satisfactionScore", "5",
                        "retrospectiveNote", "잘한 선택"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualChoice").value("선택 A"))
                .andExpect(jsonPath("$.satisfactionScore").value(5))
                .andExpect(
                        jsonPath("$.retrospectiveNote")
                                .value("잘한 선택"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "6", "abc", "1.5" })
    void invalidRetrospectiveScoreIsRejected(String score)
            throws Exception {

        Decision decision = record(owner, "내 기록", "기타", false);

        mvc.perform(put(
                "/api/history/"
                        + decision.getId()
                        + "/retrospective")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "satisfactionScore", score))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherUserCannotModifyOrDeleteMyRecord()
            throws Exception {

        Decision decision = record(owner, "내 기록", "기타", false);
        String root = "/api/history/" + decision.getId();

        mvc.perform(post(root + "/bookmark")
                .with(as(other))
                .with(csrf()))
                .andExpect(status().isNotFound());

        mvc.perform(put(root + "/retrospective")
                .with(as(other))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"satisfactionScore\":5}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete(root)
                .with(as(other))
                .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(page(0, 10, "latest")
                .get("totalElements")
                .asLong()).isEqualTo(1);
    }

    @Test
    void deletingLastItemUpdatesPageTotals() throws Exception {
        record(owner, "첫 기록", "기타", false);

        Decision last = record(owner, "마지막 기록", "기타", false);

        mvc.perform(delete("/api/history/" + last.getId())
                .with(as(owner))
                .with(csrf()))
                .andExpect(status().isOk());

        JsonNode response = page(1, 1, "oldest");

        assertThat(response.get("items")).isEmpty();
        assertThat(response.get("totalElements").asLong()).isEqualTo(1);
        assertThat(response.get("totalPages").asInt()).isEqualTo(1);
    }

    @Test
    void clearOnlyDeletesCurrentUsersRecords() throws Exception {
        record(owner, "내 기록", "기타", false);
        record(other, "다른 사람 기록", "기타", false);

        mvc.perform(delete("/api/history")
                .with(as(owner))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1));

        assertThat(page(0, 10, "latest")
                .get("totalElements")
                .asLong()).isZero();

        mvc.perform(get("/api/history/page").with(as(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
