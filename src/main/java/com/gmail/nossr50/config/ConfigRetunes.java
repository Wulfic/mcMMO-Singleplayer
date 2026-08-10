package com.gmail.nossr50.config;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * The single registry of shipped config <em>defaults that have changed value</em> since a player's
 * config file could have been written, so {@link ConfigLoader} can carry the change onto an existing
 * file instead of stranding it in the jar.
 *
 * <h2>The problem this exists to solve</h2>
 * {@code ConfigLoader#copyMissingDefaults} back-fills only <b>absent</b> keys. Editing a value in a
 * bundled {@code .yml} therefore changes <b>nothing</b> for anybody who has already run the mod once
 * — including whoever reported the tuning problem that prompted the change. Six of the ten play-test
 * issues in the first §G batch were value changes, and every one of them would have shipped as a
 * no-op. A retune declared here is applied on the next load.
 *
 * <h2>What it will and will not touch</h2>
 * A retune fires only when the on-disk value is still <b>exactly the old shipped default</b> — i.e.
 * the player never expressed an opinion about it. A customised value is left alone and logged, never
 * overwritten: the file belongs to the player, and silently reverting their tuning is a worse
 * failure than a stale default (the same reasoning as the warn-don't-rewrite policy on renamed
 * sections, one step further along).
 *
 * <p>Each retune also carries a {@link Retune#version()}, and {@link ConfigLoader} stamps the file
 * with the highest version it has applied. That is what makes a retune fire <b>once, ever</b>: a
 * player who deliberately sets the value back to the old default afterwards keeps it, because the
 * stamp says that retune is already spent. Value comparison alone cannot distinguish "never touched
 * it" from "put it back on purpose".
 *
 * <h2>Adding one</h2>
 * Bump {@code version} past every existing entry for that file and append. Never edit a shipped
 * entry's {@code version} or {@code oldDefault} — files already stamped with it will skip the edit,
 * so the change silently reaches only new players.
 */
public final class ConfigRetunes {

    private ConfigRetunes() {
    }

    /** The key {@link ConfigLoader} stamps the applied retune version under, at each file's root. */
    public static final String VERSION_KEY = "Config_Version";

    /**
     * One changed default.
     *
     * @param fileName which config it lives in, e.g. {@code experience.yml}
     * @param path the dotted path of the value
     * @param oldDefault the value this key shipped with before; a file still holding this is
     *        migrated
     * @param newDefault the value it ships with now
     * @param version the retune sequence number for {@code fileName}, strictly increasing
     * @param reason a short human explanation, logged when the retune is applied
     */
    public record Retune(@NotNull String fileName, @NotNull String path, @NotNull Object oldDefault,
                         @NotNull Object newDefault, int version, @NotNull String reason) {
    }

    private static final List<Retune> RETUNES = new ArrayList<>();

    static {
        // 2026-08-03 (GitHub #6): sneaking paid 25 XP/s, which the reporter measured as the slowest
        // grind in the mod for the most attention-demanding activity in it. Doubled to 50, making
        // Stealth the fastest continuous earner -- ruled deliberately, not drifted into.
        RETUNES.add(new Retune("experience.yml",
                "Experience_Values.Stealth.Sneak.Baseline_Xp_Per_Second", 25.0D, 50.0D, 1,
                "sneak XP was too slow to be worth doing (GitHub #6)"));
        // 2026-08-10 (GitHub #12): pets kept being left behind by a teleport even with the #2 follow
        // feature switched on. The gate was never the feature, it was this radius -- a pet trailing a
        // sprinting or flying owner sits well beyond 32 blocks when the jump lands, so it was not in
        // the box and was never collected. 32 -> 128, which is roughly the band in which the pet
        // could still have been ticking at all.
        //
        // 🔑 THIS IS WHY ConfigRetunes EXISTS, THE SECOND TIME. The reporter has run the mod, so
        // their config.yml already holds `Pets_Follow_Teleport_Radius: 32` and copyMissingDefaults
        // would never touch it: editing the bundled yml alone would have shipped this fix to
        // everyone EXCEPT the person who reported it. (Note the on-disk value is the integer 32
        // while the default is the double 32.0 -- ConfigLoader compares numbers by value for exactly
        // this reason.) config.yml becomes the second file to carry a Config_Version stamp.
        RETUNES.add(new Retune("config.yml", "Skills.Taming.Pets_Follow_Teleport_Radius", 32.0D,
                128.0D, 1, "pets trailing further than 32 blocks were left behind (GitHub #12)"));
        // TODO.md item 3.1 deliberately does NOT add a retune. Limit Break became implemented, but
        // its AllowPVE default stayed false, so there is no changed default to carry onto an
        // existing file -- and an opt-in mechanic that switched itself on during an update would be
        // precisely the surprise a retune exists to make impossible.
    }

    /**
     * Every retune declared for one config file, in declaration order.
     *
     * @param fileName the config file name, e.g. {@code advanced.yml}
     * @return a new list, empty when this file has never been retuned (the common case)
     */
    public static @NotNull List<Retune> forFile(@NotNull String fileName) {
        final List<Retune> matches = new ArrayList<>();
        for (Retune retune : RETUNES) {
            if (retune.fileName().equals(fileName)) {
                matches.add(retune);
            }
        }
        return matches;
    }

    /**
     * The version a fully up-to-date copy of a file is stamped with: the highest version among its
     * retunes, or {@code 0} for a file that has never been retuned.
     *
     * @param retunes the retunes for one file, typically from {@link #forFile(String)}
     * @return the current retune version
     */
    public static int highestVersion(@NotNull List<Retune> retunes) {
        int highest = 0;
        for (Retune retune : retunes) {
            highest = Math.max(highest, retune.version());
        }
        return highest;
    }
}
