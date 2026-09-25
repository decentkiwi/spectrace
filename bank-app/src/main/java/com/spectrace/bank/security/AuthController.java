package com.spectrace.bank.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectrace.bank.accounts.AccountService;
import com.spectrace.bank.common.AuditService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/auth/login  — accepts {accountNumber, pin} and returns a JWT.
 * Subject to rate limiting: 5 failed attempts per 15 minutes locks the account.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    record LoginRequest(
        @NotBlank String accountNumber,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits") String pin
    ) {}

    record LoginResponse(String token, String accountNumber) {}

    private final AccountService accountService;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;

    public AuthController(AccountService accountService, JwtUtil jwtUtil,
                          LoginRateLimiter rateLimiter, AuditService audit) {
        this.accountService = accountService;
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        String acct = req.accountNumber();

        if (rateLimiter.isBlocked(acct)) {
            audit.record(acct, "LOGIN_BLOCKED", "Too many failed attempts");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(java.util.Map.of("error", "Too many failed login attempts. Try again later."));
        }

        boolean valid;
        try {
            valid = accountService.verifyPin(acct, req.pin());
        } catch (Exception e) {
            // account not found — treat same as bad PIN to avoid account enumeration
            rateLimiter.recordFailure(acct);
            audit.record(acct, "LOGIN_FAILED", "Account not found or bad PIN");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("error", "Invalid account number or PIN"));
        }

        if (!valid) {
            rateLimiter.recordFailure(acct);
            audit.record(acct, "LOGIN_FAILED", "Bad PIN");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("error", "Invalid account number or PIN"));
        }

        rateLimiter.clearFailures(acct);
        String token = jwtUtil.generate(acct);
        audit.record(acct, "LOGIN_SUCCESS", "JWT issued");
        return ResponseEntity.ok(new LoginResponse(token, acct));
    }
}
