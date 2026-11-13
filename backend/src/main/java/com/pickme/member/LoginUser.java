package com.pickme.member;

import java.io.Serializable;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.pickme.common.ApiException;

/** 세션에 저장되는 로그인 정보(인증 principal). Flask 의 session['user_id'] 에 해당. */
public record LoginUser(Integer id) implements Serializable {

    /** 로그인하지 않았으면 null */
    public static Integer currentIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return user.id();
        }
        return null;
    }

    /** 로그인하지 않았으면 401 */
    public static Integer requireId() {
        Integer id = currentIdOrNull();
        if (id == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return id;
    }
}
