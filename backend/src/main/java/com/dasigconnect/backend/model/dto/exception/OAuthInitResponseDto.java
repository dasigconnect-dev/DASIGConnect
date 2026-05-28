package com.dasigconnect.backend.model.dto.exception;

public class OAuthInitResponseDto {

    private String authorizationUrl;

    public OAuthInitResponseDto(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    public String getAuthorizationUrl() { return authorizationUrl; }
}
