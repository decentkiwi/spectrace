package com.spectrace.bank.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import com.spectrace.bank.common.AuditService;
import com.spectrace.bank.common.BankException;
import com.spectrace.bank.common.Money;

class AccountsRequirementsTest {

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(NoOpPasswordEncoder.getInstance(), new AuditService());
    }

    // ── REQ-ACC-01 ────────────────────────────────────────────────────────────

    @Test
    @Tag("REQ-ACC-01")
    void zeroInitialDepositIsAllowed() {
        Account account = service.open("Maria Santos", BigDecimal.ZERO, "000000");

        assertThat(account.getStatus()).isEqualTo(Account.Status.ACTIVE);
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    // ── REQ-ACC-05 ────────────────────────────────────────────────────────────

    @Test
    @Tag("REQ-ACC-05")
    void closingAccountWithNonZeroBalanceIsRejected() {
        Account account = service.open("Alice Tan", Money.sgd("100"), "123456");

        assertThatThrownBy(() -> service.close(account.getAccountNumber()))
                .isInstanceOf(BankException.class);
        assertThat(account.getStatus()).isEqualTo(Account.Status.ACTIVE);
    }

    @Test
    @Tag("REQ-ACC-05")
    void closingAccountWithZeroBalanceSetsStatusToClosed() {
        Account account = service.open("Bob Lim", BigDecimal.ZERO, "654321");

        service.close(account.getAccountNumber());

        assertThat(account.getStatus()).isEqualTo(Account.Status.CLOSED);
    }

    // ── REQ-ACC-06 ────────────────────────────────────────────────────────────

    @Test
    @Tag("REQ-ACC-06")
    void depositToFrozenAccountIsRejected() {
        Account account = service.open("Charlie Ng", Money.sgd("200"), "111111");
        service.freeze(account.getAccountNumber());

        assertThatThrownBy(() -> service.deposit(account.getAccountNumber(), Money.sgd("50")))
                .isInstanceOf(BankException.class);
    }

    @Test
    @Tag("REQ-ACC-06")
    void withdrawalFromFrozenAccountIsRejected() {
        Account account = service.open("Diana Koh", Money.sgd("200"), "222222");
        service.freeze(account.getAccountNumber());

        assertThatThrownBy(() -> service.withdraw(account.getAccountNumber(), Money.sgd("50")))
                .isInstanceOf(BankException.class);
    }

    @Test
    @Tag("REQ-ACC-06")
    void frozenAccountBalanceUnchangedAfterRejectedOperations() {
        Account account = service.open("Eve Chen", Money.sgd("200"), "333333");
        service.freeze(account.getAccountNumber());

        try { service.deposit(account.getAccountNumber(), Money.sgd("50")); } catch (Exception ignored) {}
        try { service.withdraw(account.getAccountNumber(), Money.sgd("50")); } catch (Exception ignored) {}

        assertThat(account.getBalance()).isEqualByComparingTo("200.00");
    }
}
