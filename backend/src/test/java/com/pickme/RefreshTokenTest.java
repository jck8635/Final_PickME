package com.pickme;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class RefreshTokenTest extends SecurityTestSupport {
    @Test void rotationAndReuseRevokesFamily() throws Exception {
        var old=memberLogin(); var next=refresh(old);
        assertThat(next.cookie().getValue()).isNotEqualTo(old.cookie().getValue());
        assertThat(next.token()).isNotEqualTo(old.token());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+next.token())).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").isNumber());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(old.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(next.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+next.token())).andExpect(status().isUnauthorized());
    }
    @Test void refreshRequiresCookie() throws Exception {
        mvc.perform(post("/api/auth/refresh").with(csrf())).andExpect(status().isUnauthorized());
    }
    @Test void refreshRequiresCsrf() throws Exception {
        var login=memberLogin();
        mvc.perform(post("/api/auth/refresh").cookie(login.cookie())).andExpect(status().isForbidden());
    }
    @Test void sessionHasExpiry() throws Exception {
        var login=memberLogin();
        String key="pickme:auth:session:"+login.cookie().getValue().split("\\.")[0];
        assertThat(redis.getExpire(key)).isBetween(1L,604800L);
        redis.delete(key);
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(login.cookie())).andExpect(status().isUnauthorized());
    }
}
