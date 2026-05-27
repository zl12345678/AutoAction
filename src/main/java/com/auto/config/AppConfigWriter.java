package com.auto.config;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Persists user settings into the local {@code autoActionConfig.json} file.
 */
public final class AppConfigWriter {
    private final Path localConfigPath;

    public AppConfigWriter() {
        this(Paths.get(AppConfigLoader.DEFAULT_RESOURCE));
    }

    public AppConfigWriter(Path localConfigPath) {
        this.localConfigPath = localConfigPath;
    }

    public void saveMiniMapRegion(RegionConfig region) {
        try {
            JSONObject root = readRootObject();
            JSONObject vision = requiredObject(root, "vision");
            vision.put("miniMapRegion", toJson(region));
            writeRootObject(root);
        } catch (IOException e) {
            throw new ConfigException("Failed to save miniMapRegion: " + localConfigPath, e);
        }
    }

    private JSONObject readRootObject() throws IOException {
        if (Files.exists(localConfigPath)) {
            return new JSONObject(Files.readString(localConfigPath, StandardCharsets.UTF_8));
        }
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(AppConfigLoader.DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                throw new ConfigException("Config resource not found: " + AppConfigLoader.DEFAULT_RESOURCE);
            }
            return new JSONObject(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private void writeRootObject(JSONObject root) throws IOException {
        Path parent = localConfigPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(localConfigPath, root.toString(2) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static JSONObject requiredObject(JSONObject object, String key) {
        if (!object.has(key)) {
            throw new ConfigException("Missing object: " + key);
        }
        return object.getJSONObject(key);
    }

    private static JSONObject toJson(RegionConfig region) {
        JSONObject json = new JSONObject();
        json.put("x", region.x());
        json.put("y", region.y());
        json.put("width", region.width());
        json.put("height", region.height());
        return json;
    }
}
