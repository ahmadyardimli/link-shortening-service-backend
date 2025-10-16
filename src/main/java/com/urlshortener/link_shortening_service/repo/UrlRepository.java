package com.urlshortener.link_shortening_service.repo;

import com.urlshortener.link_shortening_service.entity.Url;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    boolean existsByCustomAlias(String customAlias);

    @Query("SELECT u FROM Url u WHERE u.userId = :userId ORDER BY u.createdAt DESC")
    List<Url> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT SUM(u.clickCount) FROM Url u WHERE u.userId = :userId")
    Long sumClickCountByUserId(@Param("userId") Long userId);

    // Bitly-like reuse. It finds a non-expired URL for this user and long URL.
    // works for both logged-in which condition is userId != null and unAuth user (userId == null).
    @Query("""
           SELECT u FROM Url u
           WHERE ((:userId IS NULL AND u.userId IS NULL) OR u.userId = :userId)
             AND u.originalUrl = :originalUrl
             AND (u.expiresAt IS NULL OR u.expiresAt > CURRENT_TIMESTAMP)
           """)
    Optional<Url> findReusableLink(@Param("userId") Long userId, @Param("originalUrl") String originalUrl);
}