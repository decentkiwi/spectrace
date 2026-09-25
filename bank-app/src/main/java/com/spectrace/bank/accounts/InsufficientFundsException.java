package com.spectrace.bank.accounts;

import com.spectrace.bank.common.BankException;

public class InsufficientFundsException extends BankException {

    public InsufficientFundsException(String accountNumber) {
        super("Insufficient funds in account " + accountNumber);
    }
}
