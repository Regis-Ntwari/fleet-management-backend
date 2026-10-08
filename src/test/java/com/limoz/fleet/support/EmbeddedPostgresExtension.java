package com.limoz.fleet.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * One embedded PostgreSQL server per test JVM. Real PostgreSQL (not H2) so that Flyway migrations,
 * JSONB columns and partial indexes are exercised exactly as in production. Works without Docker.
 */
public final class EmbeddedPostgresExtension {

    private static volatile EmbeddedPostgres instance;

    private EmbeddedPostgresExtension() {}

    public static EmbeddedPostgres get() {
        if (instance == null) {
            synchronized (EmbeddedPostgresExtension.class) {
                if (instance == null) {
                    try {
                        instance = EmbeddedPostgres.builder().start();
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                instance.close();
                            } catch (IOException ignored) {
                                // JVM is exiting
                            }
                        }));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not start embedded PostgreSQL", e);
                    }
                }
            }
        }
        return instance;
    }

    public static String jdbcUrl() {
        return get().getJdbcUrl("postgres", "postgres");
    }
}
