package com.auto.window;

import com.auto.domain.MouseButton;
import com.auto.input.InputController;
import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GameWindowClickHelperTest {
    @Test
    public void foregroundWindowUsesSingleClick() {
        FakeWindowService windows = new FakeWindowService(true);
        FakeInput input = new FakeInput();
        WindowRef window = new WindowRef("Game", null, new Rectangle(0, 0, 800, 600));

        GameWindowClickResult result = GameWindowClickHelper.click(windows, input, window, new Point(123, 456));

        assertTrue(result.foregroundAfterClicks());
        assertTrue(result.clickCount() >= 1);
        assertEquals(123, result.preparedPoint().x);
        assertEquals(456, result.preparedPoint().y);
    }

    @Test
    public void backgroundWindowUsesPrimingClick() {
        FakeWindowService windows = new FakeWindowService(false);
        FakeInput input = new FakeInput();
        WindowRef window = new WindowRef("Game", null, new Rectangle(0, 0, 800, 600));

        GameWindowClickResult result = GameWindowClickHelper.click(windows, input, window, new Point(123, 456));

        assertTrue(result.clickCount() >= 2);
        assertTrue(result.usedPrimingClick());
    }

    private static final class FakeWindowService implements WindowService {
        private boolean foreground;

        private FakeWindowService(boolean foreground) {
            this.foreground = foreground;
        }

        @Override
        public int getActiveProcessId() {
            return 1;
        }

        @Override
        public Optional<WindowRef> findWindow(String title) {
            return Optional.empty();
        }

        @Override
        public BufferedImage capture(Rectangle region) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        }

        @Override
        public Rectangle getClientBoundsOnScreen(WindowRef window) {
            return window.bounds();
        }

        @Override
        public Rectangle getWindowBoundsOnScreen(WindowRef window) {
            return window.bounds();
        }

        @Override
        public boolean isForeground(WindowRef window) {
            return foreground;
        }

        @Override
        public void focus(WindowRef window) {
            foreground = true;
        }
    }

    private static final class FakeInput implements InputController {
        private final List<String> events = new ArrayList<>();

        @Override
        public void mouseMove(int x, int y) {
            events.add("move:" + x + "," + y);
        }

        @Override
        public void mousePress(MouseButton button) {
            events.add("press:" + button);
        }

        @Override
        public void mouseRelease(MouseButton button) {
            events.add("release:" + button);
        }

        @Override
        public void keyPress(int keyCode) {
        }

        @Override
        public void keyRelease(int keyCode) {
        }

        @Override
        public void delay(int milliseconds) {
            events.add("delay:" + milliseconds);
        }

        @Override
        public Point currentMousePosition() {
            return new Point(0, 0);
        }
    }
}
