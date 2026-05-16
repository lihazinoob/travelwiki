package com.example.travelwiki.auth.dto;

import com.example.travelwiki.auth.entity.UserStatus;

public record AuthUserResponse(
    Long id,
    String email,
    boolean emailVerified,
    String displayName,
    String pictureUrl,
    UserStatus status
) {
}
