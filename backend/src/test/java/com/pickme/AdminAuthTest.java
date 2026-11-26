package com.pickme;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AdminAuthTest extends SecurityTestSupport {
    @Test void adminCanAccessOverview() throws Exception {
        var login=adminLogin();
        mvc.perform(get("/api/admin/overview").header("Authorization","Bearer "+login.token())).andExpect(status().isOk());
    }
    @Test void memberCannotAccessAdmin() throws Exception {
        var login=memberLogin();
        mvc.perform(get("/api/admin/overview").header("Authorization","Bearer "+login.token())).andExpect(status().isForbidden());
    }
    @Test void anonymousCannotAccessAdmin() throws Exception {
        mvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
    }
    @Test void wrongAdminPasswordRejected() throws Exception {
        mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username","invalid-"+java.util.UUID.randomUUID(),"password","wrong")))).andExpect(status().isUnauthorized());
    }
    @Test void refreshedAdminRetainsRole() throws Exception {
        var login=refresh(adminLogin());
        mvc.perform(get("/api/admin/overview").header("Authorization","Bearer "+login.token())).andExpect(status().isOk());
    }
    @Test void adminLogoutRevokesAccess() throws Exception {
        var login=adminLogin();
        mvc.perform(post("/api/admin/auth/logout").with(csrf()).cookie(login.cookie()).header("Authorization","Bearer "+login.token())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/overview").header("Authorization","Bearer "+login.token())).andExpect(status().isUnauthorized());
    }
}
