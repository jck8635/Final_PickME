package com.pickme.history;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionRepository extends JpaRepository<Decision, Integer> {

    @Query("""
            select d from Decision d
            where d.userId = :userId
              and (:category is null or d.category = :category)
              and (:bookmarkedOnly = false or d.bookmarked = true)
              and (:pattern is null or lower(d.title) like :pattern or lower(d.question) like :pattern)
            """)
    List<Decision> search(@Param("userId") Integer userId,
            @Param("category") String category,
            @Param("bookmarkedOnly") boolean bookmarkedOnly,
            @Param("pattern") String pattern,
            Sort sort);

    Optional<Decision> findByIdAndUserId(Integer id, Integer userId);

    @Modifying
    @Query("delete from Decision d where d.userId = :userId")
    int deleteAllByUserId(@Param("userId") Integer userId);

    /** 인기 고민 Top N: 같은 제목이 많은 순 */
    @Query("select d.title, count(d) from Decision d group by d.title order by count(d) desc")
    List<Object[]> popularTitles(Pageable pageable);
}
