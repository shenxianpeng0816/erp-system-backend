package com.erp.service;

import com.erp.mapper.DocSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates unique, restart-safe document numbers backed by the doc_sequence table.
 * Each call runs in its own transaction with FOR UPDATE lock to prevent duplicates.
 *
 * Examples:
 *   nextDocNo("SO")  → "SO202504250001"
 *   nextDocNo("INV") → "INV202504250001"
 *   nextDocNo("DN")  → "DN202504250001"
 *   nextDocNo("IN")  → "IN202504250001"
 */
@Service
@RequiredArgsConstructor
public class DocSequenceService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DocSequenceMapper mapper;

    /**
     * Returns the next formatted document number for the given prefix.
     * Thread-safe and restart-safe.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextDocNo(String prefix) {
        String today = LocalDate.now().format(DATE_FMT);
        String storedDate = mapper.getDatePart(prefix);

        if (!today.equals(storedDate)) {
            // New day — reset counter to 1
            mapper.resetForNewDay(prefix, today);
        }

        Long val = mapper.getCurrentVal(prefix);
        mapper.increment(prefix);

        return prefix + today + String.format("%04d", val);
    }
}
