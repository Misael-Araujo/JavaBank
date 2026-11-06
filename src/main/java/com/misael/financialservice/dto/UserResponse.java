package com.misael.financialservice.dto;

import java.math.BigDecimal;

import com.misael.financialservice.entities.UserType;

public record UserResponse(
        Long id,
        String completeName,
        String email,
        String document,
        BigDecimal wallet,
        UserType userType) {
}
