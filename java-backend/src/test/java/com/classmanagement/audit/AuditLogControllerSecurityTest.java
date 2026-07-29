package com.classmanagement.audit;

import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.config.SecurityConfig;
import com.classmanagement.security.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
@Import(SecurityConfig.class)
class AuditLogControllerSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuditLogService service;
    @MockitoBean JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void forwardJwtFilter() throws Exception {
        doAnswer(call -> {
            ServletRequest request = call.getArgument(0);
            ServletResponse response = call.getArgument(1);
            FilterChain chain = call.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "admin")
    void adminCanQueryFilters() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageResponse<>(
                List.of(), 0, 1, 20));

        mvc.perform(get("/api/audit-logs")
                        .param("page", "2")
                        .param("page_size", "30")
                        .param("actor_email", " admin@ ")
                        .param("category", " fees ")
                        .param("action", " create ")
                        .param("from", "2026-07-29T00:00:00Z")
                        .param("to", "2026-07-29T23:59:59Z")
                        .param("q", " payment "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page_size").value(20));

        var pagination = org.mockito.ArgumentCaptor.forClass(Pagination.class);
        var filter = org.mockito.ArgumentCaptor.forClass(AuditLogFilter.class);
        verify(service).list(pagination.capture(), filter.capture());
        assertEquals(new Pagination(2, 30), pagination.getValue());
        assertEquals(new AuditLogFilter(
                "fees", "admin@", "create",
                Instant.parse("2026-07-29T00:00:00Z"),
                Instant.parse("2026-07-29T23:59:59Z"), "payment"), filter.getValue());
    }

    @Test
    @WithMockUser(authorities = "admin")
    void rejectsMalformedInstant() throws Exception {
        mvc.perform(get("/api/audit-logs").param("from", "2026-07-29"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @WithMockUser(authorities = "teacher")
    void teacherIsForbidden() throws Exception {
        mvc.perform(get("/api/audit-logs")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserIsRejected() throws Exception {
        mvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "admin")
    void mutationMethodsAreRejected() throws Exception {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.PATCH, HttpMethod.DELETE)) {
            mvc.perform(request(method, "/api/audit-logs"))
                    .andExpect(status().isForbidden());
        }
    }
}
