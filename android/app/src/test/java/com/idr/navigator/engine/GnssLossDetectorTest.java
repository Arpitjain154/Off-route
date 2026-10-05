package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;

public class GnssLossDetectorTest {
    private static final long T=10_000_000_000L;
    private static GnssLossDetector healthy() {
        GnssLossDetector detector=new GnssLossDetector();
        assertFalse(detector.used(T,8,30));return detector;
    }
    @Test public void zeroUsedConfirmsCollapseAndMeasuresFromFirstEvidence() {
        GnssLossDetector detector=healthy();
        assertFalse(detector.used(T+1_000_000_000L,0,Double.NaN));
        assertTrue(detector.used(T+1_300_000_000L,0,0));
        assertTrue(detector.detected());assertEquals("satellite-collapse",detector.reason());
        assertEquals(T+1_000_000_000L,detector.evidenceNs());
        assertEquals(T+1_300_000_000L,detector.detectedNs());assertEquals(300,detector.latencyMs(),0);
    }
    @Test public void weakSingleSatelliteRequiresBothCountAndCn0Collapse() {
        GnssLossDetector detector=healthy();
        assertFalse(detector.used(T+1_000_000_000L,1,15));
        assertFalse(detector.used(T+2_000_000_000L,2,5));
        assertFalse(detector.used(T+3_000_000_000L,1,14));
        assertTrue(detector.used(T+3_500_000_000L,1,14));
        assertEquals(500,detector.latencyMs(),0);
    }
    @Test public void startupAndPoorInitialSignalCannotDeclareCollapse() {
        for(double cn0:new double[]{19,Double.NaN,Double.POSITIVE_INFINITY}) {
            GnssLossDetector detector=new GnssLossDetector();
            assertFalse(detector.used(T,4,cn0));
            assertFalse(detector.used(T+1_000_000_000L,0,0));
            assertFalse(detector.used(T+2_000_000_000L,0,0));
            assertFalse(detector.detected());
        }
        GnssLossDetector detector=new GnssLossDetector();detector.acceptedFix(T);
        assertFalse(detector.used(T+1_000_000_000L,0,0));
        assertFalse(detector.used(T+2_000_000_000L,0,0));
    }
    @Test public void callbackBurstCannotConfirmBeforeThreeHundredMilliseconds() {
        GnssLossDetector detector=healthy();
        assertFalse(detector.used(T+1_000_000_000L,0,0));
        assertFalse(detector.used(T+1_100_000_000L,0,0));
        assertFalse(detector.used(T+1_299_999_999L,0,0));
        assertTrue(detector.used(T+1_300_000_000L,0,0));
    }
    @Test public void recoveryBetweenSuspiciousCallbacksBreaksConsecutiveEvidence() {
        GnssLossDetector detector=healthy();
        detector.used(T+1_000_000_000L,0,0);
        assertFalse(detector.used(T+1_400_000_000L,5,27));
        assertFalse(detector.used(T+2_000_000_000L,0,0));
        assertTrue(detector.used(T+2_300_000_000L,0,0));
        assertEquals(T+2_000_000_000L,detector.evidenceNs());
    }
    @Test public void staleEvidenceCannotConfirmAndOldCallbacksCannotDisturbNewEvidence() {
        GnssLossDetector detector=healthy();
        assertFalse(detector.used(T+1_000_000_000L,0,0));
        assertFalse(detector.used(T+3_000_000_001L,0,0));
        assertFalse(detector.used(T+2_000_000_000L,8,30));
        assertFalse(detector.used(T+3_000_000_001L,0,0));
        assertTrue(detector.used(T+3_300_000_001L,0,0));
        assertEquals(T+3_000_000_001L,detector.evidenceNs());
    }
    @Test public void freshFixClearsLossButPreservesPriorHealthySignalEligibility() {
        GnssLossDetector detector=healthy();
        detector.used(T+1_000_000_000L,0,0);detector.used(T+1_400_000_000L,0,0);
        assertTrue(detector.detected());detector.acceptedFix(T+2_000_000_000L);
        assertFalse(detector.detected());assertEquals("none",detector.reason());
        assertEquals(0,detector.evidenceNs());assertEquals(0,detector.detectedNs());
        assertFalse(detector.used(T+2_100_000_000L,0,0));
        assertTrue(detector.used(T+2_400_000_000L,0,0));
    }
    @Test public void acceptedFixAlsoClearsUnconfirmedEvidence() {
        GnssLossDetector detector=healthy();
        detector.used(T+1_000_000_000L,0,0);detector.acceptedFix(T+1_200_000_000L);
        assertFalse(detector.used(T+1_400_000_000L,0,0));
        assertTrue(detector.used(T+1_700_000_000L,0,0));
        assertEquals(T+1_400_000_000L,detector.evidenceNs());
    }
    @Test public void explicitProviderDisableIsImmediateWithoutPriorSatelliteEvidence() {
        GnssLossDetector detector=new GnssLossDetector();
        assertTrue(detector.providerDisabled(T));assertTrue(detector.detected());
        assertEquals("provider-disabled",detector.reason());
        assertEquals(T,detector.evidenceNs());assertEquals(T,detector.detectedNs());
        assertEquals(0,detector.latencyMs(),0);
        detector.acceptedFix(T-1);assertTrue(detector.detected());
        detector.acceptedFix(T+1);assertFalse(detector.detected());
        assertFalse(detector.providerDisabled(T));
    }
    @Test public void healthyStatusCannotClearLatchedLossWithoutAnAcceptedFix() {
        GnssLossDetector detector=healthy();
        detector.used(T+1_000_000_000L,0,0);detector.used(T+1_400_000_000L,0,0);
        assertTrue(detector.used(T+2_000_000_000L,8,30));
        assertEquals(T+1_000_000_000L,detector.evidenceNs());
        assertEquals(400,detector.latencyMs(),0);
    }
    @Test public void invalidSamplesBreakEvidenceWithoutCreatingAnOutage() {
        GnssLossDetector detector=healthy();
        detector.used(T+1_000_000_000L,0,0);
        assertFalse(detector.used(T+1_100_000_000L,1,Double.NaN));
        assertFalse(detector.used(T+1_500_000_000L,0,0));
        assertTrue(detector.used(T+1_800_000_000L,0,0));
        assertEquals(T+1_500_000_000L,detector.evidenceNs());
    }
    @Test public void tenMinutesOfHealthyAndMarginalStatusNeverSignalsAnOutage() {
        GnssLossDetector detector=healthy();
        for(int second=1;second<=600;second++) {
            long now=T+second*1_000_000_000L;
            assertFalse(detector.used(now,second%5==0?3:8,second%5==0?18:30));
            detector.acceptedFix(now);assertFalse(detector.detected());
        }
    }
}
