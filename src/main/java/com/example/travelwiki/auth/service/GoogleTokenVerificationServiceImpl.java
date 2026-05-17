package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.config.GoogleAuthProperties;
import com.example.travelwiki.auth.dto.VerifiedGoogleToken;
import com.example.travelwiki.auth.exception.InvalidGoogleTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GoogleTokenVerificationServiceImpl implements GoogleTokenVerificationService {

    private static final List<String> GOOGLE_ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerificationServiceImpl(GoogleAuthProperties googleAuthProperties) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(googleAuthProperties.allowedAudiences())
            .setIssuers(GOOGLE_ISSUERS)
            .build();
    }

    @Override
    public VerifiedGoogleToken verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new InvalidGoogleTokenException("Google ID token is required");
        }

        GoogleIdToken googleIdToken = parseAndVerify(idToken);
        GoogleIdToken.Payload payload = googleIdToken.getPayload();

        String subject = payload.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new InvalidGoogleTokenException("Google token subject is missing");
        }

        String email = payload.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new InvalidGoogleTokenException("Google token email is missing");
        }

        boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
        if (!emailVerified) {
            throw new InvalidGoogleTokenException("Google account email is not verified");
        }

        return new VerifiedGoogleToken(
            subject,
            email,
            true,
            stringClaim(payload.get("name")),
            stringClaim(payload.get("picture"))
        );
    }

    private GoogleIdToken parseAndVerify(String idToken) {
        try {
            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                throw new InvalidGoogleTokenException("Google ID token is invalid");
            }
            return googleIdToken;
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidGoogleTokenException("Google ID token is malformed", exception);
        }
        catch (IOException | GeneralSecurityException exception) {
            throw new InvalidGoogleTokenException("Failed to verify Google ID token", exception);
        }
    }

    private String stringClaim(Object claimValue) {
        if (claimValue == null) {
            return null;
        }

        String value = Objects.toString(claimValue, null);
        return StringUtils.hasText(value) ? value : null;
    }
}
