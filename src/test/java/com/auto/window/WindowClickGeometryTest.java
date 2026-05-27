package com.auto.window;

import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WindowClickGeometryTest {
    @Test
    public void pushesClickAwayFromCenterWhenTooClose() {
        Rectangle client = new Rectangle(100, 100, 800, 600);
        Point center = new Point(500, 400);
        Point nearCenter = new Point(510, 405);

        Point prepared = WindowClickGeometry.prepareScreenClick(nearCenter, client);

        double distance = Math.hypot(prepared.getX() - center.getX(), prepared.getY() - center.getY());
        assertTrue(distance >= 119.0);
    }

    @Test
    public void clampsClickInsideClientBounds() {
        Rectangle client = new Rectangle(100, 100, 200, 200);
        Point outside = new Point(50, 50);

        Point prepared = WindowClickGeometry.prepareScreenClick(outside, client);

        assertTrue(prepared.getX() >= client.x + 8);
        assertTrue(prepared.getY() >= client.y + 8);
        assertTrue(prepared.getX() <= client.x + client.width - 8);
        assertTrue(prepared.getY() <= client.y + client.height - 8);
    }
}
