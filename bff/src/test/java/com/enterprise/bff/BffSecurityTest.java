package com.enterprise.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BffSecurityTest {

    @Autowired MockMvc mockMvc;

    @Test
    void userInfo_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/bff/user-info"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userInfo_authenticated_returnsUserInfo() throws Exception {
        mockMvc.perform(get("/bff/user-info")
                .with(oidcLogin()
                    .idToken(token -> token
                        .subject("user-123")
                        .claim("email", "john@example.com")
                        .claim("given_name", "John")
                        .claim("family_name", "Doe")
                        .claim("preferred_username", "john.doe")
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                    )
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sub").value("user-123"))
            .andExpect(jsonPath("$.email").value("john@example.com"))
            .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void userInfo_authenticated_rolesEmptyWithoutRealmAccess() throws Exception {
        mockMvc.perform(get("/bff/user-info")
                .with(oidcLogin()
                    .idToken(token -> token
                        .subject("user-456")
                        .claim("preferred_username", "jane.doe")
                    )
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles").isArray())
            .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void protectedProxy_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/bff/api/items"))
            .andExpect(status().isUnauthorized());
    }
}
