package ly.lynk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA entity representing a shortened URL mapping and its click statistics. */
@Entity
@Table(name = "url_mappings")
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "alias", nullable = false, unique = true, length = 32)
    private String alias;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Column(name = "owner_id", length = 255)
    private String ownerId;

    protected UrlMapping() {}

    /**
     * Creates a new URL mapping entity.
     *
     * @param originalUrl the original target URL
     * @param alias the unique alias
     * @param createdAt the creation instant
     * @param expiresAt the optional expiration instant, or {@code null} if none
     * @param ownerId the optional owner subject identifier, or {@code null} if unowned
     */
    public UrlMapping(String originalUrl, String alias, Instant createdAt, Instant expiresAt, String ownerId) {
        this.originalUrl = originalUrl;
        this.alias = alias;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.ownerId = ownerId;
        this.clickCount = 0L;
    }

    /**
     * Returns the database ID.
     *
     * @return the primary key ID
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the original target URL.
     *
     * @return the target URL
     */
    public String getOriginalUrl() {
        return originalUrl;
    }

    /**
     * Returns the unique alias.
     *
     * @return the alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return the creation instant
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the optional expiration timestamp, or {@code null} if it never expires.
     *
     * @return the expiration instant, or {@code null}
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returns the total click count.
     *
     * @return the click count
     */
    public long getClickCount() {
        return clickCount;
    }

    /**
     * Returns the owner subject identifier, or {@code null} if unowned.
     *
     * @return the owner identifier, or {@code null}
     */
    public String getOwnerId() {
        return ownerId;
    }
}
