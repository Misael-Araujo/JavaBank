package com.misael.financialservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotNull @Positive Long recipientId,
        @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
