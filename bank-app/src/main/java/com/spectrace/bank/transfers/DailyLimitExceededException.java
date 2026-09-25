package com.spectrace.bank.transfers;

import com.spectrace.bank.common.BankException;

public class DailyLimitExceededException extends BankException {

    public DailyLimitExceededException(String accountNumber) {
        super("Daily transfer limit exceeded for account " + accountNumber);
    }
}
