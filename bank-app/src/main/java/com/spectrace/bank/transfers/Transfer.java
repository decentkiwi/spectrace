package com.spectrace.bank.transfers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Transfer(
        String reference,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        LocalDateTime timestamp,
        Status status) {

    public enum Status { COMPLETED, PENDING_REVIEW }
}
