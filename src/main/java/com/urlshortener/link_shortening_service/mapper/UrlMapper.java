package com.urlshortener.link_shortening_service.mapper;

import com.urlshortener.link_shortening_service.dto.ShortenUrl;
import com.urlshortener.link_shortening_service.dto.UrlStatistics;
import com.urlshortener.link_shortening_service.entity.Url;
import com.urlshortener.link_shortening_service.request.ShortenUrlRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Service
public class UrlMapper implements Function<Url, ShortenUrl> {

    @Override
    public ShortenUrl apply(Url url) {
        return ShortenUrl.builder()
                .shortCode(url.getShortCode())
                .shortUrl(null)
                .build();
    }

    public Url toEntity(ShortenUrlRequest shortenUrlRequest, String shortCode, Long userId) {
        return Url.builder()
                .originalUrl(shortenUrlRequest.getUrl())
                .shortCode(shortCode)
                .customAlias(shortenUrlRequest.getCustomAlias())
                .expiresAt(calcExpiresAt(shortenUrlRequest.getExpirationDays()))
                .userId(userId)
                .clickCount(0L)
                .build();
    }

    public UrlStatistics toStats(Url url) {
        return UrlStatistics.builder()
                .shortCode(url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .clickCount(url.getClickCount())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .customAlias(url.getCustomAlias())
                .build();
    }

    public List<UrlStatistics> toStatsList(List<Url> urls) {
        return urls.stream().map(this::toStats).toList();
    }

    private static LocalDateTime calcExpiresAt(Integer days) {
        if (days == null || days <= 0) return null;
        return LocalDateTime.now().plusDays(days);
    }
}