package com.misael.financialservice.mappers;

import com.misael.financialservice.entities.Transaction;
import com.misael.financialservice.dto.TransactionResponse;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getPayer().getId(),
                transaction.getPayee().getId(),
                transaction.getAmount(),
                transaction.getTransferDate(),
                transaction.getStatus(),
                transaction.getTransactionType());
    }
}
