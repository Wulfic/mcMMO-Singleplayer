package com.gmail.nossr50.util;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Every mcMMO block decision that can be made from a block's vanilla registry-id <em>path</em>
 * ({@code oak_log}) and a position key, with no Minecraft type anywhere in the signature. The
 * MC-typed bridge that extracts those keys from a live {@code Block}/{@code BlockState}/{@code World}
 * is {@code platform.BlockUtils}.
 *
 * <h2>Why this class exists (Phase 2, ruling P2-d)</h2>
 * The multi-version work seals {@code net.minecraft} out of everything except {@code fabric/} and
 * {@code platform/}. Relocating a file into {@code platform/} satisfies that boundary but does not
 * <em>shrink</em> it — the same lines still diverge per band, they just diverge somewhere else.
 * Only extraction shrinks it. Everything below is version-agnostic by construction: it reasons about
 * strings and longs, so it is byte-identical on every supported Minecraft band, and it is unit-testable
 * without the {@code fabric-loader-junit} harness (see {@code gradle-build-tuning}: {@code
 * Bootstrap.initialize()} costs ~53s per fork, which Phase 4.4 has to pay per band).
 *
 * <h2>⚠️ What deliberately did NOT move here</h2>
 * Two checks in the bridge are block <b>identity</b> comparisons, not id-path lookups —
 * {@code state.isOf(Blocks.SNOW)} in Berserk's insta-break set, and {@code formed == Blocks.OBSIDIAN}
 * in the lava gate. Re-expressing them as {@code "snow".equals(idPath)} would silently <em>broaden</em>
 * them to any namespace's block of that name. The store-backed whitelists this class delegates to are
 * already path-keyed and therefore already namespace-blind — that is the existing design and is not
 * relitigated here — but converting an exact check into a fuzzy one as a side effect of a refactor is
 * how semantics rot. Both stay on the MC side, one line each.
 *
 * <p>Also still MC-typed, because they read live block state rather than an identity: crop maturity
 * (the {@code age} property scan) and the two Hylian block-tag memberships — the latter reach this
 * class as {@link BooleanSupplier}s, see {@link #hylianTreasureGroup}.
 *
 * @see MaterialMapStore the hardcoded whitelists, keyed on the registry path
 * @see PlacedBlockTracker the placed-block flag store, keyed on (world key, packed position)
 */
public final class BlockRules {

    private BlockRules() {}

    /**
     * Whether the block grants XP for the given skill in the loaded {@code experience.yml}.
     *
     * <p>Null-safe on the config: these predicates are reachable before a server session has loaded
     * {@code experience.yml} (and from unit tests), and "no config" means "no XP" — matching
     * {@link com.gmail.nossr50.skills.BlockBreakXp}.
     */
    private static boolean givesSkillXp(@NotNull String idPath, @NotNull PrimarySkillType skill) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null) {
            return false;
        }
        return config.doesBlockGiveSkillXP(skill, ConfigStringUtils.getMaterialConfigString(idPath));
    }

    // --- Super-ability activation gates -------------------------------------

    /**
     * Whether a block should allow super-ability activation (step of the right/left-click trigger).
     * A short, hardcoded blacklist (e.g. interactive blocks) is excluded.
     */
    public static boolean canActivateAbilities(@NotNull String idPath) {
        return !McMMOMod.getMaterialMapStore().isAbilityActivationBlackListed(idPath);
    }

    /**
     * Whether a block should allow tool activation — step 1 of the 2-step super-ability activation.
     *
     * <p>PORT: legacy also excludes the Repair/Salvage anvil materials here; those come from the
     * not-yet-ported Repair/Salvage item configs, so only the MaterialMapStore blacklist is applied
     * for now (no vanilla block is an mcMMO anvil until an operator configures one).
     */
    public static boolean canActivateTools(@NotNull String idPath) {
        return !McMMOMod.getMaterialMapStore().isToolActivationBlackListed(idPath);
    }

    /** Whether a block can activate Herbalism abilities (Green Terra double-drop reach). */
    public static boolean canActivateHerbalism(@NotNull String idPath) {
        return McMMOMod.getMaterialMapStore().isHerbalismAbilityWhiteListed(idPath);
    }

    // --- Super-ability affected-block checks --------------------------------

    /**
     * Whether a block is affected by Super Breaker (Mining super ability). True if the block is
     * intended to be broken by a pickaxe, or grants Mining XP in the config.
     */
    public static boolean affectedBySuperBreaker(@NotNull String idPath) {
        if (McMMOMod.getMaterialMapStore().isIntendedToolPickaxe(idPath)) {
            return true;
        }
        return givesSkillXp(idPath, PrimarySkillType.MINING);
    }

    /** Whether a block is affected by Giga Drill Breaker (Excavation super ability). */
    public static boolean affectedByGigaDrillBreaker(@NotNull String idPath) {
        return givesSkillXp(idPath, PrimarySkillType.EXCAVATION);
    }

    /** Whether a block is affected by Green Terra (Herbalism super ability) — grants Herbalism XP. */
    public static boolean affectedByGreenTerra(@NotNull String idPath) {
        return givesSkillXp(idPath, PrimarySkillType.HERBALISM);
    }

    /** Whether a block is affected by Block Cracker (Berserk turning smooth blocks to cracked). */
    public static boolean affectedByBlockCracker(@NotNull String idPath) {
        return McMMOMod.getMaterialMapStore().isBlockCrackerWhiteListed(idPath);
    }

    /**
     * The path-keyed part of Berserk's insta-break set: an Excavation-XP block, or glass.
     *
     * <p>⚠️ <b>Incomplete on purpose.</b> Berserk also insta-breaks the snow <em>layer</em>, and that
     * arm is a {@code state.isOf(Blocks.SNOW)} identity check which stays in the bridge — see the
     * class note. A caller reaching for "is this affected by Berserk" wants
     * {@code platform.BlockUtils#affectedByBerserk}, not this.
     */
    public static boolean affectedByBerserkExceptSnowLayer(@NotNull String idPath) {
        return affectedByGigaDrillBreaker(idPath) || McMMOMod.getMaterialMapStore().isGlass(idPath);
    }

    // --- Woodcutting / tree ------------------------------------------------

    /** Whether a block grants Woodcutting XP (i.e. is a log addressed by the config). */
    public static boolean hasWoodcuttingXP(@NotNull String idPath) {
        return givesSkillXp(idPath, PrimarySkillType.WOODCUTTING);
    }

    /** Whether a block is a non-wood part of a tree (leaves, mushroom caps, warts) for Tree Feller. */
    public static boolean isNonWoodPartOfTree(@NotNull String idPath) {
        return McMMOMod.getMaterialMapStore().isTreeFellerDestructible(idPath);
    }

    /** Whether a block is any part of a tree — a Woodcutting log, or a non-wood tree part. */
    public static boolean isPartOfTree(@NotNull String idPath) {
        return hasWoodcuttingXP(idPath) || isNonWoodPartOfTree(idPath);
    }

    // --- Mining / ore ------------------------------------------------------

    /** Whether a block is an ore. */
    public static boolean isOre(@NotNull String idPath) {
        return McMMOMod.getMaterialMapStore().isOre(idPath);
    }

    // --- Herbalism block conversions ---------------------------------------

    /** Whether a block can be made mossy (Green Terra / mossify conversion whitelist). */
    public static boolean canMakeMossy(@NotNull String idPath) {
        return McMMOMod.getMaterialMapStore().isMossyWhiteListed(idPath);
    }

    /** Whether a block can be made into Mycelium (Shroom Thumb conversion whitelist). */
    public static boolean canMakeShroomy(@NotNull String idPath) {
        return McMMOMod.getMaterialMapStore().isShroomyWhiteListed(idPath);
    }

    /**
     * Classify a block into its Hylian Luck {@code Drops_From} group ({@code "Flowers"},
     * {@code "Bushes"} or {@code "Pots"}), or {@code null} if it is not a Hylian Luck source block.
     *
     * <p>Matched exactly as legacy did: the nine small flowers are a hardcoded list (legacy lists them
     * individually, not via the broader {@code small_flowers} tag — see
     * {@link MaterialMapStore#isHylianLuckFlower}); {@code fern}/{@code short_grass}/{@code dead_bush}
     * are hardcoded bush members; saplings and flower pots come from the vanilla block tags.
     *
     * <h2>⚠️⚠️ The tag arguments are {@link BooleanSupplier}s and that is load-bearing</h2>
     * {@code BlockState#isIn(TagKey)} <b>throws {@code IllegalStateException}</b> when the tag is
     * unbound, which it is until the datapacks have loaded — that is the whole reason this
     * classification happens at block-break time rather than at config load. Java evaluates arguments
     * eagerly, so taking two plain {@code boolean}s would run both tag reads for <em>every</em> block,
     * including the hardcoded flowers and bushes that return before any tag is consulted today. That
     * would convert a working short-circuit into a crash. Same trap, same fix, as
     * {@code platform.ParticleEffectUtils#spawnAtEyes}'s {@code Supplier}.
     *
     * @param idPath the broken block's registry-id path
     * @param isSapling whether the block is in the vanilla {@code saplings} tag — evaluated only if
     *     the block is not a hardcoded flower or bush member
     * @param isFlowerPot whether the block is in the vanilla {@code flower_pots} tag — evaluated only
     *     if neither of the earlier groups matched
     * @return the group name, or {@code null} if the block drops no Hylian treasure
     */
    public static @Nullable String hylianTreasureGroup(@NotNull String idPath,
            @NotNull BooleanSupplier isSapling, @NotNull BooleanSupplier isFlowerPot) {
        final MaterialMapStore store = McMMOMod.getMaterialMapStore();
        if (store.isHylianLuckFlower(idPath)) {
            return "Flowers";
        }
        if (store.isHylianLuckBushBlock(idPath) || isSapling.getAsBoolean()) {
            return "Bushes";
        }
        if (isFlowerPot.getAsBoolean()) {
            return "Pots";
        }
        return null;
    }

    // --- Placed-block reward eligibility (legacy UserBlockTracker) ----------

    /**
     * Record that the block at this position did not get there naturally, so gathering skills give it
     * no rewards (legacy {@code BlockUtils#setUnnaturalBlock}). Hand placement is one source, but a
     * block a player <em>manufactured</em> without touching it is the same exploit and the same flag.
     *
     * <p>Callers other than the block-place seam gate themselves on their own {@code ExploitFix} key
     * before calling (lava formation, snow golems, pistons) — this method enforces only the tracker's
     * master switch.
     */
    public static void markUnnatural(@NotNull String worldKey, long posKey) {
        if (!isPlacedBlockTrackingEnabled()) {
            // Don't just refuse to *read* the flags when the gate is off -- refuse to write them.
            // Otherwise every placed block still costs memory and still lands in placed_blocks.dat,
            // and switching the gate back on resurrects a session's worth of stale flags.
            return;
        }
        McMMOMod.getPlacedBlockTracker().setIneligible(worldKey, posKey);
    }

    /**
     * Clear the placed-block flag at this position — the block there is gone (broken / blasted /
     * felled), so the location is natural again (legacy {@code setNaturalBlock} →
     * {@code UserBlockTracker#setEligible}). Idempotent for a position that was never placed, and it
     * bounds the tracker's memory to still-standing placed blocks.
     */
    public static void markNatural(@NotNull String worldKey, long posKey) {
        McMMOMod.getPlacedBlockTracker().setEligible(worldKey, posKey);
    }

    /**
     * Whether the block at this position must give no gathering rewards because a player placed it
     * (legacy's {@code !mcMMO.getUserBlockTracker().isIneligible(block)} guard on every gathering
     * branch). Any block never hand-placed reads as eligible (the default).
     */
    public static boolean isRewardIneligible(@NotNull String worldKey, long posKey) {
        if (!isPlacedBlockTrackingEnabled()) {
            return false;
        }
        return McMMOMod.getPlacedBlockTracker().isIneligible(worldKey, posKey);
    }

    /**
     * Deny gathering rewards for a block that a lava/water interaction just manufactured — the
     * cobblestone, stone and basalt "generators" (legacy {@code BlockListener#onBlockFormEvent},
     * {@code ExploitFix.LavaStoneAndCobbleFarming}).
     *
     * <p><b>The §A/K9 tracker cannot reach this on its own.</b> Its only writer is the hand-placement
     * seam, on the reasoning that a block nobody placed needs no flag. A generated block <em>is</em>
     * a block nobody placed, and it is manufactured on demand, for free, forever: at the shipped
     * prices a basalt generator pays 40 Mining XP per block and a stone generator 15, both fully
     * automatable. Conservative tracking is right about grown and world-gen blocks and wrong about
     * these, which is why legacy hooks them explicitly.
     *
     * <p><b>The block must actually pay Mining XP.</b> Plain cobblestone has no entry in the shipped
     * table, so the classic cobble generator was never worth anything anyway; flagging it would only
     * grow the tracker for nothing.
     *
     * <p>⚠️ <b>Obsidian's exemption is NOT here</b> — making it consumes the lava source, so the loop
     * cannot repeat without hauling another bucket. That check is an identity comparison against
     * {@code Blocks.OBSIDIAN} and stays in the bridge; see the class note.
     *
     * @param formedIdPath registry-id path of the block that has just appeared at this position
     */
    public static void markLavaFormed(@NotNull String worldKey, long posKey,
            @NotNull String formedIdPath) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null || !config.preventStoneLavaFarming()) {
            return;
        }
        if (!givesSkillXp(formedIdPath, PrimarySkillType.MINING)) {
            return;
        }
        markUnnatural(worldKey, posKey);
    }

    /**
     * Deny Excavation rewards for a snow layer a snow golem just laid down
     * ({@code ExploitFix.SnowGolemExcavation}, legacy {@code BlockListener#onEntityBlockFormEvent}).
     *
     * <p>A snow golem walking in a pen lays snow indefinitely, for free, with nobody at the keyboard;
     * the shipped table pays 20 Excavation XP for a layer and 40 for a block. Like the lava
     * generators this is invisible to the §A/K9 tracker, because the golem is not a player and the
     * snow was never placed by hand.
     *
     * <p>Gated on the snow actually being worth Excavation XP, so a resource pack or config that
     * zeroes it stops the tracker growing for nothing.
     *
     * @param formedIdPath registry-id path of the block the golem just created — always {@code snow}
     *     in vanilla, passed in so the caller reads the world rather than this method assuming it
     */
    public static void markSnowGolemFormed(@NotNull String worldKey, long posKey,
            @NotNull String formedIdPath) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null || !config.isSnowExploitPrevented()) {
            return;
        }
        if (!givesSkillXp(formedIdPath, PrimarySkillType.EXCAVATION)) {
            return;
        }
        markUnnatural(worldKey, posKey);
    }

    /**
     * Carry placed-block flags along with the blocks a piston just pushed or pulled
     * ({@code ExploitFix.PistonCheating}, legacy {@code BlockListener#onBlockPistonExtend}/
     * {@code onBlockPistonRetract}).
     *
     * <p>Without this the flags stay at the old coordinates while the blocks walk away from them, so
     * <em>place → push → mine</em> launders a hand-placed block back into a rewarding one. Legacy
     * closes it by marking every destination unnatural.
     *
     * <p><b>This port moves the flag instead, which is strictly narrower.</b> Legacy's blanket mark
     * is a consequence of its over-marking design; applied to this tracker it would introduce the
     * false positive the tracker was built to avoid — pushing a <em>natural</em> stone wall one block
     * sideways would make it worthless forever. A piston moves blocks, it does not create them, so
     * the honest update is to move what we know.
     *
     * <p>⚠️ <b>The three passes are not decoration.</b> Sources and destinations overlap whenever a
     * piston pushes a column (the destination of one block is the source of the next), so reading,
     * clearing and re-setting have to happen as separate phases or a flag is cleared after it was
     * just written and the middle of every pushed column silently loses its flag.
     *
     * @param movedFrom packed positions the blocks occupied <em>before</em> the push
     * @param movedTo packed positions those same blocks arrive at, index-aligned with
     *     {@code movedFrom} (the caller owns the direction arithmetic, which needs an MC type)
     * @param broken packed positions destroyed by the push (a torch in the way) — those blocks are
     *     gone, so their flags are simply dropped
     * @throws IllegalArgumentException if the two moved arrays differ in length, which would mean a
     *     block was about to be moved to nowhere or from nowhere
     */
    public static void movePlacedFlags(@NotNull String worldKey, long @NotNull [] movedFrom,
            long @NotNull [] movedTo, long @NotNull [] broken) {
        if (movedFrom.length != movedTo.length) {
            throw new IllegalArgumentException("movedFrom/movedTo must be index-aligned, got "
                    + movedFrom.length + " and " + movedTo.length);
        }
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null || !config.isPistonCheatingPrevented()
                || !isPlacedBlockTrackingEnabled()) {
            return;
        }
        final PlacedBlockTracker tracker = McMMOMod.getPlacedBlockTracker();

        // 1. Read every source flag before anything is written.
        final long[] destinations = new long[movedFrom.length];
        int destinationCount = 0;
        for (int i = 0; i < movedFrom.length; i++) {
            if (tracker.isIneligible(worldKey, movedFrom[i])) {
                destinations[destinationCount++] = movedTo[i];
            }
        }
        // 2. Vacate every source (and every broken block — that one is simply gone).
        for (long pos : movedFrom) {
            tracker.setEligible(worldKey, pos);
        }
        for (long pos : broken) {
            tracker.setEligible(worldKey, pos);
        }
        // 3. Only now write the destinations.
        for (int i = 0; i < destinationCount; i++) {
            tracker.setIneligible(worldKey, destinations[i]);
        }
    }

    /**
     * Whether the placed-block gate is switched on ({@code ExploitFix.PlacedBlocks}, GitHub #9).
     *
     * <p>Gated at both ends of the tracker rather than at its call sites: this class is the only
     * bridge to it, and the alternative — a check in each of the four consumers (block break, blast
     * mining, tree feller, the crop diversion) — is four chances to add a fifth consumer and forget.
     *
     * <p>Defaults to <b>on</b> when no config is loaded yet. A gate whose config has not arrived must
     * fail closed; failing open would pay full rewards for hand-placed blocks during world load,
     * which is the exploit itself.
     */
    public static boolean isPlacedBlockTrackingEnabled() {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        return config == null || config.isPlacedBlockTrackingEnabled();
    }
}
