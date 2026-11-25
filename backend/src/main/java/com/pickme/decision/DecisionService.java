package com.pickme.decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.pickme.ai.AiClient;
import com.pickme.common.ApiException;
import com.pickme.common.Categories;
import com.pickme.history.Decision;
import com.pickme.history.DecisionRepository;
import com.pickme.history.HistoryEmbeddingService;
import com.pickme.member.UserRepository;

import tools.jackson.databind.ObjectMapper;
import com.pickme.member.UserProfileService.ProfileResponse;

/** 기존 Flask 의 routes/decision_routes.py 의 저장/프롬프트 준비 부분. */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    private static final Set<String> STYLES = Set.of("soft", "objective", "fact");

    private final AiClient aiClient;
    private final DecisionRepository decisions;
    private final UserRepository users;
    private final ObjectMapper objectMapper;
    private final HistoryEmbeddingService historyEmbeddingService;

    public DecisionService(AiClient aiClient, DecisionRepository decisions, UserRepository users,
            ObjectMapper objectMapper, HistoryEmbeddingService historyEmbeddingService) {
        this.aiClient = aiClient;
        this.decisions = decisions;
        this.users = users;
        this.objectMapper = objectMapper;
        this.historyEmbeddingService = historyEmbeddingService;
    }

    public record Option(String title, String note) {
    }

    /** weight: "1"~"5" 또는 "보류" */
    public record Criterion(String name, String weight) {
    }

    public record ConsultRequest(
            List<String> categories, String style, String title, String content,
            List<String> conditions, String conditionsFree, List<Option> options, List<Criterion> criteria,
            String result) {
    }

    public record ConsultResponse(String result, Integer decisionId) {
    }

    public record PopularItem(String title, long count) {
    }

    record Prepared(List<String> categories, String style, String title, String content, String conditions,
            List<AiClient.DecisionOption> options, String criteria) {
    }

    public ConsultResponse consult(
            ConsultRequest req,
            Integer userId,
            ProfileResponse userProfile) {
        Prepared p = prepare(req);
        String result = aiClient.consult(toAiRequest(p, userProfile));
        // Integer savedId = userId == null ? null : save(userId, p, result);
        return new ConsultResponse(result, null);
    }

    @Transactional
    public Integer saveManual(Integer userId, ConsultRequest req, String result) {
        if (userId == null) {
            return null;
        }
        if (result == null || result.isBlank()) {
            throw ApiException.badRequest("상담 결과가 없습니다.");
        }
        Prepared p = prepare(req);
        return save(userId, p, result);
    }

    public String restyle(ConsultRequest req, ProfileResponse profile) {
        return aiClient.consult(toAiRequest(prepare(req), profile));
    }

    // ai 서버에서 역질문 요청
    public List<String> questions(ConsultRequest req) {
        // 기존 상담 입력 정리 ,내용 비어있으면 400
        Prepared p = prepare(req);
        // 함수 호출 질문 목록 받기
        return aiClient.questions(toAiRequest(p, null));
    }

    @Transactional(readOnly = true)
    public List<PopularItem> popular() {
        return decisions.popularTitles(PageRequest.of(0, 5)).stream()
                .map(row -> new PopularItem((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    /** AI 호출(수 초)이 트랜잭션 밖에서 끝난 뒤 저장만 짧게 합니다. */
    private Integer save(Integer userId, Prepared p, String result) {
        var user = users.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("categories", p.categories());
        json.put("style", p.style());
        json.put("conditions", p.conditions());
        json.put("options", p.options());
        json.put("criteria", p.criteria());
        json.put("result", result);

        String title = p.title().isEmpty() ? truncate(p.content(), 50) : truncate(p.title(), 200);
        // 원본은 category 를 저장하지 않아 기록 화면의 카테고리 필터가 동작하지 않았음 → 첫 번째 카테고리 저장
        String category = p.categories().isEmpty() ? "기타" : p.categories().get(0);
        Decision saved = decisions.save(new Decision(user, title, p.content(), category,
                objectMapper.writeValueAsString(json)));
        Integer decisionId = saved.getId();

        // 기록이 DB에 커밋된 이후에만 별도 트랜잭션으로 임베딩을 생성합니다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    historyEmbeddingService.generate(decisionId, userId);
                    log.info("고민 기록 임베딩 저장 완료: decisionId={}", decisionId);
                } catch (Exception e) {
                    // 임베딩 실패가 이미 저장된 고민 기록의 성공 응답을 막지 않도록 합니다.
                    // 고민 기록 화면의 동기화 버튼으로 다시 생성할 수 있습니다.
                    log.warn("고민 기록 임베딩 저장 실패. 동기화로 재시도 가능: decisionId={}", decisionId, e);
                }
            }
        });
        return decisionId;
    }

    private Prepared prepare(ConsultRequest req) {
        String content = strip(req.content());
        if (content.isEmpty()) {
            throw ApiException.badRequest("고민 내용을 입력해 주세요.");
        }
        if (content.length() > 5000) {
            throw ApiException.badRequest("고민 내용은 5,000자 이내로 입력해 주세요.");
        }
        List<String> categories = nonNull(req.categories()).stream()
                .filter(Categories.DECISION::contains).distinct().toList();
        String style = req.style() != null && STYLES.contains(req.style()) ? req.style() : "objective";
        String title = truncate(strip(req.title()), 200);

        // 칩(체크박스) + 자유 입력 조건을 모두 반영
        List<String> conditionParts = new ArrayList<>(nonNull(req.conditions()).stream()
                .map(DecisionService::strip).filter(s -> !s.isEmpty()).toList());
        String free = strip(req.conditionsFree());
        if (!free.isEmpty()) {
            conditionParts.add(free);
        }

        String conditions = truncate(String.join(", ", conditionParts), 1000);
        // AI 보낼 데이터 가공 구간
        List<AiClient.DecisionOption> options = nonNull(req.options()).stream()
                .filter(o -> o != null && !strip(o.title()).isEmpty())
                .limit(5)
                .map(o -> new AiClient.DecisionOption(truncate(strip(o.title()), 200), truncate(strip(o.note()), 500)))
                .toList();

        // "기준명(가중치점)" 형태로 정리해 AI 에게 전달
        String criteria = nonNull(req.criteria()).stream()
                .filter(c -> c != null && !strip(c.name()).isEmpty())
                .map(c -> {
                    String w = strip(c.weight());
                    String label = w.isEmpty() ? "?점" : ("보류".equals(w) ? "보류" : w + "점");
                    return strip(c.name()) + "(" + label + ")";
                })
                .distinct()
                .collect(Collectors.joining(", "));
        if (criteria.isEmpty()) {
            criteria = "지정 없음";
        }
        return new Prepared(categories, style, title, content, conditions, options, truncate(criteria, 1000));
    }

    // 프로필을 함께받아서 AI 요청
    private static AiClient.DecisionRequest toAiRequest(Prepared p, ProfileResponse profile) {
        AiClient.UserProfileForAi aiprofile = profile == null ? null
                : new AiClient.UserProfileForAi(
                        profile.region(), profile.occupation(), profile.mbti(),
                        profile.interests(), profile.dislikedFoods(), profile.bio());

        return new AiClient.DecisionRequest(p.categories(), p.style(), p.title(), p.content(), p.conditions(),
                p.options(), p.criteria(), aiprofile);
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
