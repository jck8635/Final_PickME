package com.pickme.community;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pickme.member.LoginUser;

/** 커뮤니티. 목록 조회는 누구나, 작성/투표/공감/댓글은 로그인 필요(SecurityConfig). */
@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService community;

    public CommunityController(CommunityService community) {
        this.community = community;
    }

    public record PostRequest(String title, String content, String category, List<String> options) {
    }

    public record CommentRequest(String content) {
    }

    @GetMapping("/posts")
    public CommunityService.Feed feed(@RequestParam(defaultValue = "전체") String category,
            @RequestParam(defaultValue = "") String q) {
        return community.feed(category, q, LoginUser.currentIdOrNull());
    }

    /** 5초마다 호출해 다른 사람의 변경이 있으면 화면을 새로 고칩니다. */
    @GetMapping("/updates")
    public Map<String, String> updates() {
        return Map.of("updatedAt", community.latestUpdate());
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody PostRequest req) {
        Integer id = community.createPost(LoginUser.requireId(), req.title(), req.content(), req.category(),
                req.options());
        return Map.of("id", id, "message", "고민 투표를 등록했습니다.");
    }

    @PutMapping("/posts/{postId}")
    public Map<String, String> edit(@PathVariable Integer postId, @RequestBody PostRequest req) {
        community.editPost(LoginUser.requireId(), postId, req.title(), req.content(), req.category());
        return Map.of("message", "게시글을 수정했습니다.");
    }

    @DeleteMapping("/posts/{postId}")
    public Map<String, String> delete(@PathVariable Integer postId) {
        community.deletePost(LoginUser.requireId(), postId);
        return Map.of("message", "게시글을 삭제했습니다.");
    }

    @PostMapping("/posts/{postId}/votes/{optionId}")
    public Map<String, String> vote(@PathVariable Integer postId, @PathVariable Integer optionId) {
        community.vote(LoginUser.requireId(), postId, optionId);
        return Map.of("message", "투표가 반영되었습니다.");
    }

    @DeleteMapping("/posts/{postId}/votes")
    public Map<String, String> cancelVote(@PathVariable Integer postId) {
        return Map.of("message", community.cancelVote(LoginUser.requireId(), postId));
    }

    @PostMapping("/posts/{postId}/like")
    public Map<String, Boolean> like(@PathVariable Integer postId) {
        return Map.of("liked", community.toggleLike(LoginUser.requireId(), postId));
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> comment(@PathVariable Integer postId, @RequestBody CommentRequest req) {
        community.addComment(LoginUser.requireId(), postId, req.content());
        return Map.of("message", "댓글을 등록했습니다.");
    }

    @PutMapping("/posts/{postId}/comments/{commentId}")
    public Map<String, String> editComment(@PathVariable Integer postId, @PathVariable Integer commentId,
            @RequestBody CommentRequest req) {
        community.editComment(LoginUser.requireId(), postId, commentId, req.content());
        return Map.of("message", "댓글을 수정했습니다.");
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public Map<String, String> deleteComment(@PathVariable Integer postId, @PathVariable Integer commentId) {
        community.deleteComment(LoginUser.requireId(), postId, commentId);
        return Map.of("message", "댓글을 삭제했습니다.");
    }
}
