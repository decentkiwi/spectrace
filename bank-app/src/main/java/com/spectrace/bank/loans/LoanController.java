package com.spectrace.bank.loans;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loans;

    public LoanController(LoanService loans) {
        this.loans = loans;
    }

    @PostMapping
    public Loan apply(@RequestBody LoanApplication app) {
        return loans.apply(app);
    }

    @GetMapping("/{id}")
    public Loan get(@PathVariable String id) {
        return loans.get(id);
    }
}
