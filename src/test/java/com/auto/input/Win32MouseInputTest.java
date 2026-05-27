package com.auto.input;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class Win32MouseInputTest {
    @Test
    public void normalizedCoordinatesStayInRange() {
        int[] normalized = Win32MouseInput.toNormalizedVirtualDesktop(826, 640);
        assertTrue(normalized[0] >= 0 && normalized[0] <= 65535);
        assertTrue(normalized[1] >= 0 && normalized[1] <= 65535);
    }
}
