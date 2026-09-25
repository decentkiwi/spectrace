package com.spectrace.bank.loans;

import java.math.BigDecimal;

public record Loan(
        String id,
        LoanApplication application,
        Status status,
        BigDecimal annualRatePercent,
        BigDecimal monthlyInstalment,
        String rejectionReason) {

    public enum Status { APPROVED, REJECTED }

    static Loan approved(String id, LoanApplication app, BigDecimal rate, BigDecimal instalment) {
        return new Loan(id, app, Status.APPROVED, rate, instalment, null);
    }

    static Loan rejected(String id, LoanApplication app, String reason) {
        return new Loan(id, app, Status.REJECTED, null, null, reason);
    }
}
