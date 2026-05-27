package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.config.JwtProperties;
import com.example.travelwiki.auth.entity.RefreshToken;
import com.example.travelwiki.auth.entity.User;
import com.example.travelwiki.auth.repository.RefreshTokenRepository;
import com.example.travelwiki.auth.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final int refreshTokenTtlDays;

    public RefreshTokenServiceImpl(
        RefreshTokenRepository refreshTokenRepository,
        UserRepository userRepository,
        JwtProperties jwtProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenTtlDays = jwtProperties.refreshTokenTtlDays();
    }

    @Override
    @Transactional
    public RefreshTokenResult issueRefreshToken(User user) {
        String rawToken = generateSecureToken();
        String tokenHash = sha256Hex(rawToken);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(refreshTokenTtlDays);

        // Re-attach the user entity to the current transaction's persistence context
        // so Hibernate can resolve the FK without hitting a detached-entity error.
        User managedUser = userRepository.getReferenceById(user.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(managedUser);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(expiresAt);
        // issuedAt is set by @PrePersist; isRevoked defaults to false

        refreshTokenRepository.save(refreshToken);

        LOGGER.info("Refresh token issued. userId={}", user.getId());
        return new RefreshTokenResult(rawToken, expiresAt);
    }

    // 32 cryptographically random bytes encoded as URL-safe base64 (no padding).
    // This yields a 43-character opaque token with ~256 bits of entropy.
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 is always available on any Java SE runtime — the catch is unreachable in practice.
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
