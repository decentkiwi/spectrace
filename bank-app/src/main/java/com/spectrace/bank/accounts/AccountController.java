package com.spectrace.bank.accounts;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    record OpenRequest(
        @NotBlank(message = "Owner name is required") String ownerName,
        @NotNull(message = "Initial deposit is required") @PositiveOrZero BigDecimal initialDeposit,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits") String pin
    ) {}

    record AmountRequest(
        @NotNull(message = "Amount is required") @jakarta.validation.constraints.Positive BigDecimal amount
    ) {}

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    public Account open(@Valid @RequestBody OpenRequest req) {
        return accounts.open(req.ownerName(), req.initialDeposit(), req.pin());
    }

    @GetMapping("/{number}")
    public Account get(@PathVariable String number) {
        return accounts.get(number);
    }

    @GetMapping("/{number}/balance")
    public BigDecimal balance(@PathVariable String number) {
        return accounts.getBalance(number);
    }

    @PostMapping("/{number}/deposit")
    public Account deposit(@PathVariable String number, @Valid @RequestBody AmountRequest req) {
        return accounts.deposit(number, req.amount());
    }

    @PostMapping("/{number}/withdraw")
    public Account withdraw(@PathVariable String number, @Valid @RequestBody AmountRequest req) {
        return accounts.withdraw(number, req.amount());
    }

    @PostMapping("/{number}/close")
    public Account close(@PathVariable String number) {
        return accounts.close(number);
    }
}
