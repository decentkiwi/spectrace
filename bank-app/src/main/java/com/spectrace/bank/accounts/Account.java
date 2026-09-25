package com.spectrace.bank.accounts;

import java.math.BigDecimal;

public class Account {

    public enum Status { ACTIVE, FROZEN, CLOSED }

    private final String accountNumber;
    private final String ownerName;
    private final String pin;
    private BigDecimal balance;
    private Status status = Status.ACTIVE;

    Account(String accountNumber, String ownerName, String pin, BigDecimal openingBalance) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.pin = pin;
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

    boolean pinMatches(String candidate) {
        return pin.equals(candidate);
    }

    void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    void setStatus(Status status) {
        this.status = status;
    }
}
