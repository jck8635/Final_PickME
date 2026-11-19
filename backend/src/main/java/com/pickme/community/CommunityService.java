package com.pickme.community;

import java.time.Instant;
import java.util.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickme.common.ApiException;
import com.pickme.common.Categories;
import com.pickme.member.LoginUser;
import com.pickme.member.User;
import com.pickme.member.UserRepository;

@Service
public class CommunityService {

    private final PostRepository posts;
    private final PostOptionRepository options;
    private final CommentRepository comments;
    private final CommunityVoteRepository votes;
    private final CommunityLikeRepository likes;
    private final UserRepository users;
    private final CommunityModeration moderation;

    public CommunityService(
            PostRepository posts,
            PostOptionRepository options,
            CommentRepository comments,
            CommunityVoteRepository votes,
            CommunityLikeRepository likes,
            UserRepository users,
            CommunityModeration moderation) {

        this.posts = posts;
        this.options = options;
        this.comments = comments;
        this.votes = votes;
        this.likes = likes;
        this.users = users;
        this.moderation = moderation;
    }

    public record OptionView(
            Integer id, String name, long count, int percent) {
    }

    public record CommentView(
            Integer id, String authorNickname, boolean mine,
            String content, Instant createdAt) {
    }

    public record PostView(
            Integer id, String title, String content,
            String category, String aiRecommendation,
            String authorNickname, boolean mine, Instant createdAt,
            long totalVotes, List<OptionView> options,
            Integer myOptionId, boolean liked, long likeCount,
            long commentCount, List<CommentView> comments) {
    }

    public record RankItem(Integer id, String title, long count) {
    }

    public record Feed(
            List<PostView> posts, RankItem featuredPost,
            int page, int totalPages,
            List<RankItem> topLiked, List<RankItem> topCommented,
            String latestUpdate, List<String> categories) {
    }

    @Transactional(readOnly = true)
    public Feed feed(String category, String query, Integer userId, String scope, int page) {
        if (page < 1 || page > Integer.MAX_VALUE / 20) {
            throw ApiException.badRequest("올바르지 않은 페이지입니다.");
        }
        if (!Set.of("all", "mine", "commented", "liked").contains(scope)) {
            throw ApiException.badRequest("올바르지 않은 탭입니다.");
        }

        if (!scope.equals("all")) {
            LoginUser.requireId();
        }

        String cat = category != null && Categories.COMMUNITY.contains(category)
                ? category
                : null;

        String q = query == null ? "" : query.strip();
        if (q.length() > 100) {
            q = q.substring(0, 100);
        }

        String pattern = q.isEmpty()
                ? null
                : "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";

        Page<Post> found = posts.search(cat, pattern, scope, userId, PageRequest.of(page - 1, 20));
        PageRequest topThree = PageRequest.of(0, 3);
        RankItem featured = page == 1 && scope.equals("all") && cat != null
                ? rank(posts.topByEngagement(cat, pattern, scope, userId, PageRequest.of(0, 1)))
                        .stream().findFirst().orElse(null)
                : null;

        return new Feed(
                postViews(found.getContent(), userId, false),
                featured, page, found.getTotalPages(),
                rank(posts.topByLikes(topThree)),
                rank(posts.topByComments(topThree)),
                latestUpdate(), Categories.COMMUNITY);
    }

    @Transactional(readOnly = true)
    public PostView detail(Integer postId, Integer userId) {
        return postViews(List.of(post(postId)), userId, true).getFirst();
    }

    private List<PostView> postViews(List<Post> found, Integer userId, boolean includeComments) {
        List<Integer> ids = found.stream().map(Post::getId).toList();

        Map<Integer, List<PostOption>> optionsByPost = new HashMap<>();
        Map<Integer, Long> voteCounts = new HashMap<>();
        Map<Integer, Long> likeCounts = new HashMap<>();
        Map<Integer, List<Comment>> commentsByPost = new HashMap<>();
        Map<Integer, Long> commentCounts = new HashMap<>();
        Map<Integer, Integer> myVotes = new HashMap<>();
        Set<Integer> myLikes = new HashSet<>();

        if (!ids.isEmpty()) {
            for (PostOption option : options.findByPostIdInOrderByIdAsc(ids)) {
                optionsByPost.computeIfAbsent(
                        option.getPostId(), k -> new ArrayList<>())
                        .add(option);
            }

            for (Object[] row : votes.countByOption(ids)) {
                voteCounts.put(
                        (Integer) row[0], ((Number) row[1]).longValue());
            }

            for (Object[] row : likes.countByPost(ids)) {
                likeCounts.put(
                        (Integer) row[0], ((Number) row[1]).longValue());
            }

            if (includeComments) {
                for (Comment comment : comments.findWithAuthorByPostIds(ids)) {
                    commentsByPost.computeIfAbsent(comment.getPostId(), key -> new ArrayList<>())
                            .add(comment);
                }
            } else {
                for (Object[] row : comments.countByPostIds(ids)) {
                    commentCounts.put((Integer) row[0], ((Number) row[1]).longValue());
                }
            }

            if (userId != null) {
                for (Object[] row : votes.myVotes(userId, ids)) {
                    myVotes.put((Integer) row[0], (Integer) row[1]);
                }

                myLikes.addAll(likes.myLikes(userId, ids));
            }
        }

        List<PostView> views = new ArrayList<>();

        for (Post post : found) {
            List<PostOption> opts = optionsByPost.getOrDefault(post.getId(), List.of());

            long total = opts.stream()
                    .mapToLong(o -> voteCounts.getOrDefault(o.getId(), 0L))
                    .sum();

            List<OptionView> optionViews = opts.stream().map(option -> {
                long count = voteCounts.getOrDefault(option.getId(), 0L);
                int percent = total == 0
                        ? 0
                        : (int) Math.round(count * 100.0 / total);

                return new OptionView(
                        option.getId(), option.getName(), count, percent);
            }).toList();

            List<CommentView> commentViews = commentsByPost.getOrDefault(post.getId(), List.of())
                    .stream()
                    .map(c -> new CommentView(
                            c.getId(),
                            c.getAuthor().getNickname(),
                            userId != null && userId.equals(c.getUserId()),
                            c.getContent(),
                            c.getCreatedAt()))
                    .toList();

            views.add(new PostView(
                    post.getId(),
                    post.getTitle(),
                    post.getContent(),
                    post.getCategory(),
                    post.getAiRecommendation(),
                    post.getAuthor().getNickname(),
                    userId != null && userId.equals(post.getUserId()),
                    post.getCreatedAt(),
                    total,
                    optionViews,
                    myVotes.get(post.getId()),
                    myLikes.contains(post.getId()),
                    likeCounts.getOrDefault(post.getId(), 0L),
                    includeComments ? commentViews.size() : commentCounts.getOrDefault(post.getId(), 0L),
                    commentViews));
        }

        return views;
    }

    @Transactional(readOnly = true)
    public String latestUpdate() {
        Instant latest = posts.latestUpdate();
        return latest == null ? "" : latest.toString();
    }

    public record WriteResult(Integer id, String warning) {}

    @Transactional
    public WriteResult createPost(
            Integer userId,
            String rawTitle,
            String rawContent,
            String category,
            List<String> rawOptions) {

        String title = strip(rawTitle);
        String content = strip(rawContent);

        List<String> opts = rawOptions == null
                ? List.of()
                : rawOptions.stream()
                        .map(CommunityService::strip)
                        .filter(s -> !s.isEmpty())
                        .toList();

        if (title.isEmpty() || title.length() > 200
                || content.isEmpty() || content.length() > 5000
                || !Categories.COMMUNITY.contains(category)
                || opts.size() < 2 || opts.size() > 5
                || opts.stream().anyMatch(o -> o.length() > 120)) {

            throw ApiException.badRequest(
                    "제목, 내용, 카테고리와 2~5개의 선택지를 확인해주세요. "
                            + "(제목 200자, 내용 5,000자 이내)");
        }

        User author = user(userId);
        String warning = moderation.check(Map.of("title", title, "content", content, "options", opts));
        Post post = posts.save(new Post(author, title, content, category));

        for (String name : opts) {
            options.save(new PostOption(post, name));
        }

        return new WriteResult(post.getId(), warning);
    }

    @Transactional
    public String editPost(
            Integer userId,
            Integer postId,
            String rawTitle,
            String rawContent,
            String category) {

        Post post = ownPost(userId, postId);
        String title = strip(rawTitle);
        String content = strip(rawContent);

        if (title.isEmpty() || title.length() > 200
                || content.isEmpty() || content.length() > 5000
                || !Categories.COMMUNITY.contains(category)) {

            throw ApiException.badRequest(
                    "제목(1~200자), 내용(1~5,000자), 카테고리를 확인해주세요.");
        }

        String warning = moderation.check(Map.of("title", title, "content", content));
        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);
        post.touch();
        return warning;
    }

    @Transactional
    public void deletePost(Integer userId, Integer postId) {
        posts.delete(ownPost(userId, postId));
    }

    @Transactional
    public void vote(Integer userId, Integer postId, Integer optionId) {
        Post post = post(postId);

        PostOption option = options.findById(optionId)
                .filter(o -> o.getPostId().equals(post.getId()))
                .orElseThrow(() -> ApiException.notFound("올바르지 않은 선택지입니다."));

        votes.findByPostIdAndUserId(postId, userId).ifPresentOrElse(
                vote -> vote.setOption(option),
                () -> votes.save(
                        new CommunityVote(post, option, user(userId))));

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

    @Transactional
    public String addComment(
            Integer userId,
            Integer postId,
            String rawContent) {

        String content = commentContent(rawContent);
        Post post = post(postId);

        User author = user(userId);
        String warning = moderation.check(Map.of("comment", content));
        comments.save(new Comment(post, author, content));
        post.touch();
        return warning;
    }

    @Transactional
    public String editComment(
            Integer userId,
            Integer postId,
            Integer commentId,
            String rawContent) {

        String content = commentContent(rawContent);
        Comment comment = ownComment(userId, postId, commentId);

        String warning = moderation.check(Map.of("comment", content));
        comment.setContent(content);
        comment.getPost().touch();
        return warning;
    }

    @Transactional
    public void deleteComment(
            Integer userId,
            Integer postId,
            Integer commentId) {

        Comment comment = ownComment(userId, postId, commentId);
        Post post = comment.getPost();

        comments.delete(comment);
        post.touch();
    }

    private static String commentContent(String raw) {
        String content = strip(raw);

        if (content.isEmpty() || content.length() > 2000) {
            throw ApiException.badRequest(
                    "댓글은 1~2,000자로 작성해주세요.");
        }

        return content;
    }

    private Post post(Integer postId) {
        return posts.findById(postId)
                .orElseThrow(() -> ApiException.notFound("게시글을 찾을 수 없습니다."));
    }

    private Post ownPost(Integer userId, Integer postId) {
        Post post = post(postId);

        if (!post.getUserId().equals(userId)) {
            throw ApiException.forbidden(
                    "본인이 작성한 게시글만 수정/삭제할 수 있습니다.");
        }

        return post;
    }

    private Comment ownComment(
            Integer userId,
            Integer postId,
            Integer commentId) {

        Comment comment = comments.findById(commentId)
                .filter(c -> c.getPostId().equals(postId))
                .orElseThrow(() -> ApiException.notFound("댓글을 찾을 수 없습니다."));

        if (!comment.getUserId().equals(userId)) {
            throw ApiException.forbidden(
                    "본인이 작성한 댓글만 수정/삭제할 수 있습니다.");
        }

        return comment;
    }

    private User user(Integer userId) {
        return users.getReferenceById(userId);
    }

    private static List<RankItem> rank(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new RankItem(
                        (Integer) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
