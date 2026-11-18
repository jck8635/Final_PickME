package com.pickme.community;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostReportRepository
        extends JpaRepository<PostReport, Integer> {

    boolean existsByPostIdAndReporterId(
            Integer postId,
            Integer reporterId);
}
