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

class TransfersRequirementsTest {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-09-26T02:00:00Z"), SGT);

    /** A clock the test can move forward, so one ledger spans two calendar days. */
    private static final class MovableClock extends Clock {
        Instant now;

        MovableClock(Instant start) {
            now = start;
        }

        @Override
        public ZoneId getZone() {
            return SGT;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private AccountService accounts;
    private Account alice;
    private Account bob;
    private Account carol;

    @BeforeEach
    void setUp() {
        accounts = new AccountService();
        alice = accounts.open("Alice", Money.sgd("20000"), "111111");
        bob = accounts.open("Bob", Money.sgd("0"), "222222");
        carol = accounts.open("Carol", Money.sgd("0"), "333333");
    }

    private void sendFourThousandToBob(TransferService service) {
        for (int i = 0; i < 4; i++) {
            service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("1000"));
        }
    }

    @Test
    @Tag("REQ-TRF-03")
    void transferReachingExactlyTheDailyLimitIsAccepted() {
        TransferService service = new TransferService(accounts, TODAY);
        sendFourThousandToBob(service);

        Transfer t = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("1000"));

        assertThat(t.status()).isEqualTo(Transfer.Status.COMPLETED);
    }

    @Test
    @Tag("REQ-TRF-03")
    void transferBeyondTheDailyLimitIsRejected() {
        TransferService service = new TransferService(accounts, TODAY);
        sendFourThousandToBob(service);
        service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("1000"));

        assertThatThrownBy(() -> service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("0.01")))
                .isInstanceOf(DailyLimitExceededException.class);
    }

    @Test
    @Tag("REQ-TRF-03")
    void previousDaysTransfersDoNotCountTowardsToday() {
        MovableClock clock = new MovableClock(Instant.parse("2026-09-25T02:00:00Z"));
        TransferService service = new TransferService(accounts, clock);
        sendFourThousandToBob(service);

        clock.now = Instant.parse("2026-09-26T02:00:00Z");
        sendFourThousandToBob(service);

        assertThat(alice.getBalance()).isEqualByComparingTo("12000.00");
    }

    @Test
    @Tag("REQ-TRF-04")
    void largeFirstTransferToNewPayeeIsHeldForReview() {
        TransferService service = new TransferService(accounts, TODAY);

        Transfer t = service.transfer(alice.getAccountNumber(), carol.getAccountNumber(), Money.sgd("1000.01"));

        assertThat(t.status()).isEqualTo(Transfer.Status.PENDING_REVIEW);
        assertThat(alice.getBalance()).isEqualByComparingTo("20000.00");
        assertThat(carol.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @Tag("REQ-TRF-04")
    void firstTransferOfExactly1000CompletesImmediately() {
        TransferService service = new TransferService(accounts, TODAY);

        Transfer t = service.transfer(alice.getAccountNumber(), carol.getAccountNumber(), Money.sgd("1000"));

        assertThat(t.status()).isEqualTo(Transfer.Status.COMPLETED);
        assertThat(carol.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    @Tag("REQ-TRF-04")
    void knownPayeeIsNotHeld() {
        TransferService service = new TransferService(accounts, TODAY);
        service.transfer(alice.getAccountNumber(), carol.getAccountNumber(), Money.sgd("10"));

        Transfer t = service.transfer(alice.getAccountNumber(), carol.getAccountNumber(), Money.sgd("2000"));

        assertThat(t.status()).isEqualTo(Transfer.Status.COMPLETED);
    }

    @Test
    @Tag("REQ-TRF-06")
    void historyListsIncomingAndOutgoingNewestFirst() {
        TransferService service = new TransferService(accounts, TODAY);
        Transfer out = service.transfer(alice.getAccountNumber(), bob.getAccountNumber(), Money.sgd("100"));
        Transfer unrelated = service.transfer(alice.getAccountNumber(), carol.getAccountNumber(), Money.sgd("50"));
        Transfer in = service.transfer(bob.getAccountNumber(), alice.getAccountNumber(), Money.sgd("20"));

        assertThat(service.history(bob.getAccountNumber())).containsExactly(in, out).doesNotContain(unrelated);
    }
}
