package com.spectrace.bank.common;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;

/**
 * Append-only in-memory audit log.
 *
 * Every security-sensitive action (login, transfer, account change) is recorded
 * here with a timestamp, the actor (account number or "SYSTEM"), an event type,
 * and a detail string. The list is never modified after append — entries are
 * immutable records — making it suitable as audit evidence.
 *
 * In production this would be persisted to an append-only table or a SIEM.
 */
@Service
public class AuditService {

    public record AuditEvent(Instant timestamp, String actor, String eventType, String detail) {}

    private final List<AuditEvent> log = new CopyOnWriteArrayList<>();

    public void record(String actor, String eventType, String detail) {
        log.add(new AuditEvent(Instant.now(), actor, eventType, detail));
    }

    /** Returns an unmodifiable snapshot of the full audit log. */
    public List<AuditEvent> getAll() {
        return Collections.unmodifiableList(log);
    }

    /** Returns the most recent N events (or all if log is smaller). */
    public List<AuditEvent> getLast(int n) {
        int size = log.size();
        int from = Math.max(0, size - n);
        return Collections.unmodifiableList(log.subList(from, size));
    }
}
