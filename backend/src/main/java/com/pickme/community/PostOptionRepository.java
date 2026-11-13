package com.pickme.community;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostOptionRepository extends JpaRepository<PostOption, Integer> {

    List<PostOption> findByPostIdInOrderByIdAsc(Collection<Integer> postIds);
}
