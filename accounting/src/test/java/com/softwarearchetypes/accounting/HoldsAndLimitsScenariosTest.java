package com.softwarearchetypes.accounting;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.softwarearchetypes.common.Result;
import com.softwarearchetypes.quantity.money.Money;

import static com.softwarearchetypes.quantity.money.Money.pln;
import static java.time.Clock.fixed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldsAndLimitsScenariosTest {

    static final ZoneId ZONE = ZoneId.systemDefault();
    static final Instant DAY_1_10_00 = LocalDateTime.of(2026, 3, 10, 10, 0).atZone(ZONE).toInstant();
    static final Instant DAY_1_10_05 = LocalDateTime.of(2026, 3, 10, 10, 5).atZone(ZONE).toInstant();
    static final Instant DAY_1_10_10 = LocalDateTime.of(2026, 3, 10, 10, 10).atZone(ZONE).toInstant();
    static final Instant DAY_1_10_20 = LocalDateTime.of(2026, 3, 10, 10, 20).atZone(ZONE).toInstant();
    static final Instant DAY_2_09_00 = LocalDateTime.of(2026, 3, 11, 9, 0).atZone(ZONE).toInstant();
    static final Instant DAY_2_10_00 = LocalDateTime.of(2026, 3, 11, 10, 0).atZone(ZONE).toInstant();
    static final Instant DAY_2_11_00 = LocalDateTime.of(2026, 3, 11, 11, 0).atZone(ZONE).toInstant();
    static final Instant DAY_3_10_00 = LocalDateTime.of(2026, 3, 12, 10, 0).atZone(ZONE).toInstant();
    static final Instant DAY_4_10_00 = LocalDateTime.of(2026, 3, 13, 10, 0).atZone(ZONE).toInstant();
    static final Instant NOW = LocalDateTime.of(2026, 3, 13, 12, 0).atZone(ZONE).toInstant();

    AccountingFacade facade = AccountingConfiguration.inMemory(fixed(NOW, ZONE)).facade();

    @Test
    void should_handle_hold_partial_settlement_and_release_with_traceability() {
        AccountId treasury = createAssetAccount("Treasury");
        AccountId available = createAssetAccount("Customer Available");
        AccountId blocked = createAssetAccount("Customer Blocked");
        AccountId merchantSettlement = createAssetAccount("Merchant Settlement");

        assertTrue(facade.transfer(treasury, available, pln(1000), DAY_1_10_00, DAY_1_10_00).success());

        Transaction hold = facade.transaction()
                .occurredAt(DAY_1_10_05)
                .appliesAt(DAY_1_10_05)
                .withTypeOf(HoldsAndLimitsModel.HOLD_CREATED)
                .withMetadata("customerId", "C-100", "reason", "card_authorization")
                .executing()
                .debitFrom(available, pln(300))
                .creditTo(blocked, pln(300))
                .build();
        assertTrue(facade.execute(hold).success());

        EntryId holdEntryOnBlocked = creditEntryIdFor(hold, blocked);

        Transaction settlement = facade.transaction()
                .occurredAt(DAY_1_10_10)
                .appliesAt(DAY_1_10_10)
                .withTypeOf(HoldsAndLimitsModel.HOLD_SETTLED)
                .withMetadata("holdTxId", hold.id().toString(), "merchant", "M-1")
                .executing()
                .debitFrom(blocked, pln(200), holdEntryOnBlocked)
                .creditTo(merchantSettlement, pln(200))
                .build();

        Transaction release = facade.transaction()
                .occurredAt(DAY_1_10_20)
                .appliesAt(DAY_1_10_20)
                .withTypeOf(HoldsAndLimitsModel.HOLD_RELEASED)
                .withMetadata("holdTxId", hold.id().toString(), "reason", "partial_capture")
                .executing()
                .debitFrom(blocked, pln(100), holdEntryOnBlocked)
                .creditTo(available, pln(100))
                .build();

        assertTrue(facade.execute(settlement, release).success());

        assertThat(facade.balance(available)).hasValue(pln(800));
        assertThat(facade.balance(blocked)).hasValue(pln(0));
        assertThat(facade.balance(merchantSettlement)).hasValue(pln(200));

        Entry settlementDebitEntry = debitEntryFor(settlement, blocked);
        assertThat(settlementDebitEntry.appliedTo()).contains(holdEntryOnBlocked);
    }

    @Test
    void should_compensate_expired_hold_and_keep_audit_history() {
        AccountId treasury = createAssetAccount("Treasury");
        AccountId available = createAssetAccount("Customer Available");
        AccountId blocked = createAssetAccount("Customer Blocked");

        assertTrue(facade.transfer(treasury, available, pln(500), DAY_1_10_00, DAY_1_10_00).success());

        Validity holdValidity = Validity.until(DAY_3_10_00);

        Transaction expiringHold = facade.transaction()
                .occurredAt(DAY_1_10_05)
                .appliesAt(DAY_1_10_05)
                .withTypeOf(HoldsAndLimitsModel.HOLD_CREATED)
                .executing()
                .debitFrom(available, pln(150), holdValidity)
                .creditTo(blocked, pln(150), holdValidity)
                .build();

        assertTrue(facade.execute(expiringHold).success());

        EntryId blockedHoldEntry = creditEntryIdFor(expiringHold, blocked);

        Transaction expirationCompensation = facade.transaction()
                .occurredAt(DAY_4_10_00)
                .appliesAt(DAY_4_10_00)
                .compensatingExpired(blockedHoldEntry)
                .withCompensationAccount(available)
                .build()
                .orElseThrow();

        assertTrue(facade.execute(expirationCompensation).success());

        assertThat(facade.balance(available)).hasValue(pln(500));
        assertThat(facade.balance(blocked)).hasValue(pln(0));
        assertThat(facade.findTransactionIdsFor(blocked)).hasSize(2);
    }

    @Test
    void should_track_daily_and_monthly_limit_usage_and_audit_exceeded_attempts() {
        AccountId dailyUsage = createOffBalanceAccount("Daily Limit Usage");
        AccountId monthlyUsage = createOffBalanceAccount("Monthly Limit Usage");
        AccountId renewalAudit = createOffBalanceAccount("Limit Renewal Audit");
        AccountId exceededAttempts = createOffBalanceAccount("Limit Exceeded Attempts");

        Transaction day1UsageA = limitUsageTransaction(HoldsAndLimitsModel.LIMIT_CONSUMED_DAILY, dailyUsage, pln(200), DAY_1_10_00);
        Transaction day1UsageB = limitUsageTransaction(HoldsAndLimitsModel.LIMIT_CONSUMED_DAILY, dailyUsage, pln(300), DAY_1_10_10);
        Transaction day1MonthlyA = limitUsageTransaction(HoldsAndLimitsModel.LIMIT_CONSUMED_MONTHLY, monthlyUsage, pln(200), DAY_1_10_00);
        Transaction day1MonthlyB = limitUsageTransaction(HoldsAndLimitsModel.LIMIT_CONSUMED_MONTHLY, monthlyUsage, pln(300), DAY_1_10_10);

        Transaction renewal = facade.transaction()
                .occurredAt(DAY_2_09_00)
                .appliesAt(DAY_2_09_00)
                .withTypeOf(HoldsAndLimitsModel.LIMIT_RENEWED)
                .withMetadata("scope", "daily", "limit", "1000")
                .executing()
                .creditTo(renewalAudit, pln(1000))
                .build();

        Transaction day2Usage = limitUsageTransaction(HoldsAndLimitsModel.LIMIT_CONSUMED_DAILY, dailyUsage, pln(400), DAY_2_10_00);
        Transaction day2Monthly = limitUsageTransaction(HoldsAndLimitsModel.LIMIT_CONSUMED_MONTHLY, monthlyUsage, pln(400), DAY_2_10_00);

        Transaction exceededAttempt = facade.transaction()
                .occurredAt(DAY_2_11_00)
                .appliesAt(DAY_2_11_00)
                .withTypeOf(HoldsAndLimitsModel.LIMIT_EXCEEDED_ATTEMPT)
                .withMetadata("scope", "daily", "attemptedAmount", "1200", "limit", "1000")
                .executing()
                .creditTo(exceededAttempts, pln(1200))
                .build();

        Result<String, java.util.Set<TransactionId>> execution = facade.execute(
                day1UsageA,
                day1UsageB,
                day1MonthlyA,
                day1MonthlyB,
                renewal,
                day2Usage,
                day2Monthly,
                exceededAttempt
        );

        assertTrue(execution.success());

        Money day1DailyUsage = sumPositiveEntriesInWindow(dailyUsage, DAY_1_10_00, DAY_2_09_00);
        Money day2DailyUsage = sumPositiveEntriesInWindow(dailyUsage, DAY_2_09_00, DAY_3_10_00);
        Money monthlyUsageInWindow = sumPositiveEntriesInWindow(monthlyUsage, DAY_1_10_00, DAY_3_10_00);

        assertThat(day1DailyUsage).isEqualTo(pln(500));
        assertThat(day2DailyUsage).isEqualTo(pln(400));
        assertThat(monthlyUsageInWindow).isEqualTo(pln(900));
        assertThat(facade.balance(exceededAttempts)).hasValue(pln(1200));
    }

    @Test
    void should_reverse_settlement_to_preserve_recoverability() {
        AccountId source = createAssetAccount("Source");
        AccountId destination = createAssetAccount("Destination");

        assertTrue(facade.transfer(source, destination, pln(250), DAY_1_10_00, DAY_1_10_00).success());

        Transaction settlement = facade.transaction()
                .occurredAt(DAY_1_10_10)
                .appliesAt(DAY_1_10_10)
                .withTypeOf(HoldsAndLimitsModel.HOLD_SETTLED)
                .executing()
                .debitFrom(destination, pln(100))
                .creditTo(source, pln(100))
                .build();

        Result<String, TransactionId> settlementResult = facade.execute(settlement);
        assertTrue(settlementResult.success());

        Result<String, TransactionId> reverseResult = facade.handle(
                new ReverseTransactionCommand(settlementResult.getSuccess().value(), DAY_1_10_20, DAY_1_10_20)
        );

        assertTrue(reverseResult.success());
        assertThat(facade.balance(source)).hasValue(pln(-250));
        assertThat(facade.balance(destination)).hasValue(pln(250));
        assertThat(facade.findTransactionBy(reverseResult.getSuccess())).get().extracting(TransactionView::refId)
                .isEqualTo(settlementResult.getSuccess());
    }

    private Transaction limitUsageTransaction(String type, AccountId usageAccount, Money amount, Instant time) {
        return facade.transaction()
                .occurredAt(time)
                .appliesAt(time)
                .withTypeOf(type)
                .executing()
                .creditTo(usageAccount, amount)
                .build();
    }

    private Money sumPositiveEntriesInWindow(AccountId accountId, Instant fromInclusive, Instant toExclusive) {
        AccountView account = facade.findAccount(accountId).orElseThrow();
        return account.entries().stream()
                .filter(entry -> !entry.appliesAt().isBefore(fromInclusive) && entry.appliesAt().isBefore(toExclusive))
                .map(EntryView::amount)
                .filter(amount -> !amount.isNegative() && !amount.isZero())
                .reduce(Money.zeroPln(), Money::add);
    }

    private EntryId creditEntryIdFor(Transaction transaction, AccountId accountId) {
        return transaction.entries().entrySet().stream()
                .filter(entry -> entry.getKey().id().equals(accountId))
                .flatMap(entry -> entry.getValue().stream())
                .filter(entry -> entry instanceof AccountCredited)
                .map(Entry::id)
                .findFirst()
                .orElseThrow();
    }

    private Entry debitEntryFor(Transaction transaction, AccountId accountId) {
        return transaction.entries().entrySet().stream()
                .filter(entry -> entry.getKey().id().equals(accountId))
                .flatMap(entry -> entry.getValue().stream())
                .filter(entry -> entry instanceof AccountDebited)
                .findFirst()
                .orElseThrow();
    }

    private AccountId createAssetAccount(String name) {
        AccountId id = AccountId.generate();
        assertTrue(facade.createAccount(CreateAccount.generateAssetAccount(id, name)).success());
        return id;
    }

    private AccountId createOffBalanceAccount(String name) {
        AccountId id = AccountId.generate();
        assertTrue(facade.createAccount(CreateAccount.generateOffBalanceAccount(id, name)).success());
        return id;
    }
}



