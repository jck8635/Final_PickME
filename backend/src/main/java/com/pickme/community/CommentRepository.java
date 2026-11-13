package com.pickme.community;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Integer> {

    @Query("select c from Comment c join fetch c.author where c.postId in :postIds order by c.createdAt asc, c.id asc")
    List<Comment> findWithAuthorByPostIds(@Param("postIds") Collection<Integer> postIds);
}
