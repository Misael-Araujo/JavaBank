package com.misael.financialservice.services;

import java.math.BigDecimal;

public record TransferCompletedEvent(
        Long transactionId,
        String senderEmail,
        String recipientEmail,
        BigDecimal amount) {
}
