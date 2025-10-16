package com.urlshortener.link_shortening_service.controller;

import com.urlshortener.link_shortening_service.dto.ShortenUrl;
import com.urlshortener.link_shortening_service.dto.UrlStatistics;
import com.urlshortener.link_shortening_service.dto.UserStatistics;
import com.urlshortener.link_shortening_service.request.ShortenUrlRequest;
import com.urlshortener.link_shortening_service.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping
@Tag(name = "URL Shortener", description = "Create short links, redirects and statistics")
public class UrlShortenerController {

    private final UrlShortenerService service;

    @PostMapping("/shorten")
    @Operation(summary = "Create a short URL (anonymous allowed)")
    @SecurityRequirements(value = {}) // public in Swagger UI
    public ResponseEntity<ShortenUrl> shorten(@Valid @RequestBody ShortenUrlRequest request) {
        ShortenUrl dto = service.shortenUrl(request);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Redirect to the original URL")
    @SecurityRequirements(value = {}) // public in Swagger UI
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = service.getOriginalUrl(code);
        return ResponseEntity.status(302).location(URI.create(target)).build();
    }

    @GetMapping("/stats/{code}")
    @Operation(summary = "Public stats for a single short URL (optionally gated by userId)")
    @SecurityRequirements(value = {}) // public in Swagger UI
    public ResponseEntity<UrlStatistics> stats(@PathVariable String code,
                                               @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(service.getUrlStatistics(code, userId));
    }

    @GetMapping("/users/me/stats")
    @Operation(summary = "Statistics for the currently logged-in user")
    @SecurityRequirement(name = "bearerAuth") // secured in Swagger UI
    public ResponseEntity<UserStatistics> myStats() {
        return ResponseEntity.ok(service.getMyUserStatistics());
    }

    @GetMapping("/users/{userId}/stats")
    @Operation(summary = "Self-only stats by userId (must match your own id)")
    @SecurityRequirement(name = "bearerAuth") // secured in Swagger UI
    public ResponseEntity<UserStatistics> userStats(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getOwnStats(userId));
    }
}
