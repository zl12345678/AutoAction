package com.auto.input;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MouseMoveResultTest {
    @Test
    public void succeededWithinTolerance() {
        MouseMoveResult result = new MouseMoveResult(100, 200, 110, 208, true, false, 12.8);
        assertTrue(result.succeeded(16));
        assertFalse(result.succeeded(10));
    }

    @Test
    public void diagnosticIncludesRequestedAndActualPoints() {
        MouseMoveResult result = new MouseMoveResult(821, 646, 2358, 573, true, true, 1539.0);
        String diagnostic = result.diagnostic();
        assertTrue(diagnostic.contains("821,646"));
        assertTrue(diagnostic.contains("2358,573"));
        assertTrue(diagnostic.contains("1539px"));
    }
}
