package com.misael.financialservice.repositories;

import java.util.Optional;

import com.misael.financialservice.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
            select entry from Transaction entry
            where entry.payer.id = :userId or entry.payee.id = :userId
            order by entry.transferDate desc, entry.id desc
            """)
    Page<Transaction> findHistoryForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select entry from Transaction entry
            where entry.id = :transactionId
              and (entry.payer.id = :userId or entry.payee.id = :userId)
            """)
    Optional<Transaction> findAccessibleTransaction(
            @Param("transactionId") Long transactionId,
            @Param("userId") Long userId);
}