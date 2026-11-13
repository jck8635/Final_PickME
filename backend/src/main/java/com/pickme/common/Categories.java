package com.pickme.common;

import java.util.List;

/** 기존 Flask 의 카테고리 상수. 커뮤니티와 결정 도우미의 이름이 서로 다릅니다(원본 그대로 유지). */
public final class Categories {

    /** community_service.CATEGORIES */
    public static final List<String> COMMUNITY = List.of(
            "음식/식사", "진로/이직", "쇼핑/소비", "연애/관계",
            "일상/생활", "학습/자기계발", "여행/여가", "기타");

    /** decision_service.CATEGORIES (고민 기록 카테고리로도 사용) */
    public static final List<String> DECISION = List.of(
            "음식/식사", "진로/직업", "소비/쇼핑", "연애/관계",
            "일상/생활", "학습/자기계발", "여행/여가", "기타");

    public static final String ALL = "전체";

    private Categories() {
    }
}
