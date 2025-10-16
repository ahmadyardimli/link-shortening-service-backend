package com.urlshortener.link_shortening_service.service.auth;

import com.urlshortener.link_shortening_service.dto.auth.AuthResponse;
import com.urlshortener.link_shortening_service.entity.User;
import com.urlshortener.link_shortening_service.security.JwtService;
import com.urlshortener.link_shortening_service.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserService users;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwt;

    public AuthService(UserService users, RefreshTokenService refreshTokens, JwtService jwt) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
    }

    public AuthResponse register(String email, String password) {
        User user = users.register(email, password);
        String access = jwt.generateAccessToken(user.getId());
        String refresh = refreshTokens.issueNew(user);
        return new AuthResponse("Bearer " + access, refresh, user.getId());
    }

    public AuthResponse login(String email, String password) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Incorrect email or password"));

        if (!users.matches(user, password)) {
            throw new IllegalStateException("Incorrect email or password");
        }

        String access = jwt.generateAccessToken(user.getId());
        String refresh = refreshTokens.issueNew(user);
        return new AuthResponse("Bearer " + access, refresh, user.getId());
    }

    public AuthResponse refresh(String incomingRefreshToken) {
        RefreshRotation refreshRotation = refreshTokens.validateAndRotate(incomingRefreshToken);
        String access = jwt.generateAccessToken(refreshRotation.getUser().getId());
        return new AuthResponse("Bearer " + access, refreshRotation.getNewRaw(), refreshRotation.getUser().getId());
    }
}