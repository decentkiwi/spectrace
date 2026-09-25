package com.spectrace.bank.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Helpers for SGD amounts. All money is held as BigDecimal with 2 decimal places. */
public final class Money {

    private Money() {
    }

    public static BigDecimal sgd(String amount) {
        return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}
