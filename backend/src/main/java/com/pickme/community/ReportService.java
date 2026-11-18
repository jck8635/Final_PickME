package com.pickme.community;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickme.common.ApiException;
import com.pickme.member.AdminAccess;
import com.pickme.member.LoginUser;
import com.pickme.member.UserRepository;

@Service
public class ReportService {

    private final PostReportRepository reports;
    private final PostRepository posts;
    private final UserRepository users;
    private final AdminAccess admins;

    public ReportService(
            PostReportRepository reports,
            PostRepository posts,
            UserRepository users,
            AdminAccess admins) {

        this.reports = reports;
        this.posts = posts;
        this.users = users;
        this.admins = admins;
    }

    @Transactional
    public void report(Integer postId, String rawReason) {
        Integer userId = LoginUser.requireId();

        if (!admins.available()) {
            throw ApiException.badRequest(
                    "현재 신고를 접수할 관리자가 없습니다.");
        }

        String reason = rawReason == null ? "" : rawReason.strip();

        if (reason.isEmpty() || reason.length() > 500) {
            throw ApiException.badRequest(
                    "신고 사유를 1~500자로 입력해주세요.");
        }

        Post post = posts.lockById(postId)
                .orElseThrow(() -> ApiException.notFound("게시글을 찾을 수 없습니다."));

        if (reports.existsByPostIdAndReporterId(postId, userId)) {
            throw ApiException.badRequest("이미 신고한 게시글입니다.");
        }

        reports.save(new PostReport(
                post,
                users.getReferenceById(userId),
                reason));
    }

    @Transactional
    public void reject(Integer id) {
        requireAdmin();

        reports.findById(id)
                .orElseThrow(() -> ApiException.notFound("신고를 찾을 수 없습니다."))
                .reject();
    }

    @Transactional
    public void deletePost(Integer id) {
        requireAdmin();

        posts.delete(posts.lockById(id)
                .orElseThrow(() -> ApiException.notFound("게시글을 찾을 수 없습니다.")));
    }

    private void requireAdmin() {
        if (!AdminAccess.current()) {
            throw ApiException.forbidden("관리자 권한이 필요합니다.");
        }
    }
}
