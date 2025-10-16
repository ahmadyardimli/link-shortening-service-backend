package com.urlshortener.link_shortening_service.service;

import com.urlshortener.link_shortening_service.dto.ShortenUrl;
import com.urlshortener.link_shortening_service.dto.UrlStatistics;
import com.urlshortener.link_shortening_service.dto.UserStatistics;
import com.urlshortener.link_shortening_service.entity.Url;
import com.urlshortener.link_shortening_service.exception.InvalidUrlException;
import com.urlshortener.link_shortening_service.exception.ResourceNotFoundException;
import com.urlshortener.link_shortening_service.exception.UrlExpiredException;
import com.urlshortener.link_shortening_service.mapper.UrlMapper;
import com.urlshortener.link_shortening_service.repo.UrlRepository;
import com.urlshortener.link_shortening_service.request.ShortenUrlRequest;
import com.urlshortener.link_shortening_service.util.AuthUtil;
import com.urlshortener.link_shortening_service.util.UrlFormatValidator;
import com.urlshortener.link_shortening_service.util.UrlSafetyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerService {
    private final UrlRepository urlRepository;
    private final UrlMapper urlMapper;
    private final UrlFormatValidator urlValidator;
    private final UrlSafetyValidator urlSafety;

    @Value("${url.shortener.base-url}")
    private String baseUrl;

    @Value("${url.shortener.code-length:6}")
    private int codeLength;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // Bitly-like behaviour here
    // reuse existing per user + long URL by default unless explicitly requested not to.
    @Transactional
    public ShortenUrl shortenUrl(ShortenUrlRequest request) {
        Long userId = AuthUtil.currentUserIdOrNull();

        log.info("Shortening URL: {} (userId={}, reuseExisting={})", request.getUrl(), userId, request.getReuseExisting());

        if (!urlValidator.isValidUrl(request.getUrl())) {
            throw new InvalidUrlException("Invalid URL format: " + request.getUrl());
        }
        if (!urlSafety.isSafe(request.getUrl())) {
            throw new InvalidUrlException("URL points to a disallowed or private host");
        }

        // if the client didn’t say opposite, default to reusing an existing link.
        boolean reuse = request.getReuseExisting() == null || Boolean.TRUE.equals(request.getReuseExisting());

        // if a custom alias is provided, I always create a new record.
        if (request.getCustomAlias() != null && !request.getCustomAlias().isEmpty()) {
            if (urlRepository.existsByCustomAlias(request.getCustomAlias())
                    || urlRepository.existsByShortCode(request.getCustomAlias())) {
                throw new InvalidUrlException("Custom alias already exists");
            }

            String shortCode = request.getCustomAlias();
            Url entity = urlMapper.toEntity(request, shortCode, userId);
            entity = urlRepository.save(entity);

            ShortenUrl dto = urlMapper.apply(entity);
            dto.setShortUrl(buildShortUrl(shortCode));
            return dto;
        }

        // reuse path. Try to find a non-expired existing link for this user + long URL.
        if (reuse) {
            var existing = urlRepository.findReusableLink(userId, request.getUrl()).orElse(null);
            if (existing != null) {
                ShortenUrl dto = urlMapper.apply(existing);
                dto.setShortUrl(buildShortUrl(existing.getShortCode()));
                return dto;
            }
        }

        // no reuse or not found. create a new short code.
        String shortCode = generateUniqueShortCode();
        Url entity = urlMapper.toEntity(request, shortCode, userId);
        entity = urlRepository.save(entity);

        ShortenUrl dto = urlMapper.apply(entity);
        dto.setShortUrl(buildShortUrl(shortCode));
        return dto;
    }

    @Transactional
    public String getOriginalUrl(String shortCode) {
        log.info("Retrieving original URL for short code: {}", shortCode);

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found: " + shortCode));

        if (url.isExpired()) {
            throw new UrlExpiredException("This short URL has expired");
        }

        url.incrementClickCount();
        urlRepository.save(url);

        return url.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public UrlStatistics getUrlStatistics(String shortCode, Long userId) {
        log.info("Retrieving statistics for short code: {}", shortCode);

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found: " + shortCode));

        if (userId != null && url.getUserId() != null && !url.getUserId().equals(userId)) {
            throw new SecurityException("You don't have permission to view these statistics");
        }

        return urlMapper.toStats(url);
    }

    // convenience for /users/me/stats
    @Transactional(readOnly = true)
    public UserStatistics getMyUserStatistics() {
        Long userId = AuthUtil.currentUserIdOrNull();
        return getUserStatistics(userId);
    }

    @Transactional(readOnly = true)
    public UserStatistics getUserStatistics(Long userId) {
        log.info("Retrieving statistics for user: {}", userId);

        List<Url> urls = urlRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Long totalClicks = urlRepository.sumClickCountByUserId(userId);

        return UserStatistics.builder()
                .totalUrls((long) urls.size())
                .totalClicks(totalClicks != null ? totalClicks : 0L)
                .urls(urlMapper.toStatsList(urls))
                .build();
    }

    // optional hardened variant for my /users/{userId}/stats endpoint
    @Transactional(readOnly = true)
    public UserStatistics getOwnStats(Long pathUserId) {
        Long current = AuthUtil.currentUserIdOrNull();
        if (current == null || !current.equals(pathUserId)) {
            throw new SecurityException("You don't have permission to view these statistics");
        }
        return getUserStatistics(pathUserId);
    }

    // not used, but could be used in future. that's why I added.
    @Transactional
    public void deleteUrl(String shortCode, Long requesterUserId) {
        log.info("Deleting URL with short code: {}", shortCode);

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found: " + shortCode));

        if (url.getUserId() != null && requesterUserId != null && !url.getUserId().equals(requesterUserId)) {
            throw new SecurityException("You don't have permission to delete this URL");
        }

        urlRepository.delete(url);
        log.info("URL deleted successfully");
    }

    private String generateUniqueShortCode() {
        String shortCode;
        int attempts = 0;
        int maxAttempts = 20;

        do {
            shortCode = generateShortCode();
            attempts++;
            if (attempts >= maxAttempts) {
                throw new IllegalStateException("Unable to generate unique short code after " + maxAttempts + " attempts");
            }
        } while (urlRepository.existsByShortCode(shortCode));

        return shortCode;
    }

    private String generateShortCode() {
        StringBuilder shortCode = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            shortCode.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return shortCode.toString();
    }

    private String buildShortUrl(String shortCode) {
        return baseUrl.endsWith("/") ? baseUrl + shortCode : baseUrl + "/" + shortCode;
    }
}