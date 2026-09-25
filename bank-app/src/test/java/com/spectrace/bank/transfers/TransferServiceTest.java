package com.spectrace.bank.transfers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.spectrace.bank.accounts.Account;
import com.spectrace.bank.accounts.AccountService;
import com.spectrace.bank.common.Money;

class TransferServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-26T02:00:00Z"), ZoneId.of("Asia/Singapore"));

    private AccountService accounts;
    private TransferService service;
    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        accounts = new AccountService();
        service = new TransferService(accounts, FIXED);
        alice = accounts.open("Alice", Money.sgd("10000"), "111111");
        bob = accounts.open("Bob", Money.sgd("500"), "222222");
    }

    @Test
    @Tag("REQ-TRF-01")
    void transferMovesFundsBetweenAccounts() {
        Transfer t = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("200"));

        assertThat(t.status()).isEqualTo(Transfer.Status.COMPLETED);
        assertThat(alice.getBalance()).isEqualByComparingTo("9800.00");
        assertThat(bob.getBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    @Tag("REQ-TRF-01")
    void failedCreditLeavesSourceUnchanged() {
        accounts.freeze(bob.getAccountNumber());

        assertThatThrownBy(() -> service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("200")))
                .isInstanceOf(RuntimeException.class);
        assertThat(alice.getBalance()).isEqualByComparingTo("10000.00");
    }

    @Test
    @Tag("REQ-TRF-02")
    void cannotTransferToSameAccount() {
        assertThatThrownBy(() -> service.transfer(alice.getAccountNumber(), alice.getAccountNumber(), Money.sgd("10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("REQ-TRF-05")
    void everyTransferHasUniqueReferenceAndTimestamp() {
        Transfer first = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("10"));
        Transfer second = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("10"));

        assertThat(first.reference()).matches("TRF-[A-Z0-9]{8}");
        assertThat(first.reference()).isNotEqualTo(second.reference());
        assertThat(first.timestamp()).isNotNull();
    }
}
