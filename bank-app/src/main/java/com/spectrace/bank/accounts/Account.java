package com.spectrace.bank.accounts;

import java.math.BigDecimal;

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

    /** Checks a raw PIN candidate against the stored BCrypt hash. */
    boolean pinMatches(String candidate) {
        // BCryptPasswordEncoder.matches is called from AccountService
        // which holds the PasswordEncoder bean; the raw hash is stored here.
        return org.springframework.security.crypto.bcrypt.BCrypt.checkpw(candidate, pinHash);
    }

    void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    void setStatus(Status status) {
        this.status = status;
    }
}
