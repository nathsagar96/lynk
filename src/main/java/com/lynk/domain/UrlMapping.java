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
     * The token subject that registered this link, and the only one whose stats call will match it.
     * <p>
     * Null only for rows that predate ownership, which
     * {@code V2__add_owner_to_url_mapping.sql} left unowned rather than dropping. Every row written
     * now carries one, because the service refuses to register a link it cannot attribute.
     */
    @Column(name = "owner", length = 255)
    private String owner;

    /**
     * Null means the link never expires.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    /**
     * Creates a not-yet-persisted mapping owned by {@code owner}. {@code createdAt} is
     * stamped on persist so callers never have to supply it.
     */
    public UrlMapping(String originalUrl, String shortCode, String owner, Instant expiresAt) {
        this.originalUrl = originalUrl;
        this.shortCode = shortCode;
        this.owner = owner;
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
