package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.config.JwtProperties;
import com.example.travelwiki.auth.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtServiceImpl implements JwtService {

    private final SecretKey signingKey;
    private final int accessTokenTtlMinutes;

    public JwtServiceImpl(JwtProperties jwtProperties) {
        byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtlMinutes = jwtProperties.accessTokenTtlMinutes();
    }

    @Override
    public Long extractUserId(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    @Override
    public JwtToken generateAccessToken(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusMinutes(accessTokenTtlMinutes);

        String tokenString = Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim("email", user.getEmail())
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(expiresAt.toInstant()))
            .signWith(signingKey)
            .compact();

        return new JwtToken(tokenString, expiresAt);
    }
}
