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

    @Test
    @Tag("REQ-ACC-05")
    void closingAccountWithNonZeroBalanceIsRejected() {
        Account account = service.open("Alice", Money.sgd("0.01"), "123456");

        assertThatThrownBy(() -> service.close(account.getAccountNumber())).isInstanceOf(BankException.class);
        assertThat(account.getStatus()).isEqualTo(Account.Status.ACTIVE);
    }

    @Test
    @Tag("REQ-ACC-05")
    void closingAccountWithZeroBalanceMarksItClosed() {
        Account account = service.open("Alice", BigDecimal.ZERO, "123456");

        service.close(account.getAccountNumber());

        assertThat(account.getStatus()).isEqualTo(Account.Status.CLOSED);
    }

    @Test
    @Tag("REQ-ACC-06")
    void frozenAccountRejectsDepositsAndWithdrawals() {
        Account account = service.open("Alice", Money.sgd("100"), "123456");
        service.freeze(account.getAccountNumber());

        assertThatThrownBy(() -> service.deposit(account.getAccountNumber(), Money.sgd("10")))
                .isInstanceOf(BankException.class);
        assertThatThrownBy(() -> service.withdraw(account.getAccountNumber(), Money.sgd("10")))
                .isInstanceOf(BankException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }
}
