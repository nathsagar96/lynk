package com.lynk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single shortened link.
 * <p>
 * The schema is owned by Flyway ({@code V1__create_url_mapping.sql}); this entity only has to
 * agree with it, which Hibernate checks at startup via {@code ddl-auto=validate}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The destination the short code redirects to.
     */
    @Column(name = "original_url", nullable = false, columnDefinition = "text")
    private String originalUrl;

    @Column(name = "short_code", nullable = false, unique = true, length = 32)
    private String shortCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Null means the link never expires.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    /**
     * Creates a not-yet-persisted mapping. {@code createdAt} is stamped on persist so callers
     * never have to supply it.
     */
    public UrlMapping(String originalUrl, String shortCode, Instant expiresAt) {
        this.originalUrl = originalUrl;
        this.shortCode = shortCode;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Whether this link has passed its expiry moment. Links without an expiry never are.
     */
    public boolean isExpired(Instant now) {
        return this.expiresAt != null && !this.expiresAt.isAfter(now);
    }
}
