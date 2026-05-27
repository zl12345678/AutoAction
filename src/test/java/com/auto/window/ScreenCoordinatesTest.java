package com.auto.window;

import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenCoordinatesTest {
    @Test
    public void convertsWindowLocalPointToScreenAbsolute() {
        Rectangle window = new Rectangle(1595, 91, 1365, 1024);
        Point local = new Point(775, 625);

        Point absolute = ScreenCoordinates.toAbsoluteScreenPoint(local, window);

        assertEquals(2370, absolute.x);
        assertEquals(716, absolute.y);
    }

    @Test
    public void detectsWindowLocalPoint() {
        Rectangle window = new Rectangle(1595, 91, 1365, 1024);
        assertTrue(ScreenCoordinates.isWindowLocalPoint(new Point(775, 625), window));
        assertFalse(ScreenCoordinates.isWindowLocalPoint(new Point(2403, 738), window));
    }

    @Test
    public void keepsAlreadyAbsolutePoint() {
        Rectangle window = new Rectangle(1595, 91, 1365, 1024);
        Point absolute = new Point(2403, 738);

        Point result = ScreenCoordinates.toAbsoluteScreenPoint(absolute, window);

        assertEquals(2403, result.x);
        assertEquals(738, result.y);
    }

    @Test
    public void convertsLocalPointWhenWindowAtOrigin() {
        Rectangle window = new Rectangle(0, 0, 1365, 1024);
        Point local = new Point(775, 625);

        Point absolute = ScreenCoordinates.toAbsoluteScreenPoint(local, window);

        assertEquals(775, absolute.x);
        assertEquals(625, absolute.y);
    }
}
