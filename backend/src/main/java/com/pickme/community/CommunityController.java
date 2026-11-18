package com.pickme.community;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.pickme.member.LoginUser;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService community;

    public CommunityController(CommunityService community) {
        this.community = community;
    }

    public record PostRequest(
            String title,
            String content,
            String category,
            List<String> options) {
    }

    public record CommentRequest(String content) {
    }

    @GetMapping("/posts")
    public CommunityService.Feed feed(
            @RequestParam(name = "category", defaultValue = "전체") String category,
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "scope", defaultValue = "all") String scope) {

        return community.feed(
                category,
                q,
                LoginUser.currentIdOrNull(),
                scope);
    }

    @GetMapping("/updates")
    public Map<String, String> updates() {
        return Map.of("updatedAt", community.latestUpdate());
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @RequestBody PostRequest req) {

        Integer id = community.createPost(
                LoginUser.requireId(),
                req.title(),
                req.content(),
                req.category(),
                req.options());

        return Map.of(
                "id", id,
                "message", "고민 투표를 등록했습니다.");
    }

    @PutMapping("/posts/{postId}")
    public Map<String, String> edit(
            @PathVariable("postId") Integer postId,
            @RequestBody PostRequest req) {

        community.editPost(
                LoginUser.requireId(),
                postId,
                req.title(),
                req.content(),
                req.category());

        return Map.of("message", "게시글을 수정했습니다.");
    }

    @DeleteMapping("/posts/{postId}")
    public Map<String, String> delete(
            @PathVariable("postId") Integer postId) {

        community.deletePost(
                LoginUser.requireId(),
                postId);

        return Map.of("message", "게시글을 삭제했습니다.");
    }

    @PostMapping("/posts/{postId}/votes/{optionId}")
    public Map<String, String> vote(
            @PathVariable("postId") Integer postId,
            @PathVariable("optionId") Integer optionId) {

        community.vote(
                LoginUser.requireId(),
                postId,
                optionId);

        return Map.of("message", "투표가 반영되었습니다.");
    }

    @DeleteMapping("/posts/{postId}/votes")
    public Map<String, String> cancelVote(
            @PathVariable("postId") Integer postId) {

        return Map.of(
                "message",
                community.cancelVote(
                        LoginUser.requireId(),
                        postId));
    }

    @PostMapping("/posts/{postId}/like")
    public Map<String, Boolean> like(
            @PathVariable("postId") Integer postId) {

        return Map.of(
                "liked",
                community.toggleLike(
                        LoginUser.requireId(),
                        postId));
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> comment(
            @PathVariable("postId") Integer postId,
            @RequestBody CommentRequest req) {

        community.addComment(
                LoginUser.requireId(),
                postId,
                req.content());

        return Map.of("message", "댓글을 등록했습니다.");
    }

    @PutMapping("/posts/{postId}/comments/{commentId}")
    public Map<String, String> editComment(
            @PathVariable("postId") Integer postId,
            @PathVariable("commentId") Integer commentId,
            @RequestBody CommentRequest req) {

        community.editComment(
                LoginUser.requireId(),
                postId,
                commentId,
                req.content());

        return Map.of("message", "댓글을 수정했습니다.");
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public Map<String, String> deleteComment(
            @PathVariable("postId") Integer postId,
            @PathVariable("commentId") Integer commentId) {

        community.deleteComment(
                LoginUser.requireId(),
                postId,
                commentId);

        return Map.of("message", "댓글을 삭제했습니다.");
    }
}
