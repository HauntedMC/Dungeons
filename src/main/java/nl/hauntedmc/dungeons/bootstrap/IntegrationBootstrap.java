package nl.hauntedmc.dungeons.bootstrap;

import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

/**
 * Registers optional integrations that depend on external plugins being present.
 *
 * <p>The class currently acts as an explicit placeholder stage so optional integrations can be
 * added without obscuring the main bootstrap sequence.</p>
 */
final class IntegrationBootstrap {
    private final DungeonsRuntime runtime;

    /**
     * Creates the integration bootstrap stage.
     */
    IntegrationBootstrap(DungeonsRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Registers optional integrations after the core runtime graph is available.
     *
     * <p>No optional integrations are currently wired, but keeping the stage explicit preserves the
     * startup boundary for future integrations.</p>
     */
    void registerOptionalIntegrations() {}
}
