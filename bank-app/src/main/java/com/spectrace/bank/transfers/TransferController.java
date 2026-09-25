package com.spectrace.bank.transfers;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    record TransferRequest(
        @NotBlank(message = "Source account is required") String fromAccount,
        @NotBlank(message = "Destination account is required") String toAccount,
        @NotNull(message = "Amount is required") @Positive BigDecimal amount
    ) {}

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    public Transfer transfer(@Valid @RequestBody TransferRequest req) {
        return transfers.transfer(req.fromAccount(), req.toAccount(), req.amount());
    }

    @GetMapping("/history/{accountNumber}")
    public List<Transfer> history(@PathVariable String accountNumber) {
        return transfers.history(accountNumber);
    }
}
