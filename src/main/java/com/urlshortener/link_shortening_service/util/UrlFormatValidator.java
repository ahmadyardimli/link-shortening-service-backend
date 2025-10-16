package com.urlshortener.link_shortening_service.util;

import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.stereotype.Component;

@Component
public class UrlFormatValidator {
    private final UrlValidator apacheValidator =
            new UrlValidator(new String[] { "http", "https" });

    public boolean isValidUrl(String url) {
        if (url == null) return false;

        String trimmed = url.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2048) return false;

        return apacheValidator.isValid(trimmed);
    }
}
