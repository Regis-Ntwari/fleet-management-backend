package com.limoz.fleet.storage;

import java.io.InputStream;

/**
 * Storage abstraction so production can switch to S3-compatible object storage without touching
 * callers. Implementations are selected through {@code fleet.storage.provider}.
 */
public interface FileStorage {

    String providerCode();

    /** Stores the content and returns the provider-specific storage key. */
    String store(String keyHint, String contentType, InputStream content, long size);

    InputStream open(String storageKey);

    void delete(String storageKey);
}
