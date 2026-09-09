package com.waypoint.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetricsIntegrationTests {
    private static final String ADMIN_ID = "test-admin";
    private static final String ADMIN_PASSWORD = "test-admin-password-12345";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheusEndpointRequiresAdminBasicAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic(ADMIN_ID, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusEndpointExportsRuntimeAndWaypointMetricsForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/ai/models"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic(ADMIN_ID, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("http_server_requests")))
                .andExpect(content().string(containsString("waypoint_api_requests_total")))
                .andExpect(content().string(containsString("area=\"ai\"")));
    }
}
