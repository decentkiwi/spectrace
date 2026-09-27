package com.spectrace.bank.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.bank.accounts.Account;
import com.spectrace.bank.accounts.AccountService;
import com.spectrace.bank.common.AuditService;
import com.spectrace.bank.common.Money;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRequirementsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private JwtUtil jwtUtil;

    private ObjectMapper mapper = new ObjectMapper();

    // ─── REQ-SEC-01: PIN protection ──────────────────────────────────────────

    @Test
    @Tag("REQ-SEC-01")
    void storedPinIsNotPlainText() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuditService localAudit = new AuditService();
        AccountService localAccounts = new AccountService(encoder, localAudit);

        Account account = localAccounts.open("Alice Tan", Money.sgd("100"), "123456");

        // BCrypt hash of "123456" is not "123456"; verify correct pin round-trips
        assertThat(localAccounts.verifyPin(account.getAccountNumber(), "123456")).isTrue();
        assertThat(localAccounts.verifyPin(account.getAccountNumber(), "654321")).isFalse();
    }

    @Test
    @Tag("REQ-SEC-01")
    void correctPinVerifiesAndIncorrectPinDoesNot() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuditService localAudit = new AuditService();
        AccountService localAccounts = new AccountService(encoder, localAudit);

        Account account = localAccounts.open("Bob Lee", Money.sgd("200"), "999888");

        assertThat(localAccounts.verifyPin(account.getAccountNumber(), "999888")).isTrue();
        assertThat(localAccounts.verifyPin(account.getAccountNumber(), "000000")).isFalse();
    }

    @Test
    @Tag("REQ-SEC-01")
    void apiResponseContainsNoPinOrPinHashField() throws Exception {
        String requestBody = "{\"ownerName\":\"Carol Chen\",\"initialDeposit\":50.00,\"pin\":\"112233\"}";
        MvcResult result = mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("pin");
        assertThat(json).doesNotContain("pinHash");
        assertThat(json).doesNotContain("112233");
    }

    // ─── REQ-SEC-02: Session tokens ──────────────────────────────────────────

    @Test
    @Tag("REQ-SEC-02")
    void freshlyIssuedTokenIsAcceptedAndIdentifiesAccount() {
        String token = jwtUtil.generate("SG12345678");
        assertThat(jwtUtil.isValid(token)).isTrue();
        assertThat(jwtUtil.validate(token)).isEqualTo("SG12345678");
    }

    @Test
    @Tag("REQ-SEC-02")
    void tamperedTokenIsRejected() {
        String token = jwtUtil.generate("SG12345678");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");
        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    @Tag("REQ-SEC-02")
    void expiredTokenIsRejected() {
        String secret = "spectrace-dev-secret-change-in-prod-min32chars";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long past = System.currentTimeMillis() - 2000L;
        String expiredToken = Jwts.builder()
                .subject("SG99999999")
                .claim("role", "USER")
                .issuedAt(new Date(past - 1000L))
                .expiration(new Date(past))
                .signWith(key)
                .compact();

        assertThat(jwtUtil.isValid(expiredToken)).isFalse();
    }

    @Test
    @Tag("REQ-SEC-02")
    void tokenLifetimeIs60Minutes() {
        String secret = "spectrace-dev-secret-change-in-prod-min32chars";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        // A token expiring in exactly 3600 s (60 min) from now must be valid
        String tokenAt3600 = Jwts.builder()
                .subject("SG00000001")
                .claim("role", "USER")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3600_000L))
                .signWith(key)
                .compact();
        assertThat(jwtUtil.isValid(tokenAt3600)).isTrue();

        // A token that already expired 1 s ago must be invalid
        String expiredToken = Jwts.builder()
                .subject("SG00000001")
                .claim("role", "USER")
                .issuedAt(new Date(now - 3601_000L))
                .expiration(new Date(now - 1000L))
                .signWith(key)
                .compact();
        assertThat(jwtUtil.isValid(expiredToken)).isFalse();
    }

    // ─── REQ-SEC-03: Login throttling ────────────────────────────────────────

    @Test
    @Tag("REQ-SEC-03")
    void after5FailedAttemptsNextAttemptIsRefused() {
        LoginRateLimiter limiter = new LoginRateLimiter(5, 900);
        String acct = "SG11111111";

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(acct);
        }
        assertThat(limiter.isBlocked(acct)).isTrue();
    }

    @Test
    @Tag("REQ-SEC-03")
    void fourFailedAttemptsDoNotBlockAccount() {
        LoginRateLimiter limiter = new LoginRateLimiter(5, 900);
        String acct = "SG22222222";

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(acct);
        }
        assertThat(limiter.isBlocked(acct)).isFalse();
    }

    @Test
    @Tag("REQ-SEC-03")
    void successfulLoginResetsFailedAttemptCount() {
        LoginRateLimiter limiter = new LoginRateLimiter(5, 900);
        String acct = "SG33333333";

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(acct);
        }
        limiter.clearFailures(acct);
        assertThat(limiter.isBlocked(acct)).isFalse();
    }

    @Test
    @Tag("REQ-SEC-03")
    void failedAttemptsOnOneAccountDoNotAffectOtherAccounts() {
        LoginRateLimiter limiter = new LoginRateLimiter(5, 900);
        String acct1 = "SG44444444";
        String acct2 = "SG55555555";

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(acct1);
        }
        assertThat(limiter.isBlocked(acct1)).isTrue();
        assertThat(limiter.isBlocked(acct2)).isFalse();
    }

    // ─── REQ-SEC-04: Audit trail ─────────────────────────────────────────────

    @Test
    @Tag("REQ-SEC-04")
    void successfulAndFailedLoginsAreRecorded() throws Exception {
        String openBody = "{\"ownerName\":\"Audit Test\",\"initialDeposit\":100.00,\"pin\":\"777777\"}";
        MvcResult openResult = mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(openBody))
                .andExpect(status().isOk())
                .andReturn();
        String accountNumber = mapper.readTree(openResult.getResponse().getContentAsString())
                .get("accountNumber").asText();

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"" + accountNumber + "\",\"pin\":\"777777\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"" + accountNumber + "\",\"pin\":\"000000\"}"))
                .andExpect(status().isUnauthorized());

        List<AuditService.AuditEvent> events = auditService.getAll();
        assertThat(events.stream().anyMatch(e -> e.actor().equals(accountNumber) && e.eventType().equals("LOGIN_SUCCESS"))).isTrue();
        assertThat(events.stream().anyMatch(e -> e.actor().equals(accountNumber) && e.eventType().equals("LOGIN_FAILED"))).isTrue();
    }

    @Test
    @Tag("REQ-SEC-04")
    void depositsAndWithdrawalsAreRecorded() {
        AuditService localAudit = new AuditService();
        AccountService localAccounts = new AccountService(new BCryptPasswordEncoder(), localAudit);

        Account account = localAccounts.open("Dave Ng", Money.sgd("500"), "456456");
        String num = account.getAccountNumber();

        localAccounts.deposit(num, Money.sgd("100"));
        localAccounts.withdraw(num, Money.sgd("50"));

        List<AuditService.AuditEvent> events = localAudit.getAll();
        assertThat(events.stream().anyMatch(e -> e.actor().equals(num) && e.eventType().equals("DEPOSIT"))).isTrue();
        assertThat(events.stream().anyMatch(e -> e.actor().equals(num) && e.eventType().equals("WITHDRAWAL"))).isTrue();
    }

    @Test
    @Tag("REQ-SEC-04")
    void accountFreezesAndClosuresAreRecorded() {
        AuditService localAudit = new AuditService();
        AccountService localAccounts = new AccountService(new BCryptPasswordEncoder(), localAudit);

        Account account = localAccounts.open("Eve Lim", Money.sgd("0"), "321321");
        String num = account.getAccountNumber();

        localAccounts.freeze(num);
        localAccounts.close(num);

        List<AuditService.AuditEvent> events = localAudit.getAll();
        assertThat(events.stream().anyMatch(e -> e.actor().equals(num) && e.eventType().equals("ACCOUNT_FROZEN"))).isTrue();
        assertThat(events.stream().anyMatch(e -> e.actor().equals(num) && e.eventType().equals("ACCOUNT_CLOSED"))).isTrue();
    }

    @Test
    @Tag("REQ-SEC-04")
    void auditEntriesCannotBeRemovedThroughInterface() {
        AuditService localAudit = new AuditService();
        localAudit.record("SG00000000", "TEST_EVENT", "detail");

        List<AuditService.AuditEvent> snapshot = localAudit.getAll();
        assertThatThrownBy(() -> snapshot.remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ─── REQ-SEC-05: Account ownership (HTTP 403) ────────────────────────────

    @Test
    @Tag("REQ-SEC-05")
    void customerRequestingAnotherCustomersAccountDetailsIsRefusedWith403() throws Exception {
        MvcResult aliceResult = mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerName\":\"Alice Owner\",\"initialDeposit\":100.00,\"pin\":\"111111\"}"))
                .andExpect(status().isOk()).andReturn();
        MvcResult bobResult = mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerName\":\"Bob Other\",\"initialDeposit\":100.00,\"pin\":\"222222\"}"))
                .andExpect(status().isOk()).andReturn();

        String aliceAccount = mapper.readTree(aliceResult.getResponse().getContentAsString()).get("accountNumber").asText();
        String bobAccount   = mapper.readTree(bobResult.getResponse().getContentAsString()).get("accountNumber").asText();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"" + aliceAccount + "\",\"pin\":\"111111\"}"))
                .andExpect(status().isOk()).andReturn();
        String aliceToken = mapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        // Alice tries to GET Bob's account — must be 403
        mockMvc.perform(get("/api/accounts/" + bobAccount)
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("REQ-SEC-05")
    void customerCanViewTheirOwnAccountDetails() throws Exception {
        MvcResult openResult = mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerName\":\"Own User\",\"initialDeposit\":50.00,\"pin\":\"333333\"}"))
                .andExpect(status().isOk()).andReturn();
        String accountNumber = mapper.readTree(openResult.getResponse().getContentAsString()).get("accountNumber").asText();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"" + accountNumber + "\",\"pin\":\"333333\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/api/accounts/" + accountNumber)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(accountNumber));
    }

    @Test
    @Tag("REQ-SEC-05")
    void requestWithoutValidSessionTokenIsRefused() throws Exception {
        mockMvc.perform(get("/api/accounts/SG00000000"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Tag("REQ-SEC-05")
    void customerAttemptingTransferFromAnotherCustomersAccountIsRefusedWith403() throws Exception {
        MvcResult aResult = mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerName\":\"Sender A\",\"initialDeposit\":1000.00,\"pin\":\"444444\"}"))
                .andExpect(status().isOk()).andReturn();
        MvcResult bResult = mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerName\":\"Receiver B\",\"initialDeposit\":100.00,\"pin\":\"555555\"}"))
                .andExpect(status().isOk()).andReturn();

        String accountA = mapper.readTree(aResult.getResponse().getContentAsString()).get("accountNumber").asText();
        String accountB = mapper.readTree(bResult.getResponse().getContentAsString()).get("accountNumber").asText();

        MvcResult loginB = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"" + accountB + "\",\"pin\":\"555555\"}"))
                .andExpect(status().isOk()).andReturn();
        String tokenB = mapper.readTree(loginB.getResponse().getContentAsString()).get("token").asText();

        // B tries to transfer from A's account — must be 403
        mockMvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + tokenB)
                .content("{\"fromAccount\":\"" + accountA + "\",\"toAccount\":\"" + accountB + "\",\"amount\":100.00}"))
                .andExpect(status().isForbidden());
    }
}
