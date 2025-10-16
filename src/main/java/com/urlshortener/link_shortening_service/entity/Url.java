package com.urlshortener.link_shortening_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "urls", indexes = {
        @Index(name = "index_short_code", columnList = "shortCode", unique = true),
        @Index(name = "index_user_id", columnList = "userId"),
        @Index(name = "index_user_original", columnList = "userId,originalUrl") // speeds up reuse lookup
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false, unique = true, length = 10)
    private String shortCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Long clickCount;

    @Column(length = 50)
    private String customAlias;

    @Column
    private LocalDateTime expiresAt;

    @Column
    private Long userId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (clickCount == null) {
            clickCount = 0L;
        }
    }

    public void incrementClickCount() {
        this.clickCount++;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}