package com.auto.vision;

import com.auto.config.ClickBackend;
import com.auto.config.NavigationConfig;
import com.auto.config.PointConfig;
import com.auto.config.RegionConfig;
import com.auto.config.VisionConfig;
import com.auto.window.WindowRef;
import com.auto.window.WindowService;
import org.junit.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavigationPreflightServiceTest {
    @Test
    public void failsWhenWindowMissing() {
        NavigationPreflightService service = new NavigationPreflightService(new EmptyWindowService());
        NavigationPreflightResult result = service.run(config(), false, ClickBackend.WIN32, "");
        assertFalse(result.ready());
        assertTrue(result.items().stream().anyMatch(item ->
                "game_window".equals(item.id()) && item.level() == PreflightLevel.FAIL
        ));
    }

    @Test
    public void warnsWhenDryRunEnabled() {
        FakeWindowService windows = new FakeWindowService();
        NavigationPreflightService service = new NavigationPreflightService(
                windows,
                new OpenCvNavigationPipeline(windows, new OpenCvNavigationAnalyzer())
        );
        NavigationPreflightResult result = service.run(config(), true, ClickBackend.WIN32, "");
        assertTrue(result.items().stream().anyMatch(item ->
                "dry_run".equals(item.id()) && item.level() == PreflightLevel.WARN
        ));
    }

    private static VisionConfig config() {
        return new VisionConfig(
                "Game",
                "img/sggd/largeMap_2.bmp",
                "img/arrow_template2.bmp",
                new RegionConfig(64, 86, 84, 80),
                new PointConfig(779, 285),
                100,
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

    private static class EmptyWindowService implements WindowService {
        @Override
        public int getActiveProcessId() {
            return 0;
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
            return false;
        }

        @Override
        public void focus(WindowRef window) {
        }
    }

    private static final class FakeWindowService extends EmptyWindowService {
        @Override
        public Optional<WindowRef> findWindow(String title) {
            return Optional.of(new WindowRef(title, null, new Rectangle(0, 0, 1920, 1080)));
        }

        @Override
        public BufferedImage capture(Rectangle region) {
            return new BufferedImage(
                    Math.max(1, region.width),
                    Math.max(1, region.height),
                    BufferedImage.TYPE_3BYTE_BGR
            );
        }

        @Override
        public boolean isForeground(WindowRef window) {
            return true;
        }
    }
}
