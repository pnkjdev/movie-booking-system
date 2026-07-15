package com.moviebooking.auth;

import com.moviebooking.support.IntegrationTestBase;
import com.moviebooking.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthRbacIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("register -> login round trip issues a working JWT")
    void registerAndLogin() throws Exception {
        String email = "register-test-" + System.nanoTime() + "@test.dev";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "New Customer", "email", email, "password", "Password@123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        String token = login(email, "Password@123");
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("duplicate email registration is rejected with 409")
    void duplicateEmail() throws Exception {
        User existing = factory.customer();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Dup", "email", existing.getEmail(), "password", "Password@123"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("wrong password yields 401 without leaking which field was wrong")
    void wrongPassword() throws Exception {
        User user = factory.customer();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(), "password", "WrongPassword@1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("customers cannot reach admin endpoints")
    void customerForbiddenOnAdmin() throws Exception {
        User customer = factory.customer();
        String token = login(customer.getEmail(), TestDataFactory.PASSWORD);
        mockMvc.perform(post("/api/v1/admin/cities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Nope City\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("admins can manage the catalog")
    void adminAllowed() throws Exception {
        User admin = factory.admin();
        String token = login(admin.getEmail(), TestDataFactory.PASSWORD);
        mockMvc.perform(post("/api/v1/admin/cities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Admin City " + System.nanoTime() + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("protected endpoints reject anonymous and garbage tokens; browsing stays open")
    void anonymousAccess() throws Exception {
        mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/cities")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/movies")).andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
