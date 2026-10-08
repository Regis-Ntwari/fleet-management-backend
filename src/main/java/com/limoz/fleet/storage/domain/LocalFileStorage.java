package com.limoz.fleet.storage.domain;

import com.limoz.fleet.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "fleet.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path basePath;

    public LocalFileStorage(AppProperties properties) {
        this.basePath = Path.of(properties.storage().local().basePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload directory " + basePath, e);
        }
        log.info("Local file storage initialised at {}", basePath);
    }

    @Override
    public String providerCode() {
        return "local";
    }

    @Override
    public String store(String keyHint, String contentType, InputStream content, long size) {
        LocalDate today = LocalDate.now();
        String safeHint = keyHint == null ? "file" : keyHint.replaceAll("[^A-Za-z0-9._-]", "_");
        String key = today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/" + UUID.randomUUID() + "_" + safeHint;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
        return key;
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Stored file not found: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", storageKey, e);
        }
    }

    private Path resolve(String key) {
        Path resolved = basePath.resolve(key).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}
