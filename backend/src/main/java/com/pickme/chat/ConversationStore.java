package com.pickme.chat;

import java.util.UUID;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pickme.common.ApiException;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL 상담 기록. AI 호출과 별도의 짧은 트랜잭션으로 저장합니다. */
@Service
public class ConversationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final boolean enabled;
    private final int retentionDays;

    public ConversationStore(JdbcTemplate jdbc, ObjectMapper json,
            @Value("${pickme.conversation.enabled:false}") boolean enabled,
            @Value("${pickme.conversation.retention-days:30}") int retentionDays) {
        this.jdbc = jdbc;
        this.json = json;
        this.enabled = enabled;
        if (retentionDays < 1) throw new IllegalArgumentException("retention-days must be positive");
        this.retentionDays = retentionDays;
    }

    @Transactional
    public UUID begin(Integer userId, UUID requestedId, String title, String tone,
            String content, String source, Object context) {
        if (!enabled || userId == null) return null;
        UUID id = requestedId == null ? UUID.randomUUID() : requestedId;
        String safeTitle = title == null || title.isBlank() ? content : title;
        safeTitle = safeTitle.substring(0, Math.min(200, safeTitle.length()));
        jdbc.update("""
            INSERT INTO public.counseling_session (id,user_id,title,tone,status,last_sequence_no)
            VALUES (?,?,?,?,'ACTIVE',0) ON CONFLICT (id) DO NOTHING
            """, id, userId, safeTitle, tone);
        appendLocked(userId, id, "user", content, source, context);
        return id;
    }

    @Transactional
    public void finish(Integer userId, UUID id, String content, String source, Object context) {
        if (!enabled || userId == null || id == null) return;
        appendLocked(userId, id, "assistant", content, source, context);
    }

    private void appendLocked(Integer userId, UUID id, String role, String content,
            String source, Object context) {
        var rows = jdbc.queryForList("""
            SELECT last_sequence_no FROM public.counseling_session
            WHERE id=? AND user_id=? AND status='ACTIVE' FOR UPDATE
            """, Integer.class, id, userId);
        if (rows.isEmpty()) throw ApiException.notFound("상담을 찾을 수 없습니다.");
        int sequence = rows.getFirst() + 1;
        jdbc.update("""
            INSERT INTO public.chat_message (id,session_id,role,content,sequence_no,created_at,expires_at)
            VALUES (?,?,?,?,?,now(),now() + (? * interval '1 day'))
            """, UUID.randomUUID(), id, role, content, sequence, retentionDays);
        jdbc.update("UPDATE public.counseling_session SET last_sequence_no=?,updated_at=now() WHERE id=?", sequence,id);
        // 생성형 요약은 후속 단계에서 구현. 현재는 최신 메시지 발췌 및 요청 문맥을 저장합니다.
        String excerpt = content.substring(0, Math.min(500, content.length()));
        String snapshot = json.writeValueAsString(Map.of("source", source, "role", role,
                "latest_message", excerpt, "request", context));
        jdbc.update("""
            INSERT INTO public.conversation_summary
              (id,session_id,summary_text,context_json,covered_until_sequence,version,updated_at,expires_at)
            VALUES (?,?,?,CAST(? AS jsonb),?,1,now(),now() + (? * interval '1 day'))
            ON CONFLICT (session_id) DO UPDATE SET summary_text=EXCLUDED.summary_text,
              context_json=EXCLUDED.context_json,covered_until_sequence=EXCLUDED.covered_until_sequence,
              version=conversation_summary.version+1,updated_at=now(),expires_at=EXCLUDED.expires_at
            """, UUID.randomUUID(),id,"최근 메시지 발췌: " + excerpt,snapshot,sequence,retentionDays);
    }
}
