package com.limoz.fleet.seed;

/**
 * One step of the development dataset. Steps run in {@code order()} sequence inside {@link DevDataSeeder};
 * each module contributes its own step so the seeder never needs to know module internals.
 */
public interface SeedStep {

    int order();

    String name();

    void seed(SeedContext context);
}
