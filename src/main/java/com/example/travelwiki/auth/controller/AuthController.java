package com.example.travelwiki.auth.controller;

import com.example.travelwiki.auth.dto.AuthResponse;
import com.example.travelwiki.auth.dto.AuthTokenPairResponse;
import com.example.travelwiki.auth.dto.AuthUserResponse;
import com.example.travelwiki.auth.dto.GoogleAuthRequest;
import com.example.travelwiki.auth.dto.RefreshRequest;
import com.example.travelwiki.auth.dto.VerifiedGoogleToken;
import com.example.travelwiki.auth.entity.User;
import com.example.travelwiki.auth.service.AuthService;
import com.example.travelwiki.auth.service.AuthUserResult;
import com.example.travelwiki.auth.service.GoogleTokenVerificationService;
import com.example.travelwiki.auth.service.JwtService;
import com.example.travelwiki.auth.service.JwtToken;
import com.example.travelwiki.auth.service.RefreshTokenResult;
import com.example.travelwiki.auth.service.RefreshTokenRotationResult;
import com.example.travelwiki.auth.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final GoogleTokenVerificationService googleTokenVerificationService;
    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
        GoogleTokenVerificationService googleTokenVerificationService,
        AuthService authService,
        JwtService jwtService,
        RefreshTokenService refreshTokenService
    ) {
        this.googleTokenVerificationService = googleTokenVerificationService;
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Full sign-in endpoint. Verifies the Google idToken, resolves the local user,
     * issues a JWT access token and a refresh token, and returns the complete
     * {@link AuthResponse} to the Android client.
     */
    @PostMapping("/google/signin")
    public ResponseEntity<AuthResponse> signIn(@Valid @RequestBody GoogleAuthRequest authRequest) {
        VerifiedGoogleToken verifiedToken = googleTokenVerificationService.verify(authRequest.idToken());
        AuthUserResult userResult = authService.findOrCreateGoogleUser(verifiedToken);

        User user = userResult.user();
        JwtToken jwtToken = jwtService.generateAccessToken(user);
        RefreshTokenResult refreshTokenResult = refreshTokenService.issueRefreshToken(user);

        AuthUserResponse userResponse = new AuthUserResponse(
            user.getId(),
            user.getEmail(),
            user.isEmailVerified(),
            user.getDisplayName(),
            user.getPictureUrl(),
            user.getStatus()
        );

        AuthTokenPairResponse tokenPairResponse = new AuthTokenPairResponse(
            "Bearer",
            jwtToken.tokenString(),
            jwtToken.expiresAt(),
            refreshTokenResult.rawToken(),
            refreshTokenResult.expiresAt()
        );

        return ResponseEntity.ok(new AuthResponse(userResponse, tokenPairResponse, userResult.newUser()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest refreshRequest) {
        RefreshTokenRotationResult rotationResult = refreshTokenService.rotate(refreshRequest.refreshToken());

        User user = rotationResult.user();
        JwtToken jwtToken = jwtService.generateAccessToken(user);
        RefreshTokenResult newRefreshToken = rotationResult.newRefreshToken();

        AuthUserResponse userResponse = new AuthUserResponse(
            user.getId(),
            user.getEmail(),
            user.isEmailVerified(),
            user.getDisplayName(),
            user.getPictureUrl(),
            user.getStatus()
        );

        AuthTokenPairResponse tokenPairResponse = new AuthTokenPairResponse(
            "Bearer",
            jwtToken.tokenString(),
            jwtToken.expiresAt(),
            newRefreshToken.rawToken(),
            newRefreshToken.expiresAt()
        );

        return ResponseEntity.ok(new AuthResponse(userResponse, tokenPairResponse, false));
    }
}
