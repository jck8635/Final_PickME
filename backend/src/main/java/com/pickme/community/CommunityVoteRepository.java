package com.pickme.community;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityVoteRepository extends JpaRepository<CommunityVote, Integer> {

    Optional<CommunityVote> findByPostIdAndUserId(Integer postId, Integer userId);

    @Query("select v.optionId, count(v.id) from CommunityVote v where v.postId in :postIds group by v.optionId")
    List<Object[]> countByOption(@Param("postIds") Collection<Integer> postIds);

    @Query("select v.postId, v.optionId from CommunityVote v where v.userId = :userId and v.postId in :postIds")
    List<Object[]> myVotes(@Param("userId") Integer userId, @Param("postIds") Collection<Integer> postIds);
}
