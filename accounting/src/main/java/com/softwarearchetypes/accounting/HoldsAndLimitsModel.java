package com.softwarearchetypes.accounting;

/**
 * Practical homework: limits and holds modeled only with existing accounting primitives.
 *
 * <p>Model assumptions:
 * <ul>
 *     <li>No new domain entities (no Limit/Reservation/Hold tables).</li>
 *     <li>All state is encoded as balances and entries on accounts.</li>
 *     <li>Traceability is achieved through transaction type + metadata + appliedTo allocation.</li>
 * </ul>
 *
 * <p>Accounts (classes of accounts):
 * <ul>
 *     <li>CUSTOMER_AVAILABLE (ASSET) - spendable funds.</li>
 *     <li>CUSTOMER_BLOCKED (ASSET) - blocked but not settled funds.</li>
 *     <li>MERCHANT_SETTLEMENT (ASSET/LIABILITY depending on chart) - final settlement leg.</li>
 *     <li>DAILY_LIMIT_USED (OFF_BALANCE) - append-only consumption trail for daily limit.</li>
 *     <li>MONTHLY_LIMIT_USED (OFF_BALANCE) - append-only consumption trail for monthly limit.</li>
 *     <li>LIMIT_EXCEEDED_AUDIT (OFF_BALANCE) - attempts/exceed events (audit trail).</li>
 *     <li>LIMIT_RENEWAL_AUDIT (OFF_BALANCE) - limit reset/renewal events.</li>
 * </ul>
 *
 * <p>Transaction types:
 * <ul>
 *     <li>HOLD_CREATED - block amount before settlement.</li>
 *     <li>HOLD_SETTLED - settle part/all of blocked amount (can use appliedTo).</li>
 *     <li>HOLD_RELEASED - release blocked amount back to available funds.</li>
 *     <li>HOLD_EXPIRED - expiration handled with EXPIRATION_COMPENSATION transaction.</li>
 *     <li>LIMIT_CONSUMED_DAILY / LIMIT_CONSUMED_MONTHLY - usage counters.</li>
 *     <li>LIMIT_RENEWED - reset/renewal checkpoint in ledger.</li>
 *     <li>LIMIT_EXCEEDED_ATTEMPT - explicit registration of failed/over-limit attempt.</li>
 * </ul>
 *
 * <p>Ledger read-model ideas:
 * <ul>
 *     <li>Available funds: balance(CUSTOMER_AVAILABLE).</li>
 *     <li>Blocked funds: balance(CUSTOMER_BLOCKED).</li>
 *     <li>Daily/monthly usage: sum entries in a time window on dedicated OFF_BALANCE accounts.</li>
 *     <li>Active holds: credits on CUSTOMER_BLOCKED minus debits allocated to those credits.</li>
 *     <li>Exceeded attempts: entries on LIMIT_EXCEEDED_AUDIT.</li>
 * </ul>
 */
public final class HoldsAndLimitsModel {

	private HoldsAndLimitsModel() {
	}

	public static final String HOLD_CREATED = "hold_created";
	public static final String HOLD_SETTLED = "hold_settled";
	public static final String HOLD_RELEASED = "hold_released";
	public static final String LIMIT_CONSUMED_DAILY = "limit_consumed_daily";
	public static final String LIMIT_CONSUMED_MONTHLY = "limit_consumed_monthly";
	public static final String LIMIT_RENEWED = "limit_renewed";
	public static final String LIMIT_EXCEEDED_ATTEMPT = "limit_exceeded_attempt";
}

