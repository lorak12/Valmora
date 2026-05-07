package org.nakii.valmora.module.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoinExpressionParserTest {

    private static double parse(String input) {
        return CoinExpressionParser.parse(input);
    }

    @Test void plain() { assertEquals(1000.0, parse("1000"), 0.001); }
    @Test void suffixK() { assertEquals(2500.0, parse("2.5k"), 0.001); }
    @Test void suffixKUpper() { assertEquals(2000.0, parse("2K"), 0.001); }
    @Test void suffixM() { assertEquals(1_000_000.0, parse("1m"), 0.001); }
    @Test void suffixB() { assertEquals(1_000_000_000.0, parse("1b"), 0.001); }
    @Test void addK() { assertEquals(1500.0, parse("1k+500"), 0.001); }
    @Test void subtractFromK() { assertEquals(2999.0, parse("3k-1"), 0.001); }
    @Test void addMixedSuffix() { assertEquals(1_500_000.0, parse("1m+500k"), 0.001); }
    @Test void multiply() { assertEquals(6000.0, parse("2k*3"), 0.001); }
    @Test void divide() { assertEquals(100_000.0, parse("1m/10"), 0.001); }
    @Test void parens() { assertEquals(3000.0, parse("(1k+500)*2"), 0.001); }
    @Test void emptyString() { assertEquals(0.0, parse(""), 0.0); }
    @Test void nullInput() { assertEquals(0.0, parse(null), 0.0); }
    @Test void nonNumeric() { assertEquals(0.0, parse("abc"), 0.0); }
    @Test void spacesAreIgnored() { assertEquals(2500.0, parse(" 2.5k "), 0.001); }
}
