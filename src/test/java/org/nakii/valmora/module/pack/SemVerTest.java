package org.nakii.valmora.module.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemVerTest {

    @Test
    void parsesFullVersion() {
        SemVer v = SemVer.parse("1.2.3");
        assertEquals(0, v.compareTo(SemVer.parse("1.2.3")));
    }

    @Test
    void lenientlyParsesPartialVersions() {
        assertEquals(SemVer.parse("1.0.0"), SemVer.parse("1"));
        assertEquals(SemVer.parse("1.2.0"), SemVer.parse("1.2"));
    }

    @Test
    void parsesEngineStyleVersionWithPreRelease() {
        SemVer v = SemVer.parse("1.0.0-beta1");
        assertTrue(v.compareTo(SemVer.parse("1.0.0")) < 0, "pre-release must sort below the release version");
    }

    @Test
    void ignoresBuildMetadata() {
        assertEquals(SemVer.parse("1.2.3"), SemVer.parse("1.2.3+build.5"));
    }

    @Test
    void tryParseReturnsNullOnMalformedInput() {
        assertNull(SemVer.tryParse("not-a-version"));
        assertNull(SemVer.tryParse(""));
        assertNull(SemVer.tryParse(null));
        assertNull(SemVer.tryParse("1.2.3.4.5"));
    }

    @Test
    void compareToOrdersMajorMinorPatch() {
        assertTrue(SemVer.parse("2.0.0").compareTo(SemVer.parse("1.9.9")) > 0);
        assertTrue(SemVer.parse("1.3.0").compareTo(SemVer.parse("1.2.9")) > 0);
        assertTrue(SemVer.parse("1.2.4").compareTo(SemVer.parse("1.2.3")) > 0);
    }

    @Test
    void satisfiesEvaluatesEachOperator() {
        SemVer v = SemVer.parse("1.5.0");
        assertTrue(v.satisfies(">=1.0.0"));
        assertFalse(v.satisfies(">=2.0.0"));
        assertTrue(v.satisfies("<=2.0.0"));
        assertTrue(v.satisfies(">1.0.0"));
        assertTrue(v.satisfies("<2.0.0"));
        assertTrue(v.satisfies("==1.5.0"));
        assertTrue(v.satisfies("1.5.0"), "bare version implies ==");
        assertFalse(v.satisfies("==1.5.1"));
    }

    @Test
    void satisfiesRejectsUnparseableTarget() {
        SemVer v = SemVer.parse("1.0.0");
        assertThrows(IllegalArgumentException.class, () -> v.satisfies(">=not-a-version"));
    }

    @Test
    void nullOrBlankConstraintAlwaysSatisfied() {
        SemVer v = SemVer.parse("1.0.0");
        assertTrue(v.satisfies(null));
        assertTrue(v.satisfies(""));
        assertTrue(v.satisfies("  "));
    }
}
