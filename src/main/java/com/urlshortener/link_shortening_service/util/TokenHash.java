package com.urlshortener.link_shortening_service.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class TokenHash {
    private TokenHash() {}

    public static String sha256Hex(String raw) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexResult = new StringBuilder(digest.length * 2);
            for (byte byteValue : digest) hexResult.append(String.format("%02x", byteValue));
            return hexResult.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}