# SpecTrace traceability matrix

_Generated 2026-09-25T18:30:27+00:00_

**22 requirements**: 12 covered · 6 newly tested · 2 bugs found · 2 not implemented · 0 untested · 15 tests added

| Requirement | Status | Implementation | Tests | Notes |
|---|---|---|---|---|
| **REQ-ACC-01** Account opening | Covered | `AccountService#open` | opensAccountWithOwnerAndInitialDeposit (PASS)<br>rejectsBlankOwnerOrNegativeDeposit (PASS) | Validation of name, deposit and PIN in open(). |
| **REQ-ACC-02** Account number format | Covered | `AccountService#nextAccountNumber` | accountNumbersFollowFormatAndAreUnique (PASS) | SG + 8 digits with a uniqueness loop. |
| **REQ-ACC-03** Deposits | Covered | `AccountService#deposit` | depositIncreasesBalanceAndMustBePositive (PASS) |  |
| **REQ-ACC-04** Withdrawals and insufficient funds | Covered | `AccountService#withdraw` | withdrawalBeyondBalanceIsRejected (PASS) |  |
| **REQ-ACC-05** Account closure | Newly tested | `AccountService#close` | 🆕 closingAccountWithNonZeroBalanceIsRejected (PASS)<br>🆕 closingAccountWithZeroBalanceMarksItClosed (PASS) | Implemented; had no tests. |
| **REQ-ACC-06** Frozen accounts | Newly tested | `AccountService#requireActive` | 🆕 frozenAccountRejectsDepositsAndWithdrawals (PASS) | Enforced via requireActive(). |
| **REQ-ACC-07** PIN lockout | Not implemented | `AccountService#verifyPin` | - | verifyPin() only compares the PIN. No failed-attempt counter or auto-freeze exists. Implement in AccountService#verifyPin with a counter on Account. |
| **REQ-ACC-08** Balance enquiry | Covered | `AccountService#getBalance` | balanceEnquiryIsRoundedToTwoDecimals (PASS) |  |
| **REQ-LN-01** Loan amount bounds | Covered | `LoanService#evaluate` | loanAmountMustBeWithinBounds (PASS) |  |
| **REQ-LN-02** Loan tenure | Covered | `LoanService#evaluate` | tenureMustBeBetween12And60Months (PASS) |  |
| **REQ-LN-03** Monthly instalment calculation | Bug found | `LoanService#monthlyInstalment` | 🆕 instalmentFor25000At5_5PercentOver36MonthsIs754_90 (FAIL)<br>🆕 instalmentFor60000At4_5PercentOver48MonthsIs1368_21 (FAIL) | BUG: spec requires half-up rounding to the cent, but the code truncates with RoundingMode.DOWN (LoanService.java:60): 754.8975 becomes 754.89 instead of 754.90. Every customer is under-quoted by up to 1 cent/month. |
| **REQ-LN-04** Affordability check | Newly tested | `LoanService#evaluate` | 🆕 applicationIsApprovedWhenIncomeAtLeastThreeTimesInstalment (PASS)<br>🆕 applicationIsRejectedWhenIncomeBelowThreeTimesInstalment (PASS) |  |
| **REQ-LN-05** Interest rate tiers | Covered | `LoanService#interestRateFor` | interestRateDependsOnAmountTier (PASS) |  |
| **REQ-LN-06** Early repayment fee | Newly tested | `LoanService#earlyRepaymentFee` | 🆕 earlyRepaymentFeeIsOnePointFivePercentRoundedHalfUp (PASS) |  |
| **REQ-LN-07** Loan decision | Covered | `LoanService#apply` | everyApplicationIsApprovedOrRejectedWithReason (PASS) |  |
| **REQ-TRF-01** Atomic funds transfer | Covered | `TransferService#transfer` | failedCreditLeavesSourceUnchanged (PASS)<br>transferMovesFundsBetweenAccounts (PASS) |  |
| **REQ-TRF-02** No self-transfers | Covered | `TransferService#transfer` | cannotTransferToSameAccount (PASS) |  |
| **REQ-TRF-03** Daily transfer limit | Bug found | `TransferService#transfer` | 🆕 previousDaysTransfersDoNotCountTowardsToday (PASS)<br>🆕 transferBeyondTheDailyLimitIsRejected (FAIL)<br>🆕 transferReachingExactlyTheDailyLimitIsAccepted (FAIL) | BUG: spec says the SGD 5,000 limit is inclusive, but the code rejects when total >= limit (compareTo(...) >= 0 at TransferService.java:47), so a day total of exactly 5,000.00 is refused. Should be > 0. |
| **REQ-TRF-04** New payee review | Newly tested | `TransferService#transfer` | 🆕 firstTransferOfExactly1000CompletesImmediately (PASS)<br>🆕 knownPayeeIsNotHeld (PASS)<br>🆕 largeFirstTransferToNewPayeeIsHeldForReview (PASS) |  |
| **REQ-TRF-05** Transfer reference | Covered | `TransferService#nextReference` | everyTransferHasUniqueReferenceAndTimestamp (PASS) |  |
| **REQ-TRF-06** Transfer history | Newly tested | `TransferService#history` | 🆕 historyListsIncomingAndOutgoingNewestFirst (PASS) |  |
| **REQ-TRF-07** Scheduled transfers | Not implemented | - | - | No scheduling support: no execution date on Transfer, and no scheduler or cancel API. Would need a ScheduledTransfer entity plus @Scheduled execution in the transfers package. |

Time: SpecTrace 14.4 min vs manual estimate 660 min (30 min per requirement to locate code, find tests, write missing tests and update the matrix by hand).
