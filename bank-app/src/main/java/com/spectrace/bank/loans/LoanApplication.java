package com.spectrace.bank.loans;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LoanApplication(
        @NotBlank(message = "Applicant name is required") String applicantName,
        @NotNull(message = "Loan amount is required") @Positive BigDecimal amount,
        @Min(value = 1, message = "Tenure must be at least 1 month")
        @Max(value = 600, message = "Tenure exceeds maximum")
        int tenureMonths,
        @NotNull(message = "Monthly income is required") @Positive BigDecimal monthlyIncome) {
}
