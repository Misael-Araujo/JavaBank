package com.misael.financialservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.misael.financialservice.entities.TransactionStatus;
import com.misael.financialservice.entities.TransactionType;

public record TransactionResponse(
        Long id,
        Long senderId,
        Long recipientId,
        BigDecimal amount,
        Instant transferredAt,
        TransactionStatus status,
        TransactionType type) {
}
