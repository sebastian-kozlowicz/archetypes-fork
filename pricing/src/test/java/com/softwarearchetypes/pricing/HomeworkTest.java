package com.softwarearchetypes.pricing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.softwarearchetypes.quantity.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.softwarearchetypes.pricing.ApplicabilityConstraint.equalsTo;
import static com.softwarearchetypes.pricing.ApplicabilityConstraint.greaterThanOrEqualTo;
import static com.softwarearchetypes.pricing.ComponentBreakdownAssert.assertThat;
import static java.time.Clock.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Zadanie: uzupełnij metodę setUp() tak, aby wszystkie testy przechodziły.
 * Nie modyfikuj metod testowych.
 *
 * Scenariusz: wycena przesyłek kurierskich SwiftShip – Strefa 2.
 *
 * Trzy osie odpowiedzialności:
 *   Calculator   — jak liczymy? (progi wagowe, procenty)
 *   Validity     — kiedy obowiązuje? (wersje temporalne stawki paliwowej)
 *   Applicability— dla kogo / w jakich warunkach? (reguły zastępujące if-y)
 *
 * Oczekiwana struktura drzewa komponentów:
 *
 *   total-cost  (CompositeComponent)
 *   ├── netto   (CompositeComponent)
 *   │   ├── base-component          — cennik wagowy, mapowanie: weight → quantity
 *   │   ├── fuel-component          — 2 wersje temporalne (sty–mar 4.5%, od kwi 5.0%)
 *   │   ├── adr-component           — 50%, tylko gdy cargo-type = "hazmat"
 *   │   ├── oversized-component     — 35%, tylko gdy weight >= 30
 *   │   ├── time-window-component   — 25%, tylko gdy delivery-type = "time-window"
 *   │   ├── cod-component           — 2% od cod-value
 *   │   └── insurance-component     — 0.15% od insured-value
 *   └── vat-component               — 23% od netto
 */
class HomeworkTest {

    static final Instant NOW = LocalDateTime.of(2025, 1, 15, 12, 50).atZone(ZoneId.systemDefault()).toInstant();
    static final Clock clock = fixed(NOW, ZoneId.systemDefault());

    private PricingFacade facade = PricingConfiguration.inMemory(clock).pricingFacade();

    @BeforeEach
    void setUp() {
        // ============================================================
        // 1. CALCULATORS — cenniki (kalkulatory)
        // ============================================================

        // Unit price calculators for weight ranges (UNIT interpretation)
        Calculator unitPrice1to5 = facade.addCalculator("unit-price-1-5", CalculatorType.SIMPLE_FIXED,
                Parameters.of("amount", Money.pln(new BigDecimal("7.90")),
                        "interpretation", Interpretation.UNIT));

        Calculator unitPrice5to30 = facade.addCalculator("unit-price-5-30", CalculatorType.SIMPLE_FIXED,
                Parameters.of("amount", Money.pln(new BigDecimal("6.10")),
                        "interpretation", Interpretation.UNIT));

        Calculator unitPrice30to70 = facade.addCalculator("unit-price-30-70", CalculatorType.SIMPLE_FIXED,
                Parameters.of("amount", Money.pln(new BigDecimal("5.20")),
                        "interpretation", Interpretation.UNIT));

        // Composite calculator for weight-based pricing (delegates to UNIT price calculators)
        facade.addCalculator("base-calc", CalculatorType.COMPOSITE,
                Parameters.of(
                        "ranges", List.of(
                                CalculatorRange.numeric(new BigDecimal("1"), new BigDecimal("5"), unitPrice1to5.getId()),
                                CalculatorRange.numeric(new BigDecimal("5"), new BigDecimal("30"), unitPrice5to30.getId()),
                                CalculatorRange.numeric(new BigDecimal("30"), new BigDecimal("70"), unitPrice30to70.getId())
                        ),
                        "rangeSelector", "quantity"
                ));

        // Fuel surcharge calculators (two rates for temporal versioning)
        facade.addCalculator("fuel-calc-45", CalculatorType.PERCENTAGE,
                Parameters.of("percentageRate", new BigDecimal("4.5")));

        facade.addCalculator("fuel-calc-50", CalculatorType.PERCENTAGE,
                Parameters.of("percentageRate", new BigDecimal("5.0")));

        // ADR surcharge calculator — 50%
        facade.addCalculator("adr-calc", CalculatorType.PERCENTAGE,
                Parameters.of("percentageRate", new BigDecimal("50")));

        // Oversized surcharge calculator — 35%
        facade.addCalculator("oversized-calc", CalculatorType.PERCENTAGE,
                Parameters.of("percentageRate", new BigDecimal("35")));

        // Time-window surcharge calculator — 25%
        facade.addCalculator("time-window-calc", CalculatorType.PERCENTAGE,
                Parameters.of("percentageRate", new BigDecimal("25")));

        // COD calculator — 2%
        facade.addCalculator("cod-calc", CalculatorType.PERCENTAGE,
                Parameters.of("percentageRate", new BigDecimal("2")));

        // Insurance calculator — 0.15%
        facade.addCalculator("insurance-calc", CalculatorType.PERCENTAGE,
                Parameters.of("percentageRate", new BigDecimal("0.15")));

        // VAT calculator — 23%
        facade.addCalculator("vat-calc", CalculatorType.PERCENTAGE,
                Parameters.of("percentageRate", new BigDecimal("23")));

        // ============================================================
        // 2. COMPONENTS — komponenty cenowe
        // ============================================================

        // base-component: weight-based pricing with parameter mapping weight → quantity
        facade.createSimpleComponent("base-component", "base-calc",
                Map.of("weight", "quantity"));

        // fuel-component: version 1 (Jan–Mar 2025) — 4.5%
        facade.createSimpleComponent("fuel-component", "fuel-calc-45",
                Map.of(),
                Validity.between(
                        LocalDateTime.of(2025, 1, 1, 0, 0),
                        LocalDateTime.of(2025, 4, 1, 0, 0)));

        // fuel-component: version 2 (from Apr 2025) — 5.0%
        facade.createSimpleComponent("fuel-component", "fuel-calc-50",
                Map.of(),
                Validity.from(LocalDateTime.of(2025, 4, 1, 0, 0)));

        // adr-component: 50% only when cargo-type = "hazmat"
        facade.createSimpleComponent("adr-component", "adr-calc",
                Map.of(),
                equalsTo("cargo-type", "hazmat"));

        // oversized-component: 35% only when weight >= 30
        facade.createSimpleComponent("oversized-component", "oversized-calc",
                Map.of(),
                greaterThanOrEqualTo("weight", 30));

        // time-window-component: 25% only when delivery-type = "time-window"
        facade.createSimpleComponent("time-window-component", "time-window-calc",
                Map.of(),
                equalsTo("delivery-type", "time-window"));

        // cod-component: 2% of cod-value
        facade.createSimpleComponent("cod-component", "cod-calc",
                Map.of("cod-value", "baseAmount"));

        // insurance-component: 0.15% of insured-value
        facade.createSimpleComponent("insurance-component", "insurance-calc",
                Map.of("insured-value", "baseAmount"));

        // vat-component: 23% (baseAmount injected via dependency)
        facade.createSimpleComponent("vat-component", "vat-calc");

        // ============================================================
        // 3. COMPOSITE COMPONENTS — kompozycja cenowa
        // ============================================================

        // netto: sum of all net components
        // fuel, adr, oversized, time-window depend on base-component result as their baseAmount
        facade.createCompositeComponent(
                "netto",
                Map.of(
                        "fuel-component", Map.of("baseAmount", new ValueOf("base-component")),
                        "adr-component", Map.of("baseAmount", new ValueOf("base-component")),
                        "oversized-component", Map.of("baseAmount", new ValueOf("base-component")),
                        "time-window-component", Map.of("baseAmount", new ValueOf("base-component"))
                ),
                "base-component", "fuel-component", "adr-component",
                "oversized-component", "time-window-component",
                "cod-component", "insurance-component"
        );

        // total-cost: netto + VAT (VAT depends on netto result)
        facade.createCompositeComponent(
                "total-cost",
                Map.of(
                        "vat-component", Map.of("baseAmount", new ValueOf("netto"))
                ),
                "netto", "vat-component"
        );
    }

    // ============================================================
    // Test 1: standardowa przesyłka — tylko dopłata paliwowa
    // ============================================================

    @Test
    void shouldCalculateStandardShipmentWithFuelSurchargeAndVAT() {
        // 3 kg, towar standardowy, dostawa standardowa, bez COD, bez ubezpieczenia
        Parameters params = Parameters.of(
                "weight",         BigDecimal.valueOf(3),
                "cargo-type",     "standard",
                "delivery-type",  "standard",
                "cod-value",      Money.pln(BigDecimal.ZERO),
                "insured-value",  Money.pln(BigDecimal.ZERO))
                .with("timestamp", LocalDateTime.of(2025, 1, 20, 10, 0));

        Money result = facade.calculateComponent("total-cost", params);

        //   cena bazowa      = 3 × 7.90 = 23.70 PLN    (zakres [1, 5) kg)
        //   dopłata paliwowa = 4.5% × 23.70 = 1.07 PLN
        //   ADR              = 0  (cargo-type ≠ "hazmat")
        //   ponadgabarytowa  = 0  (3 kg < 30 kg)
        //   okno czasowe     = 0  (delivery-type ≠ "time-window")
        //   COD              = 0  (cod-value = 0)
        //   ubezpieczenie    = 0  (insured-value = 0)
        //   netto            = 24.77 PLN
        //   VAT 23%          = 5.70 PLN
        //   razem            = 30.47 PLN
        assertEquals(Money.pln(new BigDecimal("30.47")), result);

        ComponentBreakdown breakdown = facade.calculateComponentBreakdown("total-cost", params);
        assertThat(breakdown)
                .hasName("total-cost")
                .hasTotal(Money.pln(new BigDecimal("30.47")))
                .hasChildrenCount(2);

        assertThat(breakdown)
                .child("netto")
                .hasTotal(Money.pln(new BigDecimal("24.77")))
                .hasChildrenCount(7);

        assertThat(breakdown).child("netto").child("base-component")
                .hasTotal(Money.pln(new BigDecimal("23.70"))).hasNoChildren();
        assertThat(breakdown).child("netto").child("fuel-component")
                .hasTotal(Money.pln(new BigDecimal("1.07"))).hasNoChildren();

        assertThat(breakdown).child("vat-component")
                .hasTotal(Money.pln(new BigDecimal("5.70"))).hasNoChildren();
    }

    // ============================================================
    // Test 2: przesyłka ADR z COD i ubezpieczeniem
    // ============================================================

    @Test
    void shouldCalculateHazmatShipmentWithCODAndInsurance() {
        // 12 kg, materiały niebezpieczne, COD 800 PLN, ubezpieczenie 1500 PLN
        Parameters params = Parameters.of(
                "weight",         BigDecimal.valueOf(12),
                "cargo-type",     "hazmat",
                "delivery-type",  "standard",
                "cod-value",      Money.pln(BigDecimal.valueOf(800)),
                "insured-value",  Money.pln(BigDecimal.valueOf(1500)))
                .with("timestamp", LocalDateTime.of(2025, 1, 20, 10, 0));

        Money result = facade.calculateComponent("total-cost", params);

        //   cena bazowa      = 12 × 6.10 = 73.20 PLN   (zakres [5, 30) kg)
        //   dopłata paliwowa = 4.5%  × 73.20 = 3.29 PLN
        //   ADR              = 50%   × 73.20 = 36.60 PLN  (cargo-type = "hazmat" ✓)
        //   ponadgabarytowa  = 0  (12 kg < 30 kg)
        //   okno czasowe     = 0  (delivery-type ≠ "time-window")
        //   COD              = 2%    × 800  = 16.00 PLN
        //   ubezpieczenie    = 0.15% × 1500 = 2.25 PLN
        //   netto            = 73.20 + 3.29 + 36.60 + 16.00 + 2.25 = 131.34 PLN
        //   VAT 23%          = 30.21 PLN
        //   razem            = 161.55 PLN
        assertEquals(Money.pln(new BigDecimal("161.55")), result);

        ComponentBreakdown breakdown = facade.calculateComponentBreakdown("total-cost", params);
        assertThat(breakdown)
                .hasName("total-cost")
                .hasTotal(Money.pln(new BigDecimal("161.55")))
                .hasChildrenCount(2);

        assertThat(breakdown)
                .child("netto")
                .hasTotal(Money.pln(new BigDecimal("131.34")))
                .hasChildrenCount(7);

        assertThat(breakdown).child("netto").child("base-component")
                .hasTotal(Money.pln(new BigDecimal("73.20")));
        assertThat(breakdown).child("netto").child("fuel-component")
                .hasTotal(Money.pln(new BigDecimal("3.29")));
        assertThat(breakdown).child("netto").child("adr-component")
                .hasTotal(Money.pln(new BigDecimal("36.60")));
        assertThat(breakdown).child("netto").child("cod-component")
                .hasTotal(Money.pln(new BigDecimal("16.00")));
        assertThat(breakdown).child("netto").child("insurance-component")
                .hasTotal(Money.pln(new BigDecimal("2.25")));

        assertThat(breakdown).child("vat-component")
                .hasTotal(Money.pln(new BigDecimal("30.21")));
    }

    // ============================================================
    // Test 3: paczka ponadgabarytowa z dostawą w oknie czasowym
    // ============================================================

    @Test
    void shouldCalculateOversizedShipmentWithTimeWindowDelivery() {
        // 45 kg, towar standardowy, dostawa w oknie czasowym
        Parameters params = Parameters.of(
                "weight",         BigDecimal.valueOf(45),
                "cargo-type",     "standard",
                "delivery-type",  "time-window",
                "cod-value",      Money.pln(BigDecimal.ZERO),
                "insured-value",  Money.pln(BigDecimal.ZERO))
                .with("timestamp", LocalDateTime.of(2025, 1, 20, 10, 0));

        Money result = facade.calculateComponent("total-cost", params);

        //   cena bazowa      = 45 × 5.20 = 234.00 PLN  (zakres [30, 70) kg)
        //   dopłata paliwowa = 4.5%  × 234.00 = 10.53 PLN
        //   ADR              = 0  (cargo-type ≠ "hazmat")
        //   ponadgabarytowa  = 35%   × 234.00 = 81.90 PLN  (45 kg ≥ 30 kg ✓)
        //   okno czasowe     = 25%   × 234.00 = 58.50 PLN  (delivery-type = "time-window" ✓)
        //   COD              = 0  (cod-value = 0)
        //   ubezpieczenie    = 0  (insured-value = 0)
        //   netto            = 234.00 + 10.53 + 81.90 + 58.50 = 384.93 PLN
        //   VAT 23%          = 88.53 PLN
        //   razem            = 473.46 PLN
        assertEquals(Money.pln(new BigDecimal("473.46")), result);

        ComponentBreakdown breakdown = facade.calculateComponentBreakdown("total-cost", params);
        assertThat(breakdown)
                .hasName("total-cost")
                .hasTotal(Money.pln(new BigDecimal("473.46")))
                .hasChildrenCount(2);

        assertThat(breakdown)
                .child("netto")
                .hasTotal(Money.pln(new BigDecimal("384.93")))
                .hasChildrenCount(7);

        assertThat(breakdown).child("netto").child("base-component")
                .hasTotal(Money.pln(new BigDecimal("234.00")));
        assertThat(breakdown).child("netto").child("fuel-component")
                .hasTotal(Money.pln(new BigDecimal("10.53")));
        assertThat(breakdown).child("netto").child("oversized-component")
                .hasTotal(Money.pln(new BigDecimal("81.90")));
        assertThat(breakdown).child("netto").child("time-window-component")
                .hasTotal(Money.pln(new BigDecimal("58.50")));

        assertThat(breakdown).child("vat-component")
                .hasTotal(Money.pln(new BigDecimal("88.53")));
    }

    // ============================================================
    // Test 4: zmiana stawki paliwowej od 1 kwietnia (validity)
    // ============================================================

    @Test
    void shouldApplyFuelRateChangeTemporallyFrom1April() {
        // Ta sama przesyłka 3 kg, ale obliczona w różnych terminach.
        // Timestamp decyduje, która wersja fuel-component jest aktywna.

        Parameters jan = Parameters.of(
                "weight",         BigDecimal.valueOf(3),
                "cargo-type",     "standard",
                "delivery-type",  "standard",
                "cod-value",      Money.pln(BigDecimal.ZERO),
                "insured-value",  Money.pln(BigDecimal.ZERO))
                .with("timestamp", LocalDateTime.of(2025, 1, 20, 10, 0));

        Parameters apr = Parameters.of(
                "weight",         BigDecimal.valueOf(3),
                "cargo-type",     "standard",
                "delivery-type",  "standard",
                "cod-value",      Money.pln(BigDecimal.ZERO),
                "insured-value",  Money.pln(BigDecimal.ZERO))
                .with("timestamp", LocalDateTime.of(2025, 4, 15, 10, 0));

        // styczeń: stawka paliwowa 4.5%
        //   cena bazowa  = 3 × 7.90 = 23.70 PLN
        //   dopłata 4.5% = 1.07 PLN
        //   netto        = 24.77 PLN  |  VAT = 5.70 PLN  |  razem = 30.47 PLN
        assertEquals(Money.pln(new BigDecimal("30.47")),
                facade.calculateComponent("total-cost", jan));

        // kwiecień: stawka paliwowa 5.0%
        //   cena bazowa  = 3 × 7.90 = 23.70 PLN
        //   dopłata 5.0% = 1.19 PLN
        //   netto        = 24.89 PLN  |  VAT = 5.72 PLN  |  razem = 30.61 PLN
        assertEquals(Money.pln(new BigDecimal("30.61")),
                facade.calculateComponent("total-cost", apr));
    }
}