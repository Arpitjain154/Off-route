package com.idr.navigator.engine;

/**
 * Confirms an abrupt loss of satellites used in a fix before the ordinary fix-age
 * timeout. Inputs must come from used-in-fix status, not tracked measurements.
 * All event timestamps share a monotonic nanosecond clock.
 */
public final class GnssLossDetector {
    private static final long CONFIRMATION_NS=300_000_000L;
    private static final long MAX_CALLBACK_GAP_NS=2_000_000_000L;
    private long lastEventNs,pendingNs,lastSuspiciousNs,evidenceNs,detectedNs;
    private boolean hadHealthySignal;
    private String reason="none";

    /** Returns the latched loss state; only an accepted fresh fix clears it. */
    public boolean used(long nowNs,int count,double meanCn0) {
        if(nowNs<=0||nowNs<=lastEventNs)return detected();
        lastEventNs=nowNs;
        if(detected())return true;
        boolean validCn0=Double.isFinite(meanCn0)&&meanCn0>=0;
        if(count>=4&&validCn0&&meanCn0>=20)hadHealthySignal=true;
        boolean collapse=hadHealthySignal&&(count==0||count==1&&validCn0&&meanCn0<15);
        if(!collapse){clearPending();return false;}
        if(pendingNs==0||nowNs-lastSuspiciousNs>MAX_CALLBACK_GAP_NS) {
            pendingNs=lastSuspiciousNs=nowNs;return false;
        }
        lastSuspiciousNs=nowNs;
        if(nowNs-pendingNs<CONFIRMATION_NS)return false;
        evidenceNs=pendingNs;detectedNs=nowNs;reason="satellite-collapse";
        clearPending();return true;
    }

    /** The caller passes receipt time after validating a fresh trusted fix. */
    public void acceptedFix(long nowNs) {
        if(nowNs<=0||nowNs<lastEventNs)return;
        lastEventNs=nowNs;clearPending();evidenceNs=detectedNs=0;reason="none";
        // A fix resets loss evidence, not the prior healthy satellite baseline.
    }

    /** A provider-disabled callback is authoritative and needs no confirmation. */
    public boolean providerDisabled(long nowNs) {
        if(nowNs<=0||nowNs<lastEventNs)return detected();
        lastEventNs=nowNs;
        if(!detected()) {
            evidenceNs=detectedNs=nowNs;reason="provider-disabled";clearPending();
        }
        return true;
    }

    public boolean detected(){return detectedNs>0;}
    public String reason(){return reason;}
    public long evidenceNs(){return evidenceNs;}
    public long detectedNs(){return detectedNs;}
    /** Confirmation latency; publish/dispatch delay belongs to the caller. */
    public double latencyMs(){return detected()?(detectedNs-evidenceNs)*1e-6:0;}
    private void clearPending(){pendingNs=lastSuspiciousNs=0;}
}
