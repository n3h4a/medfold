package com.medfold.records;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LabFlagCalculatorTest {

    @ParameterizedTest(name = "{0} vs \"{1}\" → {2}")
    @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
            13.5   | 13.0 - 17.0        | NORMAL
            12.9   | 13.0 - 17.0        | LOW
            17.1   | 13.0-17.0          | HIGH
            13.0   | 13.0 – 17.0        | NORMAL
            17.0   | 13.0 to 17.0       | NORMAL
            6.1    | 4.0 - 5.6 %        | HIGH
            7,400  | 4,000 - 11,000     | NORMAL
            12,500 | 4000 - 11000 /cumm | HIGH
            5,6    | 4,0 - 5,5          | HIGH
            .8     | 0.5 - 1.2 mg/dL    | NORMAL
            180    | < 200              | NORMAL
            200    | < 200              | HIGH
            200    | <= 200             | NORMAL
            201    | Up to 200          | HIGH
            199    | less than 200      | NORMAL
            45     | > 40               | NORMAL
            40     | > 40               | LOW
            40     | >= 40              | NORMAL
            39     | ≥ 40               | LOW
            -1.5   | -2.0 - 2.0         | NORMAL
            -2.5   | -2.0 - 2.0         | LOW
            13.5*  | 13.0 - 17.0        | NORMAL
            """)
    void comparesNumbersWithThePrintedRange(String value, String range, LabFlag expected) {
        assertThat(LabFlagCalculator.compare(value, range).flag()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} vs \"{1}\" → no flag")
    @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
            Negative | Negative
            <0.5     | 0 - 1
            13.5 H   | 13.0 - 17.0
            1/80     | < 1/40
            13.5     | null
            13.5     | ''
            13.5     | M: 13-17 F: 12-15
            13.5     | 17.0 - 13.0
            13.5     | See note
            null     | 13.0 - 17.0
            """)
    void anythingUncertainGetsNoFlag(String value, String range) {
        assertThat(LabFlagCalculator.compare(value, range).flag()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            7,400     | 7400
            1,234,567 | 1234567
            5,6       | 5.6
            0.05      | 0.05
            +3        | 3
            """)
    void parsesPrintedNumbers(String text, BigDecimal expected) {
        assertThat(LabFlagCalculator.parseValue(text)).isEqualByComparingTo(expected);
    }

    @Test
    void keepsTheParsedNumbers() {
        LabFlagCalculator.Comparison c = LabFlagCalculator.compare("6.1", "4.0 - 5.6");

        assertThat(c.value()).isEqualByComparingTo("6.1");
        assertThat(c.low()).isEqualByComparingTo("4.0");
        assertThat(c.high()).isEqualByComparingTo("5.6");

        LabFlagCalculator.Comparison upperOnly = LabFlagCalculator.compare("180", "< 200");
        assertThat(upperOnly.low()).isNull();
        assertThat(upperOnly.high()).isEqualByComparingTo("200");

        LabFlagCalculator.Comparison qualitative = LabFlagCalculator.compare("Negative", "0 - 1");
        assertThat(qualitative.value()).isNull();
        assertThat(qualitative.low()).isEqualByComparingTo("0");
    }
}
