package com.urlshortener.link_shortening_service.service.auth;

import com.urlshortener.link_shortening_service.entity.User;
import com.urlshortener.link_shortening_service.entity.RefreshToken;
import com.urlshortener.link_shortening_service.repo.RefreshTokenRepository;
import com.urlshortener.link_shortening_service.util.TokenHash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    // default 30 days; configurable via properties
    @Value("${jwt.refresh.expiration-seconds:2592000}")
    private long refreshTtlSeconds;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // this method creates a refresh token for the given user and returns plain text
    @Transactional
    public String issueNew(User user) {
        var existing = refreshTokenRepository.findByUser_Id(user.getId()).orElse(null);
        String raw = UUID.randomUUID().toString();
        String hash = TokenHash.sha256Hex(raw);
        LocalDateTime exp = LocalDateTime.now().plusSeconds(refreshTtlSeconds);

        if (existing == null) {
            existing = RefreshToken.builder()
                    .user(user)
                    .tokenHash(hash)
                    .expiresAt(exp)
                    .build();
        } else {
            existing.setTokenHash(hash);
            existing.setExpiresAt(exp);
        }
        refreshTokenRepository.save(existing);
        // this is the plan text. And will go back to client
        return raw;
    }


    // This method validates incoming refresh token and rotates it atomically
    // returns the user and the new plain text token.
    @Transactional
    public RefreshRotation validateAndRotate(String incomingRaw) {
        String h = TokenHash.sha256Hex(incomingRaw);
        var refreshTokenRecord = refreshTokenRepository.findByTokenHash(h).orElseThrow(() -> new IllegalStateException("Invalid refresh token"));

        if (refreshTokenRecord.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Refresh token expired");
        }

        String newRaw = UUID.randomUUID().toString();
        refreshTokenRecord.setTokenHash(TokenHash.sha256Hex(newRaw));
        refreshTokenRecord.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTtlSeconds));
        refreshTokenRepository.save(refreshTokenRecord);

        return new RefreshRotation(refreshTokenRecord.getUser(), newRaw);
    }
}