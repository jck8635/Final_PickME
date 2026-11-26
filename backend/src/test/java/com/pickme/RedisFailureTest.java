package com.pickme;
import com.pickme.member.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:pickme-failure;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedisFailureTest {
    @Autowired MockMvc mvc;
    @MockitoBean TokenService tokens;
    private RedisConnectionFailureException failure(){return new RedisConnectionFailureException("simulated Redis outage");}
    @Test void protectedMemberRequestFailsClosed() throws Exception {
        when(tokens.verify("test-token")).thenThrow(failure());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer test-token")).andExpect(status().isServiceUnavailable());
    }
    @Test void protectedAdminRequestFailsClosed() throws Exception {
        when(tokens.verify("test-token")).thenThrow(failure());
        mvc.perform(get("/api/admin/overview").header("Authorization","Bearer test-token")).andExpect(status().isServiceUnavailable());
    }
    @Test void memberLoginFailsClosed() throws Exception {
        doThrow(failure()).when(tokens).limit(eq("member"),anyString(),anyString());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\"test@example.test\",\"password\":\"test-password\"}")).andExpect(status().isServiceUnavailable());
    }
    @Test void adminLoginFailsClosed() throws Exception {
        doThrow(failure()).when(tokens).limit(eq("admin"),anyString(),anyString());
        mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"test\",\"password\":\"test-password\"}")).andExpect(status().isServiceUnavailable());
    }
    @Test void refreshFailsClosed() throws Exception {
        when(tokens.refresh(any(),any())).thenThrow(failure());
        mvc.perform(post("/api/auth/refresh").with(csrf())).andExpect(status().isServiceUnavailable());
    }
}
