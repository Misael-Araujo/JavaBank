package com.misael.financialservice.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
