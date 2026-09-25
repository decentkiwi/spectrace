package com.spectrace.bank.loans;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.spectrace.bank.common.BankException;
import com.spectrace.bank.common.Money;

@Service
public class LoanService {

    static final BigDecimal MIN_AMOUNT = Money.sgd("1000");
    static final BigDecimal MAX_AMOUNT = Money.sgd("100000");
    static final int MIN_TENURE = 12;
    static final int MAX_TENURE = 60;
    static final BigDecimal EARLY_REPAYMENT_FEE_RATE = new BigDecimal("0.015");

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");

    private final Map<String, Loan> loans = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1000);

    public Loan apply(LoanApplication app) {
        String id = "LN-" + sequence.incrementAndGet();
        Loan loan = evaluate(id, app);
        loans.put(id, loan);
        return loan;
    }

    public Loan get(String id) {
        Loan loan = loans.get(id);
        if (loan == null) {
            throw new BankException("Loan not found: " + id);
        }
        return loan;
    }

    public BigDecimal interestRateFor(BigDecimal amount) {
        if (amount.compareTo(Money.sgd("20000")) < 0) {
            return new BigDecimal("6.5");
        }
        if (amount.compareTo(Money.sgd("50000")) <= 0) {
            return new BigDecimal("5.5");
        }
        return new BigDecimal("4.5");
    }

    public BigDecimal monthlyInstalment(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        BigDecimal r = annualRatePercent.divide(TWELVE_HUNDRED, MC);
        BigDecimal growth = BigDecimal.ONE.add(r).pow(months, MC);
        BigDecimal instalment = principal.multiply(r, MC).multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE), MC);
        return instalment.setScale(2, RoundingMode.DOWN);
    }

    public BigDecimal earlyRepaymentFee(BigDecimal outstandingPrincipal) {
        return Money.round(outstandingPrincipal.multiply(EARLY_REPAYMENT_FEE_RATE));
    }

    private Loan evaluate(String id, LoanApplication app) {
        if (app.amount() == null
                || app.amount().compareTo(MIN_AMOUNT) < 0
                || app.amount().compareTo(MAX_AMOUNT) > 0) {
            return Loan.rejected(id, app, "Loan amount must be between SGD 1,000 and SGD 100,000");
        }
        if (app.tenureMonths() < MIN_TENURE || app.tenureMonths() > MAX_TENURE) {
            return Loan.rejected(id, app, "Tenure must be between 12 and 60 months");
        }
        BigDecimal rate = interestRateFor(app.amount());
        BigDecimal instalment = monthlyInstalment(app.amount(), rate, app.tenureMonths());
        BigDecimal requiredIncome = instalment.multiply(BigDecimal.valueOf(3));
        if (app.monthlyIncome() == null || app.monthlyIncome().compareTo(requiredIncome) < 0) {
            return Loan.rejected(id, app, "Monthly income must be at least 3x the instalment");
        }
        return Loan.approved(id, app, rate, instalment);
    }
}
