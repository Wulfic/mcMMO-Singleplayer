package com.gmail.nossr50.util;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

/**
 * Shared one-time Minecraft bootstrap for unit tests that touch live vanilla registries (item/block
 * id-path extraction in {@code ItemUtils}/{@code BlockUtils}, etc.). Call {@link #bootstrap()} from a
 * {@code @BeforeAll}.
 *
 * <p>Only works under the {@code fabric-loader-junit} test launcher (see {@code build.gradle}), which
 * runs tests through Knot's classloader so Minecraft's access wideners are applied — plain JUnit
 * throws an {@code IllegalAccessError} from {@code SimpleRegistry} during registration. Idempotent:
 * {@link Bootstrap#initialize()} is itself guarded, and the flag here avoids re-entry.
 */
final class McTestRegistries {

    private static boolean bootstrapped;

    private McTestRegistries() {}

    static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        bootstrapped = true;
    }
}
