package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;

public class OutageRoadConstraintTest {
    @Test public void requiresRepeatedHighTrustOnSameEdgeAndNeverChangesEdgeDuringOutage() {
        OutageRoadConstraint gate=new OutageRoadConstraint();
        assertFalse(gate.accept(1_000_000_000L,7,.65,2,5));
        assertTrue(gate.accept(3_000_000_000L,7,.65,2,5));
        assertFalse(gate.accept(5_000_000_000L,8,.65,2,5));
        assertFalse(gate.accept(7_000_000_000L,8,.65,2,5));
        gate.reset();
        assertFalse(gate.accept(9_000_000_000L,8,.65,2,5));
        assertTrue(gate.accept(11_000_000_000L,8,.65,2,5));
    }
    @Test public void weakOffsetOppositeAndStaleMatchesCannotConstrainHeading() {
        OutageRoadConstraint gate=new OutageRoadConstraint();
        assertFalse(gate.accept(1_000_000_000L,7,.4,2,5));
        assertFalse(gate.accept(2_000_000_000L,7,.65,20,5));
        assertFalse(gate.accept(3_000_000_000L,7,.65,2,175));
        assertFalse(gate.accept(4_000_000_000L,7,.65,2,5));
        assertFalse(gate.accept(4_000_000_000L,7,.65,2,5));
        assertFalse(gate.accept(9_000_000_000L,7,.65,2,5));
    }
}
