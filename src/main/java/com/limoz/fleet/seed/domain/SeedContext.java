package com.limoz.fleet.seed.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Shared state between seed steps: ids created so far and a deterministic random source. */
public class SeedContext {

    public static final String PASSWORD = "Limoz@2026";

    private final Clock clock;
    private final ZoneId zone;
    private final Random random = new Random(20260604L);
    private final Map<String, List<Long>> ids = new HashMap<>();
    private final Map<String, Long> named = new HashMap<>();

    public SeedContext(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(zone).toInstant();
    }

    public Instant at(LocalDate date, int hour, int minute) {
        return at(date, LocalTime.of(hour, minute));
    }

    public ZoneId zone() {
        return zone;
    }

    public Random random() {
        return random;
    }

    public int between(int min, int maxInclusive) {
        return min + random.nextInt(maxInclusive - min + 1);
    }

    public <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    public void add(String type, Long id) {
        ids.computeIfAbsent(type, k -> new ArrayList<>()).add(id);
    }

    public List<Long> ids(String type) {
        return ids.getOrDefault(type, List.of());
    }

    public void name(String key, Long id) {
        named.put(key, id);
    }

    public Long id(String key) {
        Long id = named.get(key);
        if (id == null) throw new IllegalStateException("Seed reference not found: " + key);
        return id;
    }
}
