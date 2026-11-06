package com.misael.financialservice.services;

import java.math.BigDecimal;

import com.misael.financialservice.entities.User;
import com.misael.financialservice.entities.UserType;
import com.misael.financialservice.dto.TransactionResponse;
import com.misael.financialservice.dto.TransferRequest;
import com.misael.financialservice.exceptions.ApiException;
import com.misael.financialservice.mappers.TransactionMapper;
import com.misael.financialservice.repositories.TransactionRepository;
import com.misael.financialservice.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AuthorizationService authorizationService;
    private final TransferPersistenceService persistenceService;
    private final TransactionMapper transactionMapper;

    public TransferService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            AuthorizationService authorizationService,
            TransferPersistenceService persistenceService,
            TransactionMapper transactionMapper) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.authorizationService = authorizationService;
        this.persistenceService = persistenceService;
        this.transactionMapper = transactionMapper;
    }

    public TransactionResponse create(Long senderId, TransferRequest request) {
        if (senderId.equals(request.recipientId())) {
            throw ApiException.unprocessable("The sender and recipient must be different accounts.");
        }
        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ONE) < 0 || amount.scale() > 2) {
            throw ApiException.unprocessable("The transfer amount must be at least 1.00 with at most two decimal places.");
        }
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> ApiException.notFound("Sender account was not found."));
        if (!userRepository.existsById(request.recipientId())) {
            throw ApiException.notFound("Recipient account was not found.");
        }
        if (sender.getUserType() != UserType.COMMON) {
            throw ApiException.forbidden("Merchant accounts cannot initiate transfers.");
        }
        if (sender.getWallet().compareTo(amount) < 0) {
            throw ApiException.unprocessable("The available balance is insufficient for this transfer.");
        }
        authorizationService.authorize(sender, amount);
        return persistenceService.applyTransfer(senderId, request.recipientId(), amount);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> history(Long userId, Pageable pageable) {
        Pageable boundedPage = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                pageable.getSort());
        return transactionRepository.findHistoryForUser(userId, boundedPage)
                .map(transactionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long userId, Long transactionId) {
        return transactionRepository.findAccessibleTransaction(transactionId, userId)
                .map(transactionMapper::toResponse)
                .orElseThrow(() -> ApiException.notFound("Transaction was not found."));
    }
}
