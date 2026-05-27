package com.auto.config;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AppConfigWriterTest {
    @Test
    public void savesMiniMapRegionToLocalConfig() throws Exception {
        Path tempDir = Files.createTempDirectory("autoaction-config");
        Path configPath = tempDir.resolve("autoActionConfig.json");
        Files.writeString(configPath, """
                {
                  "system": {"dryRun": true},
                  "vision": {
                    "windowTitle": "Test",
                    "mapImage": "img/map.bmp",
                    "arrowTemplate": "img/arrow.bmp",
                    "miniMapRegion": {"x": 0, "y": 0, "width": 10, "height": 10},
                    "target": {"x": 1, "y": 2}
                  }
                }
                """);

        AppConfigWriter writer = new AppConfigWriter(configPath);
        writer.saveMiniMapRegion(new RegionConfig(12, 34, 120, 90));

        AppConfig config = new AppConfigLoader().loadFromPath(configPath);
        assertEquals(12, config.vision().miniMapRegion().x());
        assertEquals(34, config.vision().miniMapRegion().y());
        assertEquals(120, config.vision().miniMapRegion().width());
        assertEquals(90, config.vision().miniMapRegion().height());
        assertTrue(Files.exists(configPath));
    }
}
