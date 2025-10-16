package com.urlshortener.link_shortening_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatistics {
    private Long totalUrls;
    private Long totalClicks;
    private List<UrlStatistics> urls;
}