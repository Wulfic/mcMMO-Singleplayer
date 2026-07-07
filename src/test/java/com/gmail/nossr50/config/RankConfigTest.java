package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.SubSkillType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link RankConfig} against the real bundled {@code skillranks.yml} on the test
 * classpath, with a temp data folder.
 *
 * <p>Only the explicit-{@code retroMode} getters and {@link RankConfig#getRankAddressKey} are
 * asserted here: the no-scaling-arg overload routes through {@code McMMOMod.getGeneralConfig()},
 * which is intentionally un-wired in unit tests (a runtime-only path). Constructing the config still
 * exercises full validation over every {@link SubSkillType} via {@code McMMOMod.getSkillTools()}.
 */
class RankConfigTest {

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new RankConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("skillranks.yml")));
    }

    @Test
    void rankAddressKeyFormatsStandardAndRetro(@TempDir Path dataFolder) {
        final RankConfig config = new RankConfig(dataFolder);
        assertEquals("Archery.ArcheryLimitBreak.Standard.Rank_1",
                config.getRankAddressKey(SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK, 1, false));
        assertEquals("Archery.ArcheryLimitBreak.RetroMode.Rank_3",
                config.getRankAddressKey(SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK, 3, true));
    }

    @Test
    void readsStandardUnlockLevels(@TempDir Path dataFolder) {
        final RankConfig config = new RankConfig(dataFolder);
        // Archery.ArcheryLimitBreak Standard ranks step 10, 20, 30... in the bundled default.
        assertEquals(10,
                config.getSubSkillUnlockLevel(SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK, 1, false));
        assertEquals(20,
                config.getSubSkillUnlockLevel(SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK, 2, false));
    }
}
