package com.spectrace.bank.transfers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.spectrace.bank.accounts.Account;
import com.spectrace.bank.accounts.AccountService;
import com.spectrace.bank.common.BankException;
import com.spectrace.bank.common.Money;

@Service
public class TransferService {

    static final BigDecimal DAILY_LIMIT = Money.sgd("5000");
    static final BigDecimal NEW_PAYEE_REVIEW_THRESHOLD = Money.sgd("1000");

    private final AccountService accounts;
    private final Clock clock;
    private final List<Transfer> ledger = new ArrayList<>();

    public TransferService(AccountService accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    public synchronized Transfer transfer(String from, String to, BigDecimal amount) {
        if (from.equals(to)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        if (!Money.isPositive(amount)) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        Account destination = accounts.get(to);
        if (destination.getStatus() != Account.Status.ACTIVE) {
            throw new BankException("Destination account " + to + " is " + destination.getStatus());
        }

        BigDecimal sentToday = totalSentOn(from, LocalDate.now(clock));
        if (sentToday.add(amount).compareTo(DAILY_LIMIT) >= 0) {
            throw new DailyLimitExceededException(from);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (isNewPayee(from, to) && amount.compareTo(NEW_PAYEE_REVIEW_THRESHOLD) > 0) {
            return record(new Transfer(nextReference(), from, to, amount, now, Transfer.Status.PENDING_REVIEW));
        }

        accounts.withdraw(from, amount);
        try {
            accounts.deposit(to, amount);
        } catch (RuntimeException e) {
            accounts.deposit(from, amount);
            throw e;
        }
        return record(new Transfer(nextReference(), from, to, amount, now, Transfer.Status.COMPLETED));
    }

    public synchronized List<Transfer> history(String accountNumber) {
        List<Transfer> result = new ArrayList<>();
        for (int i = ledger.size() - 1; i >= 0; i--) {
            Transfer t = ledger.get(i);
            if (t.fromAccount().equals(accountNumber) || t.toAccount().equals(accountNumber)) {
                result.add(t);
            }
        }
        return result;
    }

    private BigDecimal totalSentOn(String accountNumber, LocalDate day) {
        return ledger.stream()
                .filter(t -> t.fromAccount().equals(accountNumber))
                .filter(t -> t.timestamp().toLocalDate().equals(day))
                .map(Transfer::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isNewPayee(String from, String to) {
        return ledger.stream().noneMatch(t -> t.fromAccount().equals(from)
                && t.toAccount().equals(to)
                && t.status() == Transfer.Status.COMPLETED);
    }

    private Transfer record(Transfer transfer) {
        ledger.add(transfer);
        return transfer;
    }

    private static String nextReference() {
        return "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
