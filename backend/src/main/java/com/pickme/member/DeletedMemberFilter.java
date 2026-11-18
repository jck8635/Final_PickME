package com.pickme.member;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DeletedMemberFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public DeletedMemberFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        Integer id = LoginUser.currentIdOrNull();

        if (id != null && !users.existsById(id)) {
            var session = request.getSession(false);

            if (session != null) {
                session.invalidate();
            }

            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
