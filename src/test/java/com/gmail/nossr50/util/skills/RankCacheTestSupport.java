package com.gmail.nossr50.util.skills;

/**
 * Re-exposes {@link RankUtils}' package-private cache reset to tests in <em>other</em> packages.
 *
 * <p><b>Why this class exists instead of a one-line visibility change.</b> {@code RankUtils} keeps
 * its unlock-level lookups in a static {@code subSkillRanks} map, and {@code resetRankCache()} is
 * package-private "visible for testing". {@code NotificationManagerTest} lives in
 * {@code com.gmail.nossr50.util.player} and cannot reach it. The owner ruled (§77, 2026-09-22) that
 * the mod should not ship an extra public method purely so a test in a sibling package can null a
 * private static, so the reach-through lives here, in {@code src/test} and in {@code RankUtils}'
 * own package. Production API is unchanged.
 *
 * <p><b>What it is for, and why a test would want it.</b> That static cache is shared by the whole
 * fork, and it is warmed by whichever test class touches ranks first — while <em>that</em> class
 * still has {@code RankConfig} bound. A later class that deliberately unbinds the configs to prove
 * a null guard sits in front of every config read then finds {@code RankUtils.getRank} returning
 * from the warm map without ever reaching {@code getRankUnlockLevel()} →
 * {@code McMMOMod.getRankConfig()}. The read it is trying to prove unreachable is already
 * unreachable, for the wrong reason, and the assertion proves nothing.
 *
 * <p>🔑 <b>A test's reachability can depend on what a SIBLING TEST CLASS did to a static</b> — and
 * with {@code maxParallelForks = 4} Gradle assigns classes to forks non-deterministically, so which
 * sibling ran first is a coin flip. Resetting makes the reading deterministic rather than lucky.
 *
 * <p>⚠️ <b>Clearing it cannot break a sibling class.</b> Cold is the fresh-JVM default; classes run
 * sequentially within a fork and there is no {@code junit-platform.properties} enabling parallel
 * execution; and {@code RankUtilsTest} and {@code SkillGatingTest} already reset it in their own
 * setup. A class that needs a warm cache <em>and</em> does not bind {@code RankConfig} itself is
 * already a latent flake under fork assignment — this surfaces that, it does not create it.
 */
public final class RankCacheTestSupport {

    private RankCacheTestSupport() {
    }

    /** Drops {@link RankUtils}' lazily-built unlock-level cache, so the next read rebuilds it. */
    public static void resetRankCache() {
        RankUtils.resetRankCache();
    }
}
