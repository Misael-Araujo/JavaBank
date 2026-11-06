package com.misael.financialservice.services;

import java.math.BigDecimal;
import java.time.Instant;

import com.misael.financialservice.entities.Transaction;
import com.misael.financialservice.entities.User;
import com.misael.financialservice.entities.UserType;
import com.misael.financialservice.dto.TransactionResponse;
import com.misael.financialservice.exceptions.ApiException;
import com.misael.financialservice.mappers.TransactionMapper;
import com.misael.financialservice.repositories.TransactionRepository;
import com.misael.financialservice.repositories.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferPersistenceService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final ApplicationEventPublisher eventPublisher;

    public TransferPersistenceService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            TransactionMapper transactionMapper,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TransactionResponse applyTransfer(Long senderId, Long recipientId, BigDecimal amount) {
        Long firstId = Math.min(senderId, recipientId);
        Long secondId = Math.max(senderId, recipientId);
        User first = userRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> ApiException.notFound("User was not found."));
        User second = userRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> ApiException.notFound("User was not found."));
        User sender = senderId.equals(firstId) ? first : second;
        User recipient = recipientId.equals(firstId) ? first : second;

        if (sender.getUserType() != UserType.COMMON) {
            throw ApiException.forbidden("Merchant accounts cannot initiate transfers.");
        }
        if (amount.compareTo(BigDecimal.ONE) < 0 || sender.getWallet().compareTo(amount) < 0) {
            throw ApiException.unprocessable("The transfer amount must be at least 1.00 and within the available balance.");
        }

        sender.debit(amount);
        recipient.credit(amount);
        Transaction transaction = transactionRepository.save(
                new Transaction(sender, recipient, amount, Instant.now()));
        eventPublisher.publishEvent(new TransferCompletedEvent(
                transaction.getId(), sender.getEmail(), recipient.getEmail(), amount));
        return transactionMapper.toResponse(transaction);
    }
}
