package com.pickme.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {

    @Query("select u from User u where lower(u.email) = :email")
    Optional<User> findByEmailIgnoreCase(@Param("email") String lowerEmail);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Integer id);
}
