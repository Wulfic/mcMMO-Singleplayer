package com.gmail.nossr50.gametest;

import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.player.UserManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Ship gate 14 (TODO.md §83).</b> The real game client creates a NEW singleplayer world with mcMMO
 * loaded, joins it, and mcMMO is running in it.
 *
 * <p>🔴 <b>GitHub #20 is why.</b> v1.5.1 shipped a milestone advancement that Minecraft 26.3 refuses
 * at registry load, and a failed registry load means no world: every 26.3 player sat on
 * <i>"Preparing for world creation…"</i>. The suite was green and every structural gate passed. The
 * create-new-world path this test drives is the exact one the player could not get through.
 *
 * <p>⚠️ <b>This class names no Minecraft method, on purpose.</b> It talks to Minecraft only through
 * Fabric's game-test API and to mcMMO only through mcMMO's own statics, so the same source compiles
 * on the official-named bands and the yarn-named {@code 1.21.x} bands alike. The lambdas below take
 * a {@code MinecraftServer} they never call into; comparing its identity is enough.
 *
 * <p>🔑 {@link #PASS_MARKER} is logged only after every check has passed, and
 * {@code scripts/client-world-check.sh} refuses a green Gradle run that lacks it: a client game-test
 * run whose entrypoint was never found runs zero tests and exits 0, and that must not read as a pass.
 */
public class WorldLoadClientGameTest implements FabricClientGameTest {

    /** Grepped by {@code scripts/client-world-check.sh}. Change both together or neither. */
    public static final String PASS_MARKER = "mcMMO client world check: PASSED";

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO gate 14");

    @Override
    public void runTest(ClientGameTestContext context) {
        LOGGER.info("mcMMO client world check: creating a NEW singleplayer world (the GitHub #20 path)");
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            LOGGER.info("mcMMO client world check: world created, joined, chunks rendered");

            // The session mcMMO opened must be THIS world's server. A stale static from an earlier
            // session, or none at all, both mean onServerStarting did not run for the world we joined.
            check(Boolean.TRUE.equals(world.getServer()
                            .computeOnServer(server -> McMMOMod.getServer() == server)),
                    "mcMMO's server session is not the server of the world that was just created");
            check(Boolean.TRUE.equals(world.getServer()
                            .computeOnServer(server -> McMMOMod.getProfileStore() != null)),
                    "mcMMO bound no profile store for the new world, so no player's skills would load");

            // The joining player gets an mcMMO profile (PlayerSessionListener). waitFor throws on
            // timeout, which fails the test with the game's own message.
            world.getServer().waitFor(server -> UserManager.getPlayers().size() == 1);
            LOGGER.info("mcMMO client world check: the joined player has an mcMMO profile");

            context.takeScreenshot("mcmmo-gate14-new-world");
            LOGGER.info(PASS_MARKER);
        }
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            LOGGER.error("mcMMO client world check: FAILED -- {}", failure);
            throw new AssertionError(failure);
        }
    }
}
