package com.pickme.community;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.pickme.member.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 커뮤니티 고민 투표 게시글 (기존 post 테이블). 삭제 시 선택지/투표/공감/댓글은 DB 에서 함께 삭제됩니다. */
@Entity
@Table(name = "post", indexes = @Index(name = "ix_post_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User author;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Integer userId;

    @Column(nullable = false, length = 200)
    private String title;

    /** PostgreSQL 에서는 text 컬럼 */
    @Column(nullable = false, length = 10000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 40)
    private String category = "기타";

    @Column(name = "ai_recommendation", length = 120)
    private String aiRecommendation;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Post(User author, String title, String content, String category) {
        this.author = author;
        this.title = title;
        this.content = content;
        this.category = category;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /** 투표/공감/댓글 변화도 새 소식으로 보이도록 갱신 시각을 올립니다. */
    public void touch() {
        updatedAt = Instant.now();
    }
}
