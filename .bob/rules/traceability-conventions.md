# Traceability conventions (apply in every mode)

- Requirement IDs look like `REQ-<MODULE>-<NN>` (`REQ-ACC-01`, `REQ-TRF-03`, `REQ-LN-06`, `REQ-SEC-05`).
  Module codes map to Java packages: `ACC` → `com.spectrace.bank.accounts`,
  `TRF` → `com.spectrace.bank.transfers`, `LN` → `com.spectrace.bank.loans`,
  `SEC` → `com.spectrace.bank.security`.
- Every test that proves a requirement carries JUnit 5 `@Tag("REQ-XXX-NN")`. One test may carry several tags.
  The tag is the single source of truth for traceability, so never trace a test that lacks the tag.
- Tests are JUnit 5 + AssertJ and construct services directly (no Spring context), **except** when an
  acceptance criterion is about the HTTP API (status codes such as 403, authentication, what a request
  returns). Test those through the real stack with `@SpringBootTest` + `@AutoConfigureMockMvc` and `MockMvc`,
  because a service-level test cannot see a missing security check in a controller or filter. Time-dependent logic
  uses a fixed `java.time.Clock`; see `bank-app/src/test/java/com/spectrace/bank/transfers/TransferServiceTest.java`.
- Assertions use the exact values from the requirement's acceptance criteria. If the spec says 754.90,
  assert 754.90. Never "adjust" an expected value to make a test pass.
- Money is `BigDecimal`; compare with `isEqualByComparingTo`.
- Build with the Maven wrapper from `bank-app/`: `./mvnw -q test`. Never use a global `mvn`.
- Production code under `bank-app/src/main` is read-only for SpecTrace. Bugs are reported, not fixed,
  unless the user explicitly asks for a fix in a separate step.
