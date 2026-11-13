package com.pickme.community;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Integer> {

    @Query("""
            select p from Post p join fetch p.author
            where (:category is null or p.category = :category)
              and (:pattern is null
                   or lower(p.title) like :pattern
                   or lower(p.content) like :pattern
                   or exists (select o.id from PostOption o where o.post = p and lower(o.name) like :pattern))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> search(@Param("category") String category, @Param("pattern") String pattern);

    @Query("select max(p.updatedAt) from Post p")
    Instant latestUpdate();

    @Query("""
            select p.id, p.title, count(l.id) from Post p left join CommunityLike l on l.post = p
            group by p.id, p.title, p.createdAt
            order by count(l.id) desc, p.createdAt desc, p.id desc
            """)
    List<Object[]> topByLikes(Pageable pageable);

    @Query("""
            select p.id, p.title, count(c.id) from Post p left join Comment c on c.post = p
            group by p.id, p.title, p.createdAt
            order by count(c.id) desc, p.createdAt desc, p.id desc
            """)
    List<Object[]> topByComments(Pageable pageable);
}
