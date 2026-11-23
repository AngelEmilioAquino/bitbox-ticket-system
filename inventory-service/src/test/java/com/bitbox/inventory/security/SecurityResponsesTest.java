package com.bitbox.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bitbox.inventory.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

/** Order and shape of 401 / 403 / 400 responses. */
class SecurityResponsesTest extends IntegrationTest {

    private static final String INVALID_EVENT = "{\"name\": \"\", \"totalTickets\": 0}";

    @Autowired
    private ProblemDetailSecurityHandler problemDetailSecurityHandler;

    @Test
    void missingTokenWithInvalidBodyIs401NotValidation400() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(INVALID_EVENT))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void invalidTokenIs401WithProblemDetail() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(INVALID_EVENT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void validTokenWithInvalidBodyIs400() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(createUser("ana@bitbox.com")))
                        .contentType(MediaType.APPLICATION_JSON).content(INVALID_EVENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * The spec defines no roles, so no endpoint can currently produce a 403. The handler wired into the
     * filter chain is still verified so a future authorization rule answers with a consistent 403 body.
     */
    @Test
    void accessDeniedIsRenderedAs403ProblemDetail() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        problemDetailSecurityHandler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"code\":\"ACCESS_DENIED\"");
    }
}
