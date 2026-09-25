package com.spectrace.bank.transfers;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    record TransferRequest(String fromAccount, String toAccount, BigDecimal amount) {
    }

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    public Transfer transfer(@RequestBody TransferRequest req) {
        return transfers.transfer(req.fromAccount(), req.toAccount(), req.amount());
    }

    @GetMapping("/history/{accountNumber}")
    public List<Transfer> history(@PathVariable String accountNumber) {
        return transfers.history(accountNumber);
    }
}
