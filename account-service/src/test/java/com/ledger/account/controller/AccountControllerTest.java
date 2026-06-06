package com.ledger.account.controller;

import com.ledger.account.service.AccountService;
import com.ledger.common.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Test
    public void testApplyTransaction_GenericException() throws Exception {
        doThrow(new RuntimeException("Simulated uncaught exception"))
                .when(accountService).processTransaction(eq("acct-123"), any(TransactionRequest.class));

        String requestBody = "{\"eventId\":\"evt-1\",\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .header("Authorization", "Bearer internal-gateway-m2m-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected internal error occurred. Please refer to trace ID for investigation."));
    }

    @Test
    public void testApplyTransaction_ValidationException() throws Exception {
        // Validation failure (e.g. empty event ID, negative amount)
        String requestBody = "{\"eventId\":\"\",\"type\":\"CREDIT\",\"amount\":-10.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .header("Authorization", "Bearer internal-gateway-m2m-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Input validation failed"));
    }
}
