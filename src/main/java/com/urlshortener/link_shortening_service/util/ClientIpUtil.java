package com.urlshortener.link_shortening_service.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpUtil {
    private ClientIpUtil() {}

    public static String extractClientIp(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");

        if (xForwardedForHeader != null && !xForwardedForHeader.isBlank()) {
            String first = xForwardedForHeader.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();

        return request.getRemoteAddr();
    }
}