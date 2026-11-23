package com.bitbox.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bitbox.inventory.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerStoresBcryptHashNeverThePlainPassword() throws Exception {
        register("Ana@Bitbox.com", "S3cret-pass")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("ana@bitbox.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE email = 'ana@bitbox.com'", String.class);
        assertThat(stored).isNotEqualTo("S3cret-pass").startsWith("$2");
        assertThat(passwordEncoder.matches("S3cret-pass", stored)).isTrue();
    }

    @Test
    void registerRejectsDuplicateEmailWith409() throws Exception {
        register("ana@bitbox.com", "S3cret-pass").andExpect(status().isCreated());

        register("ANA@bitbox.com", "Other-pass1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        register("not-an-email", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginReturnsSignedJwtForValidCredentials() throws Exception {
        register("ana@bitbox.com", "S3cret-pass");

        login("ana@bitbox.com", "S3cret-pass")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(not(emptyString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownEmailIdentically() throws Exception {
        register("ana@bitbox.com", "S3cret-pass");

        login("ana@bitbox.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("nobody@bitbox.com", "S3cret-pass")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentialsJson(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentialsJson(email, password)));
    }

    private static String credentialsJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
