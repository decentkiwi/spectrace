package com.spectrace.bank.accounts;

import java.math.BigDecimal;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Account {

    public enum Status { ACTIVE, FROZEN, CLOSED }

    private final String accountNumber;
    private final String ownerName;
    @JsonIgnore
    private final String pinHash;
    private BigDecimal balance;
    private Status status = Status.ACTIVE;

    Account(String accountNumber, String ownerName, String pinHash, BigDecimal openingBalance) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.pinHash = pinHash;
        this.balance = openingBalance;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Status getStatus() {
        return status;
    }

    /**
     * Checks a raw PIN candidate against the stored hash using the supplied encoder.
     * Keeping the encoder out of Account makes tests that use NoOpPasswordEncoder work
     * without BCrypt, since NoOpPasswordEncoder stores the PIN as plain text.
     */
    boolean pinMatches(String candidate, PasswordEncoder encoder) {
        return encoder.matches(candidate, pinHash);
    }

    void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    void setStatus(Status status) {
        this.status = status;
    }
}
