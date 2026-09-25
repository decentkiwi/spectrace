package com.spectrace.bank.common;

/** Base type for business-rule violations raised by the banking services. */
public class BankException extends RuntimeException {

    public BankException(String message) {
        super(message);
    }
}
