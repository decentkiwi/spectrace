package com.spectrace.bank.loans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.spectrace.bank.common.Money;

class LoanServiceTest {

    private LoanService service;

    @BeforeEach
    void setUp() {
        service = new LoanService();
    }

    private static LoanApplication app(String amount, int months) {
        return new LoanApplication("Alice", Money.sgd(amount), months, Money.sgd("50000"));
    }

    @Test
    @Tag("REQ-LN-01")
    void loanAmountMustBeWithinBounds() {
        assertThat(service.apply(app("999.99", 24)).status()).isEqualTo(Loan.Status.REJECTED);
        assertThat(service.apply(app("1000", 24)).status()).isEqualTo(Loan.Status.APPROVED);
        assertThat(service.apply(app("100000", 24)).status()).isEqualTo(Loan.Status.APPROVED);
        assertThat(service.apply(app("100000.01", 24)).status()).isEqualTo(Loan.Status.REJECTED);
    }

    @Test
    @Tag("REQ-LN-02")
    void tenureMustBeBetween12And60Months() {
        assertThat(service.apply(app("10000", 11)).status()).isEqualTo(Loan.Status.REJECTED);
        assertThat(service.apply(app("10000", 12)).status()).isEqualTo(Loan.Status.APPROVED);
        assertThat(service.apply(app("10000", 60)).status()).isEqualTo(Loan.Status.APPROVED);
        assertThat(service.apply(app("10000", 61)).status()).isEqualTo(Loan.Status.REJECTED);
    }

    @Test
    @Tag("REQ-LN-05")
    void interestRateDependsOnAmountTier() {
        assertThat(service.interestRateFor(Money.sgd("19999.99"))).isEqualByComparingTo("6.5");
        assertThat(service.interestRateFor(Money.sgd("20000"))).isEqualByComparingTo("5.5");
        assertThat(service.interestRateFor(Money.sgd("50000"))).isEqualByComparingTo("5.5");
        assertThat(service.interestRateFor(Money.sgd("50000.01"))).isEqualByComparingTo("4.5");
    }

    @Test
    @Tag("REQ-LN-07")
    void everyApplicationIsApprovedOrRejectedWithReason() {
        Loan approved = service.apply(app("10000", 24));
        Loan rejected = service.apply(app("500", 24));

        assertThat(approved.status()).isEqualTo(Loan.Status.APPROVED);
        assertThat(approved.rejectionReason()).isNull();
        assertThat(rejected.status()).isEqualTo(Loan.Status.REJECTED);
        assertThat(rejected.rejectionReason()).isNotBlank();
        assertThat(service.get(approved.id())).isEqualTo(approved);
    }
}
