package com.spectrace.bank.loans;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

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

    @Test
    @Tag("REQ-LN-03")
    void instalmentFor25000At5_5PercentOver36MonthsIs754_90() {
        assertThat(service.monthlyInstalment(Money.sgd("25000"), new BigDecimal("5.5"), 36))
                .isEqualByComparingTo("754.90");
    }

    @Test
    @Tag("REQ-LN-03")
    void instalmentFor60000At4_5PercentOver48MonthsIs1368_21() {
        assertThat(service.monthlyInstalment(Money.sgd("60000"), new BigDecimal("4.5"), 48))
                .isEqualByComparingTo("1368.21");
    }

    @Test
    @Tag("REQ-LN-04")
    void applicationIsRejectedWhenIncomeBelowThreeTimesInstalment() {
        // 10,000 at 6.5% over 12 months -> instalment 862.96; 3x = 2,588.88
        Loan loan = service.apply(new LoanApplication("Alice", Money.sgd("10000"), 12, Money.sgd("2588.87")));

        assertThat(loan.status()).isEqualTo(Loan.Status.REJECTED);
        assertThat(loan.rejectionReason()).containsIgnoringCase("income");
    }

    @Test
    @Tag("REQ-LN-04")
    void applicationIsApprovedWhenIncomeAtLeastThreeTimesInstalment() {
        Loan loan = service.apply(new LoanApplication("Alice", Money.sgd("10000"), 12, Money.sgd("2588.88")));

        assertThat(loan.status()).isEqualTo(Loan.Status.APPROVED);
    }

    @Test
    @Tag("REQ-LN-06")
    void earlyRepaymentFeeIsOnePointFivePercentRoundedHalfUp() {
        assertThat(service.earlyRepaymentFee(Money.sgd("12345.67"))).isEqualByComparingTo("185.19");
        assertThat(service.earlyRepaymentFee(Money.sgd("10000"))).isEqualByComparingTo("150.00");
    }
}
