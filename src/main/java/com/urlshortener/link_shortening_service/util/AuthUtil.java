package com.urlshortener.link_shortening_service.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtil {
    private AuthUtil() {}

    public static Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;

        Object principal = auth.getPrincipal();

        if (principal instanceof Long) return (Long) principal;

        try {
            return Long.valueOf(String.valueOf(principal));
        } catch (Exception ignored) {}

        return null;
    }
}
