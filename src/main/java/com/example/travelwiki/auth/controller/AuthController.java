package com.example.travelwiki.auth.controller;

import com.example.travelwiki.auth.dto.GoogleAuthRequest;
import com.example.travelwiki.auth.dto.VerifiedGoogleToken;
import com.example.travelwiki.auth.service.GoogleTokenVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth/google")
public class AuthController {

    private final GoogleTokenVerificationService googleTokenVerificationService;

    public AuthController(GoogleTokenVerificationService googleTokenVerificationService) {
        this.googleTokenVerificationService = googleTokenVerificationService;
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifiedGoogleToken> verifyToken(@Valid @RequestBody GoogleAuthRequest authRequest) {
        VerifiedGoogleToken verifiedToken = googleTokenVerificationService.verify(authRequest.idToken());
        return ResponseEntity.ok(verifiedToken);
    }
}
