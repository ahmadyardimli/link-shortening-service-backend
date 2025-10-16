package com.urlshortener.link_shortening_service.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    // this is the "Bearer <jwt>"
    private String accessToken;

    // this is my raw string
    private String refreshToken;
    private Long userId;
}
