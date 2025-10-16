package com.urlshortener.link_shortening_service.service.auth;

import com.urlshortener.link_shortening_service.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RefreshRotation {
    private final User user;
    private final String newRaw;
}