package com.misael.financialservice;

import java.math.BigDecimal;
import java.util.Optional;

import com.misael.financialservice.dto.TransactionResponse;
import com.misael.financialservice.dto.TransferRequest;
import com.misael.financialservice.entities.TransactionStatus;
import com.misael.financialservice.entities.TransactionType;
import com.misael.financialservice.entities.User;
import com.misael.financialservice.entities.UserType;
import com.misael.financialservice.exceptions.ApiException;
import com.misael.financialservice.mappers.TransactionMapper;
import com.misael.financialservice.repositories.TransactionRepository;
import com.misael.financialservice.repositories.UserRepository;
import com.misael.financialservice.services.AuthorizationService;
import com.misael.financialservice.services.TransferPersistenceService;
import com.misael.financialservice.services.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private TransferPersistenceService persistenceService;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransferService transferService;

    private User sender;

    @BeforeEach
    void setUp() {
        sender = new User("Sender", "sender@example.com", "52998224725", "hash", UserType.COMMON);
        sender.credit(new BigDecimal("100.00"));
    }

    @Test
    void authorizesAndDelegatesValidTransfers() {
        TransferRequest request = new TransferRequest(2L, new BigDecimal("25.00"));
        TransactionResponse expected = new TransactionResponse(
                7L, 1L, 2L, request.amount(), java.time.Instant.now(),
                TransactionStatus.COMPLETED, TransactionType.TRANSFER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(persistenceService.applyTransfer(1L, 2L, request.amount())).thenReturn(expected);

        assertThat(transferService.create(1L, request)).isEqualTo(expected);

        verify(authorizationService).authorize(sender, request.amount());
        verify(persistenceService).applyTransfer(1L, 2L, request.amount());
    }

    @Test
    void rejectsSelfTransfersBeforeLoadingAccounts() {
        assertThatThrownBy(() -> transferService.create(
                1L, new TransferRequest(1L, new BigDecimal("25.00"))))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getStatus().value())
                .isEqualTo(422);
        verifyNoInteractions(userRepository, authorizationService, persistenceService);
    }

    @Test
    void rejectsMissingRecipientAndInsufficientBalance() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.existsById(2L)).thenReturn(false);
        assertThatThrownBy(() -> transferService.create(
                1L, new TransferRequest(2L, new BigDecimal("25.00"))))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getStatus().value())
                .isEqualTo(404);

        when(userRepository.existsById(2L)).thenReturn(true);
        assertThatThrownBy(() -> transferService.create(
                1L, new TransferRequest(2L, new BigDecimal("101.00"))))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getStatus().value())
                .isEqualTo(422);
        verifyNoInteractions(authorizationService, persistenceService);
    }

    @Test
    void rejectsMerchantSendersBeforeExternalAuthorization() {
        User merchant = new User("Merchant", "merchant@example.com", "11222333000181", "hash", UserType.MERCHANT);
        merchant.credit(new BigDecimal("100.00"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(userRepository.existsById(2L)).thenReturn(true);

        assertThatThrownBy(() -> transferService.create(
                1L, new TransferRequest(2L, new BigDecimal("25.00"))))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getStatus().value())
                .isEqualTo(403);
        verifyNoInteractions(authorizationService, persistenceService);
    }
}
