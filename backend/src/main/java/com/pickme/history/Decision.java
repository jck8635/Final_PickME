package com.pickme.history;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

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

/** 결정 도우미 상담 기록 (기존 decision 테이블). */
@Entity
@Table(name = "decision", indexes = {
        @Index(name = "ix_decision_user_id", columnList = "user_id"),
        @Index(name = "ix_decision_category", columnList = "category")})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Integer userId;

    @Column(nullable = false, length = 200)
    private String title;

    /** PostgreSQL 에서는 text 컬럼 */
    @Column(nullable = false, length = 10000)
    private String question = "";

    /** {"categories", "style", "conditions", "options", "criteria", "result"} JSON 문자열 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json")
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 80)
    @ColumnDefault("'일반'")
    private String category = "일반";

    @Column(name = "is_bookmarked", nullable = false)
    @ColumnDefault("false")
    private boolean bookmarked;

    @Column(name = "actual_choice", length = 10000)
    private String actualChoice;

    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    @Column(name = "retrospective_note", length = 10000)
    private String retrospectiveNote;

    public Decision(User user, String title, String question, String category, String resultJson) {
        this.user = user;
        this.title = title;
        this.question = question;
        this.category = category;
        this.resultJson = resultJson;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
