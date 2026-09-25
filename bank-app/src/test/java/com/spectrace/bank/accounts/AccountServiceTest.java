package com.spectrace.bank.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import com.spectrace.bank.common.AuditService;
import com.spectrace.bank.common.Money;

class AccountServiceTest {

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(NoOpPasswordEncoder.getInstance(), new AuditService());
    }

    @Test
    @Tag("REQ-ACC-01")
    void opensAccountWithOwnerAndInitialDeposit() {
        Account account = service.open("Tan Wei Ming", Money.sgd("250"), "123456");

        assertThat(account.getOwnerName()).isEqualTo("Tan Wei Ming");
        assertThat(account.getBalance()).isEqualByComparingTo("250.00");
        assertThat(account.getStatus()).isEqualTo(Account.Status.ACTIVE);
    }

    @Test
    @Tag("REQ-ACC-01")
    void rejectsBlankOwnerOrNegativeDeposit() {
        assertThatThrownBy(() -> service.open("  ", Money.sgd("10"), "123456"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.open("Alice", Money.sgd("-1"), "123456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("REQ-ACC-02")
    void accountNumbersFollowFormatAndAreUnique() {
        Account a = service.open("Alice", BigDecimal.ZERO, "111111");
        Account b = service.open("Bob", BigDecimal.ZERO, "222222");

        assertThat(a.getAccountNumber()).matches("SG\\d{8}");
        assertThat(b.getAccountNumber()).matches("SG\\d{8}");
        assertThat(a.getAccountNumber()).isNotEqualTo(b.getAccountNumber());
    }

    @Test
    @Tag("REQ-ACC-03")
    void depositIncreasesBalanceAndMustBePositive() {
        Account account = service.open("Alice", Money.sgd("100"), "123456");

        service.deposit(account.getAccountNumber(), Money.sgd("50.25"));

        assertThat(account.getBalance()).isEqualByComparingTo("150.25");
        assertThatThrownBy(() -> service.deposit(account.getAccountNumber(), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("REQ-ACC-04")
    void withdrawalBeyondBalanceIsRejected() {
        Account account = service.open("Alice", Money.sgd("100"), "123456");

        assertThatThrownBy(() -> service.withdraw(account.getAccountNumber(), Money.sgd("100.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @Tag("REQ-ACC-08")
    void balanceEnquiryIsRoundedToTwoDecimals() {
        Account account = service.open("Alice", new BigDecimal("10.005"), "123456");

        assertThat(service.getBalance(account.getAccountNumber())).isEqualTo(new BigDecimal("10.01"));
    }
}
