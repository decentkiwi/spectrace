package com.spectrace.bank.loans;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.spectrace.bank.common.Money;

class LoansRequirementsTest {

    private LoanService service;

    @BeforeEach
    void setUp() {
        service = new LoanService();
    }

    // ── REQ-LN-03: Monthly instalment calculation (HALF_UP rounding) ─────────

    @Test
    @Tag("REQ-LN-03")
    void instalment_25000_at_5_5pct_36months() {
        // SGD 25,000.00 at 5.5% p.a. over 36 months -> SGD 754.90
        BigDecimal instalment = service.monthlyInstalment(
                Money.sgd("25000.00"), new BigDecimal("5.5"), 36);
        assertThat(instalment).isEqualByComparingTo("754.90");
    }

    @Test
    @Tag("REQ-LN-03")
    void instalment_60000_at_4_5pct_48months() {
        // SGD 60,000.00 at 4.5% p.a. over 48 months -> SGD 1,368.21
        BigDecimal instalment = service.monthlyInstalment(
                Money.sgd("60000.00"), new BigDecimal("4.5"), 48);
        assertThat(instalment).isEqualByComparingTo("1368.21");
    }

    // ── REQ-LN-04: Affordability check ───────────────────────────────────────

    @Test
    @Tag("REQ-LN-04")
    void insufficientIncomeIsRejectedWithAffordabilityReason() {
        // income below 3x instalment -> REJECTED with affordability reason
        // 25,000 at 5.5% / 36m: instalment ~754.90; 3x ~2264.70; income 2264.00 < threshold
        LoanApplication app = new LoanApplication("Bob", Money.sgd("25000.00"), 36,
                Money.sgd("2264.00"));
        Loan result = service.apply(app);
        assertThat(result.status()).isEqualTo(Loan.Status.REJECTED);
        assertThat(result.rejectionReason()).isNotBlank();
    }

    @Test
    @Tag("REQ-LN-04")
    void incomeOfExactly3xInstalmentIsEligible() {
        // income of exactly 3x instalment -> eligible (APPROVED)
        BigDecimal rate = service.interestRateFor(Money.sgd("10000.00"));
        BigDecimal instalment = service.monthlyInstalment(Money.sgd("10000.00"), rate, 12);
        BigDecimal threeX = instalment.multiply(new BigDecimal("3"))
                .setScale(2, RoundingMode.HALF_UP);
        LoanApplication app = new LoanApplication("Carol", Money.sgd("10000.00"), 12, threeX);
        Loan result = service.apply(app);
        assertThat(result.status()).isEqualTo(Loan.Status.APPROVED);
    }

    // ── REQ-LN-06: Early repayment fee ───────────────────────────────────────

    @Test
    @Tag("REQ-LN-06")
    void earlyRepaymentFee_12345_67() {
        // Outstanding SGD 12,345.67 -> fee SGD 185.19
        BigDecimal fee = service.earlyRepaymentFee(Money.sgd("12345.67"));
        assertThat(fee).isEqualByComparingTo("185.19");
    }

    @Test
    @Tag("REQ-LN-06")
    void earlyRepaymentFee_10000_00() {
        // Outstanding SGD 10,000.00 -> fee SGD 150.00
        BigDecimal fee = service.earlyRepaymentFee(Money.sgd("10000.00"));
        assertThat(fee).isEqualByComparingTo("150.00");
    }
}
