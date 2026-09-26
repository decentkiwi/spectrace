package com.spectrace.bank.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.bank.accounts.Account;
import com.spectrace.bank.accounts.AccountService;
import com.spectrace.bank.common.AuditService;
import com.spectrace.bank.common.Money;
import com.spectrace.bank.transfers.TransferService;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRequirementsTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AccountService bankAccounts;
    @Autowired
    private JwtUtil bankJwt;

    private final ObjectMapper json = new ObjectMapper();
    private AuditService audit;
    private AccountService accounts;
    private LoginRateLimiter limiter;
    private AuthController auth;

    @BeforeEach
    void setUp() {
        audit = new AuditService();
        accounts = new AccountService(new BCryptPasswordEncoder(4), audit);
        limiter = new LoginRateLimiter(5, 900);
        auth = new AuthController(accounts, new JwtUtil(SECRET, 3600), limiter, audit);
    }

    private HttpStatus login(String account, String pin) {
        return HttpStatus.valueOf(auth.login(new AuthController.LoginRequest(account, pin)).getStatusCode().value());
    }

    // REQ-SEC-01 PIN protection

    @Test
    @Tag("REQ-SEC-01")
    void storedPinIsAHashNotThePlainTextPin() throws Exception {
        Account account = accounts.open("Alice", Money.sgd("10"), "123456");
        Field field = Account.class.getDeclaredField("pinHash");
        field.setAccessible(true);

        assertThat((String) field.get(account)).isNotEqualTo("123456").doesNotContain("123456");
    }

    @Test
    @Tag("REQ-SEC-01")
    void correctPinVerifiesAndWrongPinDoesNot() {
        Account account = accounts.open("Alice", Money.sgd("10"), "123456");

        assertThat(accounts.verifyPin(account.getAccountNumber(), "123456")).isTrue();
        assertThat(accounts.verifyPin(account.getAccountNumber(), "654321")).isFalse();
    }

    @Test
    @Tag("REQ-SEC-01")
    void accountJsonContainsNoPinOrHash() throws Exception {
        Account account = accounts.open("Alice", Money.sgd("10"), "123456");

        String body = json.writeValueAsString(account);

        assertThat(body).doesNotContainIgnoringCase("pin").doesNotContain("123456");
    }

    // REQ-SEC-02 Session tokens

    @Test
    @Tag("REQ-SEC-02")
    void freshTokenIsAcceptedAndIdentifiesTheAccount() {
        JwtUtil jwt = new JwtUtil(SECRET, 3600);
        String token = jwt.generate("SG12345678");

        assertThat(jwt.isValid(token)).isTrue();
        assertThat(jwt.validate(token)).isEqualTo("SG12345678");
    }

    @Test
    @Tag("REQ-SEC-02")
    void tamperedTokenIsRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, 3600);
        String token = jwt.generate("SG12345678");
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"SG99999999\",\"role\":\"USER\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(jwt.isValid(parts[0] + "." + forgedPayload + "." + parts[2])).isFalse();
        assertThat(new JwtUtil("another-secret-that-is-also-32-chars-long", 3600).isValid(token)).isFalse();
    }

    @Test
    @Tag("REQ-SEC-02")
    void expiredTokenIsRejected() {
        JwtUtil alreadyExpired = new JwtUtil(SECRET, -60);

        assertThat(alreadyExpired.isValid(alreadyExpired.generate("SG12345678"))).isFalse();
    }

    @Test
    @Tag("REQ-SEC-02")
    void tokenLifetimeIsSixtyMinutes() throws Exception {
        String token = bankJwt.generate("SG12345678");
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(3600);
    }

    // REQ-SEC-03 Login throttling

    @Test
    @Tag("REQ-SEC-03")
    void afterFiveFailuresEvenTheCorrectPinIsRefused() {
        String acct = accounts.open("Alice", Money.sgd("10"), "123456").getAccountNumber();
        for (int i = 0; i < 5; i++) {
            assertThat(login(acct, "000000")).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(login(acct, "123456")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @Tag("REQ-SEC-03")
    void fourFailuresDoNotBlock() {
        String acct = accounts.open("Alice", Money.sgd("10"), "123456").getAccountNumber();
        for (int i = 0; i < 4; i++) {
            login(acct, "000000");
        }

        assertThat(login(acct, "123456")).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Tag("REQ-SEC-03")
    void successfulLoginResetsTheCount() {
        String acct = accounts.open("Alice", Money.sgd("10"), "123456").getAccountNumber();
        for (int i = 0; i < 4; i++) {
            login(acct, "000000");
        }
        login(acct, "123456");
        for (int i = 0; i < 4; i++) {
            login(acct, "000000");
        }

        assertThat(login(acct, "123456")).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Tag("REQ-SEC-03")
    void failuresOnOneAccountDoNotAffectAnother() {
        String alice = accounts.open("Alice", Money.sgd("10"), "123456").getAccountNumber();
        String bob = accounts.open("Bob", Money.sgd("10"), "222222").getAccountNumber();
        for (int i = 0; i < 5; i++) {
            login(alice, "000000");
        }

        assertThat(login(bob, "222222")).isEqualTo(HttpStatus.OK);
    }

    // REQ-SEC-04 Audit trail

    @Test
    @Tag("REQ-SEC-04")
    void loginsMoneyMovementsFreezesAndClosuresAreAudited() {
        TransferService transfers = new TransferService(accounts, java.time.Clock.systemUTC(), audit);
        String alice = accounts.open("Alice", Money.sgd("500"), "123456").getAccountNumber();
        String bob = accounts.open("Bob", Money.sgd("0"), "222222").getAccountNumber();

        login(alice, "123456");
        login(alice, "000000");
        accounts.deposit(alice, Money.sgd("10"));
        accounts.withdraw(alice, Money.sgd("10"));
        assertThatThrownBy(() -> accounts.withdraw(bob, Money.sgd("1")));
        transfers.transfer(alice, bob, Money.sgd("100"));
        accounts.withdraw(bob, Money.sgd("100"));
        accounts.close(bob);
        accounts.freeze(alice);

        List<AuditService.AuditEvent> log = audit.getAll();
        assertThat(log).extracting(AuditService.AuditEvent::eventType).contains(
                "LOGIN_SUCCESS", "LOGIN_FAILED", "DEPOSIT", "WITHDRAWAL", "WITHDRAWAL_FAILED",
                "TRANSFER_COMPLETED", "ACCOUNT_CLOSED", "ACCOUNT_FROZEN");
        assertThat(log).allSatisfy(e -> {
            assertThat(e.timestamp()).isNotNull();
            assertThat(e.actor()).isNotBlank();
        });
    }

    @Test
    @Tag("REQ-SEC-04")
    void auditEntriesCannotBeModifiedOrRemoved() {
        accounts.open("Alice", Money.sgd("10"), "123456");
        List<AuditService.AuditEvent> log = audit.getAll();

        assertThatThrownBy(log::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> log.remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    // REQ-SEC-05 Account ownership (through the real HTTP + Spring Security stack)

    private String bearer(String accountNumber) {
        return "Bearer " + bankJwt.generate(accountNumber);
    }

    @Test
    @Tag("REQ-SEC-05")
    void customerCannotViewAnotherCustomersAccount() throws Exception {
        String alice = bankAccounts.open("Alice", Money.sgd("100"), "111111").getAccountNumber();
        String bob = bankAccounts.open("Bob", Money.sgd("100"), "222222").getAccountNumber();

        mvc.perform(get("/api/accounts/" + bob).header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("REQ-SEC-05")
    void customerCannotTransferFromAnotherCustomersAccount() throws Exception {
        String alice = bankAccounts.open("Alice", Money.sgd("100"), "111111").getAccountNumber();
        String bob = bankAccounts.open("Bob", Money.sgd("100"), "222222").getAccountNumber();
        String body = "{\"fromAccount\":\"" + bob + "\",\"toAccount\":\"" + alice + "\",\"amount\":50}";

        mvc.perform(post("/api/transfers").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(bankAccounts.getBalance(bob)).isEqualByComparingTo("100.00");
    }

    @Test
    @Tag("REQ-SEC-05")
    void customerCanViewTheirOwnAccount() throws Exception {
        String alice = bankAccounts.open("Alice", Money.sgd("100"), "111111").getAccountNumber();

        mvc.perform(get("/api/accounts/" + alice).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
    }

    @Test
    @Tag("REQ-SEC-05")
    void requestsWithoutAValidTokenAreRefused() throws Exception {
        String alice = bankAccounts.open("Alice", Money.sgd("100"), "111111").getAccountNumber();

        int noToken = mvc.perform(get("/api/accounts/" + alice)).andReturn().getResponse().getStatus();
        int badToken = mvc.perform(get("/api/accounts/" + alice).header("Authorization", "Bearer not-a-token"))
                .andReturn().getResponse().getStatus();

        assertThat(List.of(noToken, badToken)).allMatch(s -> s == 401 || s == 403);
    }
}
