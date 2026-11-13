package com.pickme.community;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickme.common.ApiException;
import com.pickme.common.Categories;
import com.pickme.member.User;
import com.pickme.member.UserRepository;

/** 기존 Flask 의 services/community_service.py + routes/community_routes.py 의 검증 로직. */
@Service
public class CommunityService {

    private final PostRepository posts;
    private final PostOptionRepository options;
    private final CommentRepository comments;
    private final CommunityVoteRepository votes;
    private final CommunityLikeRepository likes;
    private final UserRepository users;

    public CommunityService(PostRepository posts, PostOptionRepository options, CommentRepository comments,
            CommunityVoteRepository votes, CommunityLikeRepository likes, UserRepository users) {
        this.posts = posts;
        this.options = options;
        this.comments = comments;
        this.votes = votes;
        this.likes = likes;
        this.users = users;
    }

    // ---------- 화면용 DTO ----------

    public record OptionView(Integer id, String name, long count, int percent) {
    }

    public record CommentView(Integer id, String authorNickname, boolean mine, String content, Instant createdAt) {
    }

    public record PostView(
            Integer id, String title, String content, String category, String aiRecommendation,
            String authorNickname, boolean mine, Instant createdAt, long totalVotes, List<OptionView> options,
            Integer myOptionId, boolean liked, long likeCount, List<CommentView> comments) {
    }

    public record RankItem(Integer id, String title, long count) {
    }

    public record Feed(
            List<PostView> posts, Integer featuredPostId, List<RankItem> topLiked, List<RankItem> topCommented,
            String latestUpdate, List<String> categories) {
    }

    // ---------- 조회 ----------

    @Transactional(readOnly = true)
    public Feed feed(String category, String query, Integer userId) {
        String cat = Categories.COMMUNITY.contains(category) ? category : null;
        String q = query == null ? "" : query.strip();
        if (q.length() > 100) {
            q = q.substring(0, 100);
        }
        String pattern = q.isEmpty() ? null : "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";

        List<Post> found = posts.search(cat, pattern);
        List<Integer> ids = found.stream().map(Post::getId).toList();

        Map<Integer, List<PostOption>> optionsByPost = new HashMap<>();
        Map<Integer, Long> voteCounts = new HashMap<>();
        Map<Integer, Long> likeCounts = new HashMap<>();
        Map<Integer, List<Comment>> commentsByPost = new HashMap<>();
        Map<Integer, Integer> myVotes = new HashMap<>();
        Set<Integer> myLikes = new HashSet<>();

        if (!ids.isEmpty()) {
            for (PostOption o : options.findByPostIdInOrderByIdAsc(ids)) {
                optionsByPost.computeIfAbsent(o.getPostId(), k -> new ArrayList<>()).add(o);
            }
            for (Object[] row : votes.countByOption(ids)) {
                voteCounts.put((Integer) row[0], ((Number) row[1]).longValue());
            }
            for (Object[] row : likes.countByPost(ids)) {
                likeCounts.put((Integer) row[0], ((Number) row[1]).longValue());
            }
            for (Comment c : comments.findWithAuthorByPostIds(ids)) {
                commentsByPost.computeIfAbsent(c.getPostId(), k -> new ArrayList<>()).add(c);
            }
            if (userId != null) {
                for (Object[] row : votes.myVotes(userId, ids)) {
                    myVotes.put((Integer) row[0], (Integer) row[1]);
                }
                myLikes.addAll(likes.myLikes(userId, ids));
            }
        }

        List<PostView> views = new ArrayList<>();
        for (Post p : found) {
            List<PostOption> opts = optionsByPost.getOrDefault(p.getId(), List.of());
            long total = opts.stream().mapToLong(o -> voteCounts.getOrDefault(o.getId(), 0L)).sum();
            List<OptionView> optionViews = opts.stream().map(o -> {
                long count = voteCounts.getOrDefault(o.getId(), 0L);
                int percent = total == 0 ? 0 : (int) Math.round(count * 100.0 / total);
                return new OptionView(o.getId(), o.getName(), count, percent);
            }).toList();
            List<CommentView> commentViews = commentsByPost.getOrDefault(p.getId(), List.of()).stream()
                    .map(c -> new CommentView(c.getId(), c.getAuthor().getNickname(),
                            userId != null && userId.equals(c.getUserId()), c.getContent(), c.getCreatedAt()))
                    .toList();
            views.add(new PostView(p.getId(), p.getTitle(), p.getContent(), p.getCategory(), p.getAiRecommendation(),
                    p.getAuthor().getNickname(), userId != null && userId.equals(p.getUserId()), p.getCreatedAt(),
                    total, optionViews, myVotes.get(p.getId()), myLikes.contains(p.getId()),
                    likeCounts.getOrDefault(p.getId(), 0L), commentViews));
        }

        // 카테고리를 고르면 공감+댓글이 가장 많은 글을 맨 위에 (나머지는 최신순)
        Integer featuredId = null;
        if (cat != null && !views.isEmpty()) {
            PostView featured = views.stream().max(Comparator
                    .comparingLong((PostView v) -> v.likeCount() + v.comments().size())
                    .thenComparing(PostView::createdAt)
                    .thenComparing(PostView::id)).orElseThrow();
            featuredId = featured.id();
            views.remove(featured);
            views.add(0, featured);
        }

        return new Feed(views, featuredId, rank(posts.topByLikes(PageRequest.of(0, 3))),
                rank(posts.topByComments(PageRequest.of(0, 3))), latestUpdate(), Categories.COMMUNITY);
    }

    @Transactional(readOnly = true)
    public String latestUpdate() {
        Instant latest = posts.latestUpdate();
        return latest == null ? "" : latest.toString();
    }

    // ---------- 게시글 ----------

    @Transactional
    public Integer createPost(Integer userId, String rawTitle, String rawContent, String category,
            List<String> rawOptions) {
        String title = strip(rawTitle);
        String content = strip(rawContent);
        List<String> opts = rawOptions == null ? List.of()
                : rawOptions.stream().map(CommunityService::strip).filter(s -> !s.isEmpty()).toList();
        if (title.isEmpty() || title.length() > 200 || content.isEmpty() || content.length() > 5000
                || !Categories.COMMUNITY.contains(category) || opts.size() < 2 || opts.size() > 5
                || opts.stream().anyMatch(o -> o.length() > 120)) {
            throw ApiException.badRequest("제목, 내용, 카테고리와 2~5개의 선택지를 확인해주세요. (제목 200자, 내용 5,000자 이내)");
        }
        User author = user(userId);
        Post post = posts.save(new Post(author, title, content, category));
        for (String name : opts) {
            options.save(new PostOption(post, name));
        }
        return post.getId();
    }

    @Transactional
    public void editPost(Integer userId, Integer postId, String rawTitle, String rawContent, String category) {
        Post post = ownPost(userId, postId);
        String title = strip(rawTitle);
        String content = strip(rawContent);
        if (title.isEmpty() || title.length() > 200 || content.isEmpty() || content.length() > 5000
                || !Categories.COMMUNITY.contains(category)) {
            throw ApiException.badRequest("제목(1~200자), 내용(1~5,000자), 카테고리를 확인해주세요.");
        }
        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);
        post.touch();
    }

    @Transactional
    public void deletePost(Integer userId, Integer postId) {
        posts.delete(ownPost(userId, postId));
    }

    // ---------- 투표 / 공감 ----------

    @Transactional
    public void vote(Integer userId, Integer postId, Integer optionId) {
        Post post = post(postId);
        PostOption option = options.findById(optionId)
                .filter(o -> o.getPostId().equals(post.getId()))
                .orElseThrow(() -> ApiException.notFound("올바르지 않은 선택지입니다."));
        votes.findByPostIdAndUserId(postId, userId).ifPresentOrElse(
                v -> v.setOption(option),
                () -> votes.save(new CommunityVote(post, option, user(userId))));
        post.touch();
    }

    @Transactional
    public String cancelVote(Integer userId, Integer postId) {
        Post post = post(postId);
        var vote = votes.findByPostIdAndUserId(postId, userId);
        if (vote.isEmpty()) {
            return "취소할 투표가 없습니다.";
        }
        votes.delete(vote.get());
        post.touch();
        return "투표를 취소했습니다.";
    }

    @Transactional
    public boolean toggleLike(Integer userId, Integer postId) {
        Post post = post(postId);
        var like = likes.findByPostIdAndUserId(postId, userId);
        post.touch();
        if (like.isPresent()) {
            likes.delete(like.get());
            return false;
        }
        likes.save(new CommunityLike(post, user(userId)));
        return true;
    }

    // ---------- 댓글 ----------

    @Transactional
    public void addComment(Integer userId, Integer postId, String rawContent) {
        String content = commentContent(rawContent);
        Post post = post(postId);
        comments.save(new Comment(post, user(userId), content));
        post.touch();
    }

    @Transactional
    public void editComment(Integer userId, Integer postId, Integer commentId, String rawContent) {
        String content = commentContent(rawContent);
        Comment comment = ownComment(userId, postId, commentId);
        comment.setContent(content);
        comment.getPost().touch();
    }

    @Transactional
    public void deleteComment(Integer userId, Integer postId, Integer commentId) {
        Comment comment = ownComment(userId, postId, commentId);
        Post post = comment.getPost();
        comments.delete(comment);
        post.touch();
    }

    // ---------- 내부 ----------

    private static String commentContent(String raw) {
        String content = strip(raw);
        if (content.isEmpty() || content.length() > 2000) {
            throw ApiException.badRequest("댓글은 1~2,000자로 작성해주세요.");
        }
        return content;
    }

    private Post post(Integer postId) {
        return posts.findById(postId).orElseThrow(() -> ApiException.notFound("게시글을 찾을 수 없습니다."));
    }

    private Post ownPost(Integer userId, Integer postId) {
        Post post = post(postId);
        if (!post.getUserId().equals(userId)) {
            throw ApiException.forbidden("본인이 작성한 게시글만 수정/삭제할 수 있습니다.");
        }
        return post;
    }

    private Comment ownComment(Integer userId, Integer postId, Integer commentId) {
        Comment comment = comments.findById(commentId)
                .filter(c -> c.getPostId().equals(postId))
                .orElseThrow(() -> ApiException.notFound("댓글을 찾을 수 없습니다."));
        if (!comment.getUserId().equals(userId)) {
            throw ApiException.forbidden("본인이 작성한 댓글만 수정/삭제할 수 있습니다.");
        }
        return comment;
    }

    private User user(Integer userId) {
        return users.getReferenceById(userId);
    }

    private static List<RankItem> rank(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new RankItem((Integer) r[0], (String) r[1], ((Number) r[2]).longValue()))
                .toList();
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
