package com.pickme.history;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionRepository extends JpaRepository<Decision, Integer> {

    /**
     * 기존 전체 목록 API용 조회.
     * 기존 호출 코드와의 호환성을 위해 유지합니다.
     */
    @Query("""
            select d from Decision d
            where d.userId = :userId
              and (:category is null or d.category = :category)
              and (:bookmarkedOnly = false or d.bookmarked = true)
              and (:pattern is null
                   or lower(d.title) like :pattern escape '\\'
                   or lower(d.question) like :pattern escape '\\')
            """)
    List<Decision> search(
            @Param("userId") Integer userId,
            @Param("category") String category,
            @Param("bookmarkedOnly") boolean bookmarkedOnly,
            @Param("pattern") String pattern,
            Sort sort);

    /**
     * DB 단계에서 페이지 크기만큼 조회합니다.
     * 전체 건수도 같은 검색 조건으로 계산합니다.
     */
    @Query("""
            select d from Decision d
            where d.userId = :userId
              and (:category is null or d.category = :category)
              and (:bookmarkedOnly = false or d.bookmarked = true)
              and (:pattern is null
                   or lower(d.title) like :pattern escape '\\'
                   or lower(d.question) like :pattern escape '\\')
            """)
    Page<Decision> searchPage(
            @Param("userId") Integer userId,
            @Param("category") String category,
            @Param("bookmarkedOnly") boolean bookmarkedOnly,
            @Param("pattern") String pattern,
            Pageable pageable);

    Optional<Decision> findByIdAndUserId(Integer id, Integer userId);

    @Modifying
    @Query("delete from Decision d where d.userId = :userId")
    int deleteAllByUserId(@Param("userId") Integer userId);

    /** 같은 제목이 많은 순으로 인기 고민을 조회합니다. */
    @Query("""
            select d.title, count(d)
            from Decision d
            group by d.title
            order by count(d) desc
            """)
    List<Object[]> popularTitles(Pageable pageable);
}
