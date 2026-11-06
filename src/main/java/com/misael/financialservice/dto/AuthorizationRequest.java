package com.misael.financialservice.dto;

import java.math.BigDecimal;

public record AuthorizationRequest(Long payer, BigDecimal value) {
}
