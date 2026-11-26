package com.pickme;
import org.junit.jupiter.api.Test;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class JwtSecurityTest extends SecurityTestSupport {
    @Test void tamperedSignatureRejected() throws Exception {
        var login=memberLogin(); String[] parts=login.token().split("\\.");
        parts[2]=(parts[2].startsWith("A")?"B":"A")+parts[2].substring(1);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+String.join(".",parts))).andExpect(status().isUnauthorized());
    }
    @Test void malformedJwtRejected() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void memberLogoutRevokesAccess() throws Exception {
        var login=memberLogin();
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(login.cookie()).header("Authorization","Bearer "+login.token())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+login.token())).andExpect(status().isUnauthorized());
    }
}
