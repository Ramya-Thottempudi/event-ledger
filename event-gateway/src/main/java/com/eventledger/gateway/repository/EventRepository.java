package com.eventledger.gateway.repository;

import com.eventledger.gateway.entity.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventRepository extends JpaRepository<EventRecord, String> {
    List<EventRecord> findByAccountIdOrderByEventTimestampAsc(String accountId);
    List<EventRecord> findByAccountId(String accountId);
    boolean existsByEventId(String eventId);
}
