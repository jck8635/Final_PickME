package com.pickme.community;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PostRepository extends JpaRepository<Post, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> lockById(@Param("id") Integer id);

    String SEARCH = """
            where (:category is null or p.category = :category)
              and (
                  :pattern is null
                  or lower(p.title) like :pattern
                  or lower(p.content) like :pattern
                  or exists (
                      select o.id from PostOption o
                      where o.post = p and lower(o.name) like :pattern
                  )
              )
              and (
                  :scope = 'all'
                  or (:scope = 'mine' and p.userId = :userId)
                  or (
                      :scope = 'commented'
                      and exists (
                          select c.id from Comment c
                          where c.post = p and c.userId = :userId
                      )
                  )
                  or (
                      :scope = 'liked'
                      and exists (
                          select l.id from CommunityLike l
                          where l.post = p and l.userId = :userId
                      )
                  )
              )
            """;

    @Query(value = "select p from Post p join fetch p.author " + SEARCH
            + "order by p.createdAt desc, p.id desc", countQuery = "select count(p) from Post p " + SEARCH)
    Page<Post> search(
            @Param("category") String category,
            @Param("pattern") String pattern,
            @Param("scope") String scope,
            @Param("userId") Integer userId,
            Pageable pageable);

    @Query("select p.id, p.title, "
            + "((select count(l) from CommunityLike l where l.post = p) "
            + "+ (select count(c) from Comment c where c.post = p)) as engagement "
            + "from Post p " + SEARCH
            + "order by engagement desc, p.createdAt desc, p.id desc")
    List<Object[]> topByEngagement(
            @Param("category") String category,
            @Param("pattern") String pattern,
            @Param("scope") String scope,
            @Param("userId") Integer userId,
            Pageable pageable);

    @Query("select max(p.updatedAt) from Post p")
    Instant latestUpdate();

    @Query("""
            select p.id, p.title, count(l.id)
            from Post p left join CommunityLike l on l.post = p
            group by p.id, p.title, p.createdAt
            order by count(l.id) desc, p.createdAt desc, p.id desc
            """)
    List<Object[]> topByLikes(Pageable pageable);

    @Query("""
            select p.id, p.title, count(c.id)
            from Post p left join Comment c on c.post = p
            group by p.id, p.title, p.createdAt
            order by count(c.id) desc, p.createdAt desc, p.id desc
            """)
    List<Object[]> topByComments(Pageable pageable);
}
