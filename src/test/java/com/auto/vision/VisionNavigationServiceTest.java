package com.auto.vision;

import com.auto.config.NavigationConfig;
import com.auto.config.PointConfig;
import com.auto.config.RegionConfig;
import com.auto.config.VisionConfig;
import com.auto.domain.MouseButton;
import com.auto.input.InputController;
import com.auto.window.WindowRef;
import com.auto.window.WindowService;
import org.junit.Test;
import org.opencv.core.Point;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VisionNavigationServiceTest {
    @Test
    public void dryRunDoesNotClick() {
        FakeInput input = new FakeInput();
        VisionNavigationService service = new VisionNavigationService(
                new FakeWindowService(),
                input,
                (NavigationTickClient) new FakeTickClient(false, false)
        );

        assertTrue(service.runOnce(() -> config(), true));
        service.close();

        assertTrue(input.events.isEmpty());
    }

    @Test
    public void nonDryRunClicksPlannedScreenPoint() {
        FakeInput input = new FakeInput();
        FakeWindowService windows = new FakeWindowService();
        VisionNavigationService service = new VisionNavigationService(
                windows,
                input,
                (NavigationTickClient) new FakeTickClient(false, false)
        );

        assertTrue(service.runOnce(() -> config(), false));
        service.close();

        assertTrue(windows.focused);
    }

    @Test
    public void arrivedStepStopsWithoutClicking() {
        FakeInput input = new FakeInput();
        VisionNavigationService service = new VisionNavigationService(
                new FakeWindowService(),
                input,
                (NavigationTickClient) new FakeTickClient(true, false)
        );

        assertTrue(service.runOnce(() -> config(), false));
        service.close();

        assertTrue(input.events.isEmpty());
    }

    @Test
    public void movingTickSkipsRepeatedClicksInDryRun() {
        FakeInput input = new FakeInput();
        FakeTickClient tickClient = new FakeTickClient(false, false);
        VisionNavigationService service = new VisionNavigationService(
                new FakeWindowService(),
                input,
                (NavigationTickClient) tickClient
        );

        assertTrue(service.runOnce(() -> config(), true));
        tickClient.advancePosition();
        service.runOnce(() -> config(), true);
        service.close();

        assertTrue(input.events.isEmpty());
    }

    private static VisionConfig config() {
        return new VisionConfig(
                "Game",
                "map.bmp",
                "arrow.bmp",
                new RegionConfig(0, 0, 100, 100),
                new PointConfig(10, 10),
                50,
                200.0,
                80,
                10.0,
                com.auto.config.OcrConfig.disabled(),
                com.auto.config.YoloConfig.disabled(),
                com.auto.config.MapPreprocessConfig.defaults(),
                com.auto.config.MapClosureConfig.defaults(),
                NavigationConfig.defaults()
        );
    }

    private static final class FakeTickClient implements NavigationPipeline, NavigationTickClient {
        private final boolean arrived;
        private final boolean lowConfidence;
        private int tick;

        private FakeTickClient(boolean arrived, boolean lowConfidence) {
            this.arrived = arrived;
            this.lowConfidence = lowConfidence;
        }

        void advancePosition() {
            tick++;
        }

        @Override
        public Optional<NavigationStep> planNext(VisionConfig config, WindowRef window, int waypointIndex) {
            return analyzeNext(config, window, waypointIndex).flatMap(NavigationAnalysis::toNavigationStep);
        }

        @Override
        public Optional<NavigationAnalysis> analyzeNext(VisionConfig config, WindowRef window, int waypointIndex) {
            if (arrived) {
                return Optional.of(new NavigationAnalysis(
                        "fake",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new Point(10, 10),
                        new Point(10, 10),
                        new Point(10, 10),
                        null,
                        new int[][]{{10, 10}},
                        true,
                        true,
                        "arrived",
                        0.95,
                        LocalizationMethod.MAP_MATCH
                ));
            }
            double x = 10 + tick * 5.0;
            return Optional.of(new NavigationAnalysis(
                    "fake",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new Point(x, 10),
                    new Point(200, 200),
                    new Point(80, 10),
                    new Point(123, 456),
                    new int[][]{{10, 10}, {80, 10}, {200, 200}},
                    false,
                    true,
                    "moving",
                    lowConfidence ? 0.1 : 0.95,
                    LocalizationMethod.MAP_MATCH
            ));
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
        public java.awt.Point currentMousePosition() {
            return new java.awt.Point(0, 0);
        }
    }

    private static final class FakeWindowService implements WindowService {
        private boolean focused = true;

        @Override
        public int getActiveProcessId() {
            return 1;
        }

        @Override
        public Optional<WindowRef> findWindow(String title) {
            return Optional.of(new WindowRef(title, null, new Rectangle(0, 0, 800, 600)));
        }

        @Override
        public BufferedImage capture(Rectangle region) {
            return new BufferedImage(region.width, region.height, BufferedImage.TYPE_3BYTE_BGR);
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
            return focused;
        }

        @Override
        public void focus(WindowRef window) {
            focused = true;
        }
    }
}
