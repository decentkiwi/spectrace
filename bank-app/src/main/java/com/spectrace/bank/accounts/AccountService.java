package com.spectrace.bank.accounts;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.spectrace.bank.common.AuditService;
import com.spectrace.bank.common.BankException;
import com.spectrace.bank.common.Money;

@Service
public class AccountService {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AccountService(PasswordEncoder passwordEncoder, AuditService audit) {
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public Account open(String ownerName, BigDecimal initialDeposit, String pin) {
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("Owner name is required");
        }
        if (initialDeposit == null || initialDeposit.signum() < 0) {
            throw new IllegalArgumentException("Initial deposit cannot be negative");
        }
        if (pin == null || !pin.matches("\\d{6}")) {
            throw new IllegalArgumentException("PIN must be 6 digits");
        }
        String number = nextAccountNumber();
        String pinHash = passwordEncoder.encode(pin);
        Account account = new Account(number, ownerName.trim(), pinHash, Money.round(initialDeposit));
        accounts.put(number, account);
        audit.record(number, "ACCOUNT_OPENED", "Owner: " + ownerName.trim());
        return account;
    }

    public Account get(String accountNumber) {
        Account account = accounts.get(accountNumber);
        if (account == null) {
            throw new BankException("Account not found: " + accountNumber);
        }
        return account;
    }

    public Account deposit(String accountNumber, BigDecimal amount) {
        Account account = requireActive(accountNumber);
        if (!Money.isPositive(amount)) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        account.setBalance(Money.round(account.getBalance().add(amount)));
        audit.record(accountNumber, "DEPOSIT", "Amount: " + amount);
        return account;
    }

    public Account withdraw(String accountNumber, BigDecimal amount) {
        Account account = requireActive(accountNumber);
        if (!Money.isPositive(amount)) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        if (account.getBalance().compareTo(amount) < 0) {
            audit.record(accountNumber, "WITHDRAWAL_FAILED", "Insufficient funds for: " + amount);
            throw new InsufficientFundsException(accountNumber);
        }
        account.setBalance(Money.round(account.getBalance().subtract(amount)));
        audit.record(accountNumber, "WITHDRAWAL", "Amount: " + amount);
        return account;
    }

    public BigDecimal getBalance(String accountNumber) {
        return Money.round(get(accountNumber).getBalance());
    }

    public Account close(String accountNumber) {
        Account account = get(accountNumber);
        if (account.getBalance().signum() != 0) {
            throw new BankException("Account must have zero balance before closing");
        }
        account.setStatus(Account.Status.CLOSED);
        audit.record(accountNumber, "ACCOUNT_CLOSED", "");
        return account;
    }

    public Account freeze(String accountNumber) {
        Account account = get(accountNumber);
        account.setStatus(Account.Status.FROZEN);
        audit.record(accountNumber, "ACCOUNT_FROZEN", "");
        return account;
    }

    public boolean verifyPin(String accountNumber, String pin) {
        return get(accountNumber).pinMatches(pin);
    }

    Account requireActive(String accountNumber) {
        Account account = get(accountNumber);
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new BankException("Account " + accountNumber + " is " + account.getStatus());
        }
        return account;
    }

    private String nextAccountNumber() {
        String number;
        do {
            number = "SG" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (accounts.containsKey(number));
        return number;
    }
}
