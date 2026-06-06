package com.ledger.gateway.controller;

import com.ledger.gateway.security.JwtHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    private final JwtHelper jwtHelper;

    public AuthController(JwtHelper jwtHelper) {
        this.jwtHelper = jwtHelper;
    }

    @GetMapping("/auth/token")
    public Map<String, String> getTestToken(@RequestParam(defaultValue = "test-user") String client) {
        String token = jwtHelper.generateToken(client);
        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("token_type", "Bearer");
        return response;
    }
}
