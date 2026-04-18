package com.lorevault.api.timeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("TriadRelationInverter")
class TriadRelationInverterTest {

    @Test
    void mapsBeforeToAfter() {
        assertThat(TriadRelationInverter.invertPrevToCurr("R:temporal.before")).isEqualTo("R:temporal.after");
    }

    @Test
    void mapsAfterToBefore() {
        assertThat(TriadRelationInverter.invertPrevToCurr("R:temporal.after")).isEqualTo("R:temporal.before");
    }

    @Test
    void mapsMeetsToAfter() {
        assertThat(TriadRelationInverter.invertPrevToCurr("R:temporal.meets")).isEqualTo("R:temporal.after");
    }

    @Test
    void mapsOverlapsToOverlappedBy() {
        assertThat(TriadRelationInverter.invertPrevToCurr("R:temporal.overlaps")).isEqualTo("R:temporal.overlapped_by");
    }

    @Test
    void mapsContainsToDuring() {
        assertThat(TriadRelationInverter.invertPrevToCurr("R:temporal.contains")).isEqualTo("R:temporal.during");
    }

    @Test
    void mapsDuringToContains() {
        assertThat(TriadRelationInverter.invertPrevToCurr("R:temporal.during")).isEqualTo("R:temporal.contains");
    }

    @Test
    void mapsEqualsToEquals() {
        assertThat(TriadRelationInverter.invertPrevToCurr("R:temporal.equals")).isEqualTo("R:temporal.equals");
    }

    @Test
    void unknownYieldsNull() {
        assertThat(TriadRelationInverter.invertPrevToCurr("R:temporal.unknown")).isNull();
    }
}
