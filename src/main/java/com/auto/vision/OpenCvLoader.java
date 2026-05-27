package com.auto.vision;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OpenCvLoader {
    private static final String CONFIG_RESOURCE = "lib/opencv/opencv.properties";
    private static final String LIBRARY_RESOURCE_ROOT = "lib/opencv/";
    private static final AtomicBoolean LOADED = new AtomicBoolean();

    private OpenCvLoader() {
    }

    public static synchronized void load() {
        if (LOADED.get()) {
            return;
        }
        try {
            OpenCvLoadConfig config = loadConfiguredResource();
            for (Path preload : resolveLibraryPaths(config.preloadLibraries())) {
                System.load(preload.toString());
            }
            List<Path> mainLibraries = resolveLibraryPaths(List.of(config.nativeLibrary()));
            System.load(mainLibraries.get(0).toString());
            LOADED.set(true);
        } catch (IOException | URISyntaxException | RuntimeException e) {
            throw new IllegalStateException("Failed to load OpenCV native library", e);
        }
    }

    private static List<Path> resolveLibraryPaths(List<String> libraries) throws IOException, URISyntaxException {
        List<Path> resolved = new ArrayList<>();
        Path extractionDir = null;
        for (String library : libraries) {
            URL resource = Thread.currentThread().getContextClassLoader().getResource(LIBRARY_RESOURCE_ROOT + library);
            if (resource == null) {
                throw new IllegalStateException("OpenCV native library not found: " + library);
            }
            if ("file".equalsIgnoreCase(resource.getProtocol())) {
                resolved.add(Paths.get(resource.toURI()));
                continue;
            }
            if (extractionDir == null) {
                extractionDir = Files.createTempDirectory("opencv-java-");
                extractionDir.toFile().deleteOnExit();
            }
            Path target = extractionDir.resolve(library);
            try (InputStream inputStream = resource.openStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            resolved.add(target);
        }
        return resolved;
    }

    private static OpenCvLoadConfig loadConfiguredResource() {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(CONFIG_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("OpenCV config resource not found: " + CONFIG_RESOURCE);
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            String nativeLibrary = properties.getProperty("nativeLibrary", "").trim();
            if (nativeLibrary.isEmpty()) {
                throw new IllegalStateException("OpenCV nativeLibrary property is blank");
            }
            String preloadLibraries = properties.getProperty("preloadLibraries", "");
            List<String> preloadList = new ArrayList<>();
            for (String entry : preloadLibraries.split(",")) {
                String value = entry.trim();
                if (!value.isEmpty()) {
                    preloadList.add(value);
                }
            }
            return new OpenCvLoadConfig(nativeLibrary, preloadList);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read OpenCV config resource", e);
        }
    }

    private record OpenCvLoadConfig(String nativeLibrary, List<String> preloadLibraries) {
    }
}
