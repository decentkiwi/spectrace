package com.spectrace.bank.loans;

import java.math.BigDecimal;

public record LoanApplication(
        String applicantName,
        BigDecimal amount,
        int tenureMonths,
        BigDecimal monthlyIncome) {
}
