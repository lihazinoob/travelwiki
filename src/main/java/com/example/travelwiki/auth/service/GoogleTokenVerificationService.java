package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.dto.VerifiedGoogleToken;

public interface GoogleTokenVerificationService {

    VerifiedGoogleToken verify(String idToken);
}
