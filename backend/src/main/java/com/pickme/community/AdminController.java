package com.pickme.community;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.pickme.common.ApiException;
import com.pickme.member.UserRepository;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JdbcTemplate db;
    private final UserRepository users;
    private final PostRepository posts;
    private final PostOptionRepository options;
    private final ReportService reports;

    public AdminController(
            JdbcTemplate db,
            UserRepository users,
            PostRepository posts,
            PostOptionRepository options,
            ReportService reports) {

        this.db = db;
        this.users = users;
        this.posts = posts;
        this.options = options;
        this.reports = reports;
    }

    public record Page<T>(
            List<T> items,
            long total,
            int page,
            int size) {
    }

    public record MemberRow(
            Integer id,
            String email,
            String nickname,
            long posts) {
    }

    public record PostRow(
            Integer id,
            String title,
            String category,
            String author,
            Instant createdAt) {
    }

    public record ReportRow(
            Integer id,
            Integer postId,
            String title,
            String reporter,
            String reason,
            String status,
            Instant createdAt) {
    }

    public record Detail(
            Integer id,
            String title,
            String content,
            String author,
            String category,
            Instant createdAt,
            List<String> options) {
    }

    @GetMapping("/overview")
    public Map<String, Long> overview() {
        return Map.of(
                "members",
                count("select count(*) from \"user\""),

                "posts",
                count("select count(*) from post"),

                "pending",
                count("""
                        select count(*)
                        from post_reports
                        where status = 'PENDING'
                        """));
    }

    @GetMapping("/members")
    public Page<MemberRow> members(
            @RequestParam(
                    name = "q",
                    defaultValue = "") String q,
            @RequestParam(
                    name = "page",
                    defaultValue = "0") int page) {

        checkPage(page);

        String pattern = pattern(q);

        String from = """
                from "user" u
                where lower(u.email) like ?
                   or lower(u.nickname) like ?
                """;

        long total = count(
                "select count(*) " + from,
                pattern,
                pattern);

        var items = db.query("""
                select u.id, u.email, u.nickname,
                    (
                        select count(*)
                        from post p
                        where p.user_id = u.id
                    ) as posts
                """ + from + " order by u.id desc limit 10 offset ?",
                (rs, n) -> new MemberRow(
                        rs.getInt("id"),
                        rs.getString("email"),
                        rs.getString("nickname"),
                        rs.getLong("posts")),
                pattern,
                pattern,
                page * 10);

        return new Page<>(items, total, page, 10);
    }

    @DeleteMapping("/members/{id}")
    @Transactional
    public Map<String, String> deleteMember(
            @PathVariable("id") Integer id) {

        users.delete(
                users.findById(id)
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "회원을 찾을 수 없습니다.")));

        users.flush();

        return Map.of(
                "message",
                "회원과 관련 데이터를 삭제했습니다.");
    }

    @GetMapping("/posts")
    public Page<PostRow> posts(
            @RequestParam(
                    name = "q",
                    defaultValue = "") String q,
            @RequestParam(
                    name = "page",
                    defaultValue = "0") int page) {

        checkPage(page);

        String pattern = pattern(q);

        String from = """
                from post p
                join "user" u on u.id = p.user_id
                where lower(p.title) like ?
                   or lower(u.nickname) like ?
                """;

        var items = db.query("""
                select p.id, p.title, p.category,
                       p.created_at, u.nickname
                """ + from
                        + " order by p.created_at desc, p.id desc"
                        + " limit 10 offset ?",
                (rs, n) -> new PostRow(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("category"),
                        rs.getString("nickname"),
                        rs.getTimestamp("created_at").toInstant()),
                pattern,
                pattern,
                page * 10);

        return new Page<>(
                items,
                count(
                        "select count(*) " + from,
                        pattern,
                        pattern),
                page,
                10);
    }

    @GetMapping("/posts/{id}")
    @Transactional(readOnly = true)
    public Detail detail(
            @PathVariable("id") Integer id) {

        Post post = posts.findById(id)
                .orElseThrow(() ->
                        ApiException.notFound(
                                "게시글을 찾을 수 없습니다."));

        return new Detail(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getNickname(),
                post.getCategory(),
                post.getCreatedAt(),
                options.findByPostIdInOrderByIdAsc(List.of(id))
                        .stream()
                        .map(PostOption::getName)
                        .toList());
    }

    @DeleteMapping("/posts/{id}")
    public Map<String, String> deletePost(
            @PathVariable("id") Integer id) {

        reports.deletePost(id);

        return Map.of(
                "message",
                "게시글과 관련 데이터를 삭제했습니다.");
    }

    @GetMapping("/reports")
    public Page<ReportRow> reports(
            @RequestParam(
                    name = "q",
                    defaultValue = "") String q,
            @RequestParam(
                    name = "status",
                    defaultValue = "PENDING") String status,
            @RequestParam(
                    name = "page",
                    defaultValue = "0") int page) {

        checkPage(page);

        if (!List.of("ALL", "PENDING", "REJECTED").contains(status)) {
            throw ApiException.badRequest("상태를 확인해주세요.");
        }

        String pattern = pattern(q);

        String from = """
                from post_reports r
                join post p on p.id = r.post_id
                join "user" u on u.id = r.reporter_id
                where (? = 'ALL' or r.status = ?)
                  and (
                      lower(p.title) like ?
                      or lower(r.reason) like ?
                  )
                """;

        var items = db.query("""
                select r.id, r.post_id, p.title, u.nickname,
                       r.reason, r.status, r.created_at
                """ + from
                        + " order by r.created_at desc, r.id desc"
                        + " limit 10 offset ?",
                (rs, n) -> new ReportRow(
                        rs.getInt("id"),
                        rs.getInt("post_id"),
                        rs.getString("title"),
                        rs.getString("nickname"),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()),
                status,
                status,
                pattern,
                pattern,
                page * 10);

        return new Page<>(
                items,
                count(
                        "select count(*) " + from,
                        status,
                        status,
                        pattern,
                        pattern),
                page,
                10);
    }

    @PatchMapping("/reports/{id}/reject")
    public Map<String, String> reject(
            @PathVariable("id") Integer id) {

        reports.reject(id);

        return Map.of("message", "신고를 기각했습니다.");
    }

    private long count(String sql, Object... args) {
        return db.queryForObject(sql, Long.class, args);
    }

    private void checkPage(int page) {
        if (page < 0 || page > 100000) {
            throw ApiException.badRequest("페이지를 확인해주세요.");
        }
    }

    private String pattern(String q) {
        String text = q == null
                ? ""
                : q.strip().toLowerCase(Locale.ROOT);

        if (text.length() > 100) {
            throw ApiException.badRequest(
                    "검색어는 100자 이내로 입력해주세요.");
        }

        return "%"
                + text.replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                + "%";
    }
}
