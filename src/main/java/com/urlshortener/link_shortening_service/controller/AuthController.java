package com.urlshortener.link_shortening_service.controller;

import com.urlshortener.link_shortening_service.dto.auth.AuthResponse;
import com.urlshortener.link_shortening_service.request.auth.LoginRequest;
import com.urlshortener.link_shortening_service.request.auth.RefreshRequest;
import com.urlshortener.link_shortening_service.request.auth.RegisterRequest;
import com.urlshortener.link_shortening_service.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "User registration, login and token refresh")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @SecurityRequirements(value = {}) // public in Swagger UI
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(auth.register(req.getEmail(), req.getPassword()));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    @SecurityRequirements(value = {}) // public in Swagger UI
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(auth.login(req.getEmail(), req.getPassword()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and get a new access token")
    @SecurityRequirements(value = {}) // public in Swagger UI
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(auth.refresh(req.getRefreshToken()));
    }
}