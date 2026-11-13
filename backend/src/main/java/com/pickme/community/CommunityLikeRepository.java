package com.pickme.community;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityLikeRepository extends JpaRepository<CommunityLike, Integer> {

    Optional<CommunityLike> findByPostIdAndUserId(Integer postId, Integer userId);

    @Query("select l.postId, count(l.id) from CommunityLike l where l.postId in :postIds group by l.postId")
    List<Object[]> countByPost(@Param("postIds") Collection<Integer> postIds);

    @Query("select l.postId from CommunityLike l where l.userId = :userId and l.postId in :postIds")
    List<Integer> myLikes(@Param("userId") Integer userId, @Param("postIds") Collection<Integer> postIds);
}
