
package com.pickme.member;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Year;
import com.pickme.common.ApiException;

@Service
public class UserProfileService {

    private final JdbcTemplate jdbc;

    public UserProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ProfileResponse(
            Integer userId,
            Integer birthYear,
            String region,
            String occupation,
            String mbti,
            List<String> interests,
            List<String> dislikedFoods,
            String bio) {
    }

    public ProfileResponse getProfile(Integer userId) {
        String sql = """
                SELECT user_id, birth_year, region, occupation,
                       mbti, interests, disliked_foods, bio
                FROM public.user_profile
                WHERE user_id = ?
                """;

        List<ProfileResponse> results = jdbc.query(
                sql,
                (rs, rowNum) -> {
                    java.sql.Array interestsArray = rs.getArray("interests");
                    java.sql.Array dislikedArray = rs.getArray("disliked_foods");

                    List<String> interests = interestsArray == null
                            ? List.of()
                            : List.of((String[]) interestsArray.getArray());

                    List<String> dislikedFoods = dislikedArray == null
                            ? List.of()
                            : List.of((String[]) dislikedArray.getArray());

                    return new ProfileResponse(
                            rs.getInt("user_id"),
                            (Integer) rs.getObject("birth_year"),
                            rs.getString("region"),
                            rs.getString("occupation"),
                            rs.getString("mbti"),
                            interests,
                            dislikedFoods,
                            rs.getString("bio"));
                },
                userId);

        // 기존 회원은 아직 프로필을 입력하지 않았을 수 있음
        if (results.isEmpty()) {
            return new ProfileResponse(
                    userId, null, null, null, null,
                    List.of(), List.of(), null);
        }

        return results.get(0);
    }

    public record ProfileUpdateRequest(
            Integer birthYear,
            String region,
            String occupation,
            String mbti,
            List<String> interests,
            List<String> dislikedFoods,
            String bio) {
    }

    @Transactional
    public ProfileResponse updateProfile(
            Integer userId,
            ProfileUpdateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException(
                    "프로필 정보를 입력해주세요.");
        }

        if (req.birthYear() != null &&
                (req.birthYear() < 1900 ||
                        req.birthYear() > 2100)) {
            throw new IllegalArgumentException(
                    "출생연도가 올바르지 않습니다.");
        }

        String mbti = req.mbti();

        if (mbti != null && !mbti.isBlank()) {
            mbti = mbti.strip().toUpperCase();

            List<String> validMbti = List.of(
                    "ISTJ", "ISFJ", "INFJ", "INTJ",
                    "ISTP", "ISFP", "INFP", "INTP",
                    "ESTP", "ESFP", "ENFP", "ENTP",
                    "ESTJ", "ESFJ", "ENFJ", "ENTJ");

            if (!validMbti.contains(mbti)) {
                throw ApiException.badRequest(
                        "올바른 MBTI 유형을 선택해주세요.");
            }
        } else {
            mbti = null;
        }

        List<String> interests = req.interests() == null
                ? List.of()
                : req.interests();

        List<String> dislikedFoods = req.dislikedFoods() == null
                ? List.of()
                : req.dislikedFoods();

        String sql = """
                INSERT INTO public.user_profile (
                    user_id,
                    birth_year,
                    region,
                    occupation,
                    mbti,
                    interests,
                    disliked_foods,
                    bio
                )
                VALUES (
                    ?, ?, ?, ?, ?,
                    ?::text[],
                    ?::text[],
                    ?
                )
                ON CONFLICT (user_id)
                DO UPDATE SET
                    birth_year = EXCLUDED.birth_year,
                    region = EXCLUDED.region,
                    occupation = EXCLUDED.occupation,
                    mbti = EXCLUDED.mbti,
                    interests = EXCLUDED.interests,
                    disliked_foods = EXCLUDED.disliked_foods,
                    bio = EXCLUDED.bio,
                    updated_at = NOW()
                """;

        jdbc.update(
                sql,
                userId,
                req.birthYear(),
                req.region(),
                req.occupation(),
                mbti,
                interests.toArray(new String[0]),
                dislikedFoods.toArray(new String[0]),
                req.bio());

        return getProfile(userId);
    }

    private void validateProfile(ProfileUpdateRequest req) {
        validateProfile(req);

        Integer birthYear = req.birthYear();

        if (birthYear != null &&
                (birthYear < 1900 ||
                        birthYear > Year.now().getValue())) {
            throw ApiException.badRequest(
                    "올바른 출생연도를 입력해주세요.");
        }

        validateText(req.region(), 100, "거주 지역");
        validateText(req.occupation(), 100, "직업");
        validateText(req.bio(), 2000, "자기소개");

        validateList(req.interests(), "관심사");
        validateList(req.dislikedFoods(), "싫어하는 음식");
    }

    private void validateText(
            String value,
            int maxLength,
            String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw ApiException.badRequest(
                    fieldName + "은(는) " +
                            maxLength + "자 이내로 입력해주세요.");
        }
    }

    private void validateList(
            List<String> values,
            String fieldName) {
        if (values == null)
            return;

        if (values.size() > 30) {
            throw ApiException.badRequest(
                    fieldName + "은(는) 최대 30개까지 입력할 수 있습니다.");
        }

        for (String value : values) {
            if (value == null ||
                    value.isBlank() ||
                    value.length() > 100) {
                throw ApiException.badRequest(
                        fieldName + "의 각 항목은 1~100자로 입력해주세요.");
            }
        }
    }

}
