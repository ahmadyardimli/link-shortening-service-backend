package com.urlshortener.link_shortening_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrl {
    private String shortCode;
    private String shortUrl;
}
