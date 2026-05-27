package com.auto.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public final class ResourceFileSupport {
    private ResourceFileSupport() {
    }

    public static Path materializeDirectory(String directoryPrefix, Map<String, String> resources) {
        try {
            Path root = Files.createTempDirectory(directoryPrefix);
            root.toFile().deleteOnExit();
            for (Map.Entry<String, String> entry : resources.entrySet()) {
                Path target = root.resolve(entry.getValue());
                Files.createDirectories(target.getParent());
                try (InputStream inputStream = Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream(entry.getKey())) {
                    if (inputStream == null) {
                        throw new IOException("Resource not found: " + entry.getKey());
                    }
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
                target.toFile().deleteOnExit();
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to materialize resources", e);
        }
    }
}
