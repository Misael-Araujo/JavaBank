package com.misael.financialservice.services;

import java.math.BigDecimal;
import java.time.Instant;

import com.misael.financialservice.entities.Transaction;
import com.misael.financialservice.entities.TransactionType;
import com.misael.financialservice.entities.User;
import com.misael.financialservice.entities.UserRole;
import com.misael.financialservice.dto.TransactionResponse;
import com.misael.financialservice.exceptions.ApiException;
import com.misael.financialservice.mappers.TransactionMapper;
import com.misael.financialservice.repositories.TransactionRepository;
import com.misael.financialservice.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public AdminService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            TransactionMapper transactionMapper) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    @Transactional
    public TransactionResponse creditAccount(Long administratorId, Long accountId, BigDecimal amount) {
        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw ApiException.unprocessable("Credit amount must be positive and have at most two decimal places.");
        }
        User administrator = userRepository.findById(administratorId)
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> ApiException.forbidden("Administrator access is required."));
        User account = userRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> ApiException.notFound("Account was not found."));
        account.credit(amount);
        Transaction credit = transactionRepository.save(new Transaction(
                administrator, account, amount, Instant.now(), TransactionType.ADMIN_CREDIT));
        return transactionMapper.toResponse(credit);
    }
}
