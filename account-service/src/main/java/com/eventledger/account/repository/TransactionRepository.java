package com.eventledger.account.repository;

import com.eventledger.account.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByAccountIdOrderByEventTimestampAsc(String accountId);
    List<TransactionEntity> findByAccountId(String accountId);
    boolean existsByEventId(String eventId);
}
