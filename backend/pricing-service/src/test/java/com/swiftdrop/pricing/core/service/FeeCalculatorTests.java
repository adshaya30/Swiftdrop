package com.swiftdrop.pricing.core.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("TST-010")
class FeeCalculatorTests {
    private final FeeCalculator calculator = new FeeCalculator();
    private static BigDecimal n(String value) { return new BigDecimal(value); }

    @ParameterizedTest
    @CsvSource({"0.01,0", "1.00,0", "1.01,150", "2.00,150", "30.00,4350"})
    @DisplayName("TST-010 BR-08: first kilogram included, subsequent kilograms rounded up")
    void weightBoundaries(String weight, String expected) {
        var fees = calculator.calculate(n(weight), n("500"), n("150"), n("0"), n("0"));
        assertThat(fees.weightFee()).isEqualByComparingTo(expected);
        assertThat(fees.total()).isEqualByComparingTo(n("500").add(n(expected)));
        assertThat(fees.total().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"0,800,0", "10,720,80", "50,400,400"})
    @DisplayName("TST-010 BR-08/13: discounts use the complete subtotal")
    void discountBoundaries(String discount, String total, String amount) {
        var fees = calculator.calculate(n("2"), n("500"), n("150"), n("150"), n(discount));
        assertThat(fees.subtotal()).isEqualByComparingTo("800.00");
        assertThat(fees.total()).isEqualByComparingTo(total);
        assertThat(fees.discountAmount()).isEqualByComparingTo(amount);
    }

    @ParameterizedTest
    @CsvSource({"1.01,50,0.51,0.50", "1.03,50,0.52,0.51", "1.00,0.5,1.00,0.00", "1.00,0.51,0.99,0.01"})
    @DisplayName("TST-010 BR-08: HALF_UP once at the end; discount is subtotal minus rounded total")
    void halfUpRounding(String base, String discount, String total, String amount) {
        var fees = calculator.calculate(n("1"), n(base), n("0"), n("0"), n(discount));
        assertThat(fees.total()).isEqualByComparingTo(total);
        assertThat(fees.discountAmount()).isEqualByComparingTo(amount);
        assertThat(fees.subtotal().subtract(fees.total())).isEqualByComparingTo(fees.discountAmount());
    }

    @ParameterizedTest
    @CsvSource({"150,800", "0,650"})
    @DisplayName("TST-010 AT-05: Colombo to Kandy, 2 kg, MEDIUM/SMALL")
    void acceptance05(String surcharge, String total) {
        assertThat(calculator.calculate(n("2"), n("500"), n("150"), n(surcharge), n("0")).total())
                .isEqualByComparingTo(total);
    }

    @ParameterizedTest
    @CsvSource({"1.00,350", "1.01,450"})
    @DisplayName("TST-010 AT-06: Colombo to Gampaha, SMALL")
    void acceptance06(String weight, String total) {
        assertThat(calculator.calculate(n(weight), n("350"), n("100"), n("0"), n("0")).total())
                .isEqualByComparingTo(total);
    }

    @Test
    @DisplayName("TST-010 AT-07: Colombo to Jaffna, 5 kg LARGE, ten percent discount")
    void acceptance07() {
        var fees = calculator.calculate(n("5"), n("800"), n("150"), n("400"), n("10"));
        assertThat(fees.subtotal()).isEqualByComparingTo("1800.00");
        assertThat(fees.discountAmount()).isEqualByComparingTo("180.00");
        assertThat(fees.total()).isEqualByComparingTo("1620.00");
    }
}
