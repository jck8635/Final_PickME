-- PostgreSQL에서 커뮤니티 최신순 목록과 카테고리 목록을 지원하는 선택 인덱스.
CREATE INDEX IF NOT EXISTS ix_post_created_id
    ON post (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_post_category_created_id
    ON post (category, created_at DESC, id DESC);
