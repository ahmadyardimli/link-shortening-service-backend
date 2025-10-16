package com.urlshortener.link_shortening_service.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlRequest {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL is too long")
    private String url;

    @Size(max = 10, message = "Custom alias is too long")
    @Pattern(
            regexp = "^[a-zA-Z0-9-_]*$",
            message = "Custom alias can only contain letters, numbers, hyphens, and underscores"
    )
    private String customAlias;

    private Integer expirationDays;

    // bitly like. when null or true, then in this situation try to reuse existing short link
    // for the same user id and url if it exisits and is not expired.
    // also we can set it to false to force a new short code
    private Boolean reuseExisting;
}
