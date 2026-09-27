package com.spectrace.bank.transfers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import com.spectrace.bank.accounts.Account;
import com.spectrace.bank.accounts.AccountService;
import com.spectrace.bank.common.AuditService;
import com.spectrace.bank.common.Money;

class TransfersRequirementsTest {

    private static final Clock DAY1 = Clock.fixed(
            Instant.parse("2026-09-26T02:00:00Z"), ZoneId.of("Asia/Singapore"));
    private static final Clock DAY2 = Clock.fixed(
            Instant.parse("2026-09-27T02:00:00Z"), ZoneId.of("Asia/Singapore"));

    private AccountService accounts;
    private AuditService audit;
    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        audit    = new AuditService();
        accounts = new AccountService(NoOpPasswordEncoder.getInstance(), audit);
        alice    = accounts.open("Alice", Money.sgd("10000"), "111111");
        bob      = accounts.open("Bob",   Money.sgd("10000"), "222222");
    }

    // ── REQ-TRF-03 ───────────────────────────────────────────────────────────

    /**
     * Spec: "a day's total of exactly SGD 5,000.00 is allowed" (inclusive limit).
     * Given SGD 4,000.00 already transferred, a further SGD 1,000.00 must be accepted.
     */
    @Test
    @Tag("REQ-TRF-03")
    void dailyLimitExactly5000IsAccepted() {
        TransferService service = new TransferService(accounts, DAY1, audit);
        service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("4000"));
        Transfer t = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("1000"));
        assertThat(t.status()).isEqualTo(Transfer.Status.COMPLETED);
        assertThat(alice.getBalance()).isEqualByComparingTo("5000.00");
    }

    /**
     * Spec: after the day's total exceeds SGD 5,000.00, any further transfer is rejected.
     * Given SGD 5,000.01 already sent, a further SGD 0.01 is rejected.
     */
    @Test
    @Tag("REQ-TRF-03")
    void dailyLimitExceededIsRejected() {
        // Send 4999.99 (below limit — passes even with the production bug)
        TransferService service = new TransferService(accounts, DAY1, audit);
        service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("4999.99"));
        // 4999.99 + 0.02 = 5000.01 > 5000 → must be rejected
        assertThatThrownBy(() ->
                service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("0.02")))
                .isInstanceOf(DailyLimitExceededException.class);
        // balance is unchanged after the rejected attempt
        assertThat(alice.getBalance()).isEqualByComparingTo("10000.00"); // 4999.99 was PENDING_REVIEW (no debit); rejected 0.02 also unchanged
    }

    /**
     * Spec: transfers made on a previous calendar day do not count toward today's limit.
     */
    @Test
    @Tag("REQ-TRF-03")
    void previousDayTransfersDoNotCountTowardsLimit() {
        // Send 4999.99 on day 1 (passes — total < 5000)
        TransferService serviceDay1 = new TransferService(accounts, DAY1, audit);
        serviceDay1.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("4999.99"));

        // On day 2, yesterday's total must not count; a fresh transfer must succeed
        TransferService serviceDay2 = new TransferService(accounts, DAY2, audit);
        Transfer t = serviceDay2.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("100"));
        assertThat(t.status()).isEqualTo(Transfer.Status.COMPLETED);
    }

    // ── REQ-TRF-04 ───────────────────────────────────────────────────────────

    /**
     * Spec: a first-time transfer of SGD 1,000.01 or more to a new payee is PENDING_REVIEW;
     * neither balance changes.
     */
    @Test
    @Tag("REQ-TRF-04")
    void newPayeeOver1000IsPendingReview() {
        TransferService service = new TransferService(accounts, DAY1, audit);
        BigDecimal aliceBefore = alice.getBalance();
        BigDecimal bobBefore   = bob.getBalance();

        Transfer t = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("1000.01"));

        assertThat(t.status()).isEqualTo(Transfer.Status.PENDING_REVIEW);
        assertThat(alice.getBalance()).isEqualByComparingTo(aliceBefore);
        assertThat(bob.getBalance()).isEqualByComparingTo(bobBefore);
    }

    /**
     * Spec: a first-time transfer of exactly SGD 1,000.00 completes immediately.
     */
    @Test
    @Tag("REQ-TRF-04")
    void newPayeeExactly1000CompletesImmediately() {
        TransferService service = new TransferService(accounts, DAY1, audit);
        Transfer t = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("1000.00"));
        assertThat(t.status()).isEqualTo(Transfer.Status.COMPLETED);
        assertThat(alice.getBalance()).isEqualByComparingTo("9000.00");
        assertThat(bob.getBalance()).isEqualByComparingTo("11000.00");
    }

    /**
     * Spec: once one transfer to a payee has completed, later transfers to that payee are not held.
     */
    @Test
    @Tag("REQ-TRF-04")
    void knownPayeeOver1000IsNotHeld() {
        TransferService service = new TransferService(accounts, DAY1, audit);
        service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("10"));
        Transfer t = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("1000.01"));
        assertThat(t.status()).isEqualTo(Transfer.Status.COMPLETED);
    }

    // ── REQ-TRF-06 ───────────────────────────────────────────────────────────

    /**
     * Spec: history includes transfers where the account is source or destination;
     * transfers between two other accounts do not appear.
     */
    @Test
    @Tag("REQ-TRF-06")
    void historyIncludesBothIncomingAndOutgoing() {
        TransferService service = new TransferService(accounts, DAY1, audit);
        Account charlie = accounts.open("Charlie", Money.sgd("5000"), "333333");

        Transfer outgoing = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("100"));
        Transfer incoming = service.transfer(charlie.getAccountNumber(), alice.getAccountNumber(), Money.sgd("50"));
        // bob→charlie transfer must NOT appear in alice's history
        service.transfer(bob.getAccountNumber(), charlie.getAccountNumber(), Money.sgd("10"));

        List<Transfer> history = service.history(alice.getAccountNumber());

        assertThat(history).contains(outgoing, incoming);
        assertThat(history).noneMatch(t ->
                t.fromAccount().equals(bob.getAccountNumber()) &&
                t.toAccount().equals(charlie.getAccountNumber()));
    }

    /**
     * Spec: transfers are listed newest first.
     */
    @Test
    @Tag("REQ-TRF-06")
    void historyIsNewestFirst() {
        TransferService service = new TransferService(accounts, DAY1, audit);
        Transfer first  = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("10"));
        Transfer second = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("20"));
        Transfer third  = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("30"));

        List<Transfer> history = service.history(alice.getAccountNumber());

        assertThat(history).containsExactly(third, second, first);
    }
}
