package com.example.travelwiki.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Persisted refresh token record, one row per issued token.
 *
 * <p>Storing tokens in the database (as hashes, never as plain-text) enables:
 * <ul>
 *   <li>Server-side revocation without waiting for token expiry.</li>
 *   <li>Refresh token rotation: when a token is used, it is replaced by a new one
 *       and the old row records {@code replacedByToken} for the audit trail.</li>
 *   <li>Replay detection: if a rotated token is presented again the server can
 *       identify the theft and revoke the whole session family.</li>
 * </ul>
 *
 * <p>Column-to-schema mapping (V1 migration):
 * <ul>
 *   <li>{@code issued_at} — the authoritative creation timestamp (matches schema; V1 name).</li>
 *   <li>{@code is_revoked} — fast boolean flag; redundant with {@code revoked_at IS NOT NULL}
 *       but kept for cheap indexed reads in high-frequency revocation checks.</li>
 * </ul>
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * SHA-256 hash of the raw refresh token value.
     * The raw token is never persisted; only the hash is stored.
     */
    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "TEXT")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /**
     * Set to {@code true} when this token is explicitly revoked (logout, rotation,
     * or security incident). Revoked tokens must never be accepted even if not yet expired.
     *
     * <p>The companion {@code revokedAt} timestamp records when this happened for
     * audit purposes.
     */
    @Column(name = "is_revoked", nullable = false)
    private boolean revoked = false;

    /**
     * Timestamp when this token was revoked.
     * {@code null} for tokens that are still active.
     */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    /**
     * Matches the schema column {@code issued_at}, not {@code created_at}.
     * Named "issued" to reflect that this is specifically a token issuance event,
     * not a generic entity creation.
     */
    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    /**
     * References the replacement token after rotation.
     * Used to build the chain of rotated tokens for replay-attack detection.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_token_id")
    private RefreshToken replacedByToken;

    @PrePersist
    void onCreate() {
        issuedAt = OffsetDateTime.now();
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public RefreshToken getReplacedByToken() {
        return replacedByToken;
    }

    public void setReplacedByToken(RefreshToken replacedByToken) {
        this.replacedByToken = replacedByToken;
    }
}
