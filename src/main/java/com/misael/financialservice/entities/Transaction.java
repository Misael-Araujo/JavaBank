package com.misael.financialservice.entities;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_tb", indexes = {
        @Index(name = "idx_transaction_payer_date", columnList = "payer_id, transfer_date"),
        @Index(name = "idx_transaction_payee_date", columnList = "payee_id, transfer_date")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payee_id", nullable = false)
    private User payee;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "transfer_date", nullable = false)
    private Instant transferDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    protected Transaction() {
    }

    public Transaction(User payer, User payee, BigDecimal amount, Instant transferDate) {
        this.payer = payer;
        this.payee = payee;
        this.amount = amount;
        this.transferDate = transferDate;
        this.status = TransactionStatus.COMPLETED;
        this.transactionType = TransactionType.TRANSFER;
    }

    public Transaction(User payer, User payee, BigDecimal amount, Instant transferDate, TransactionType type) {
        this.payer = payer;
        this.payee = payee;
        this.amount = amount;
        this.transferDate = transferDate;
        this.status = TransactionStatus.COMPLETED;
        this.transactionType = type;
    }

    public Long getId() {
        return id;
    }

    public User getPayer() {
        return payer;
    }

    public User getPayee() {
        return payee;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getTransferDate() {
        return transferDate;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }
}
