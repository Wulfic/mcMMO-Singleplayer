package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.util.MobOrigins;
import com.gmail.nossr50.util.MobTiers;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.CombatUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.CopperGolemEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.NotNull;

/**
 * Hunter's kill handler: the one place a mob's death is turned into progress, and the four gates that
 * decide whether it is allowed to. Both of the skill's axes are paid here — the per-mob mastery
 * counter (stage 3) and the skill's own XP (stage 5).
 *
 * <p>MC-typed glue only. {@link HunterManager} owns every number — what a threshold is, which tier a
 * count has reached, whether this kill crossed one, what a tier pays; this class answers "who killed
 * what, and does it count".
 *
 * <h2>✅ Both axes ride the SAME gates — ruled 2026-07-30 (user)</h2>
 * The plan's D-HU1 note recommended the opposite: keep the counter strict but let XP through the
 * looser {@code CombatUtils#processCombatXP} gates, so a spawner zombie would pay XP without paying
 * mastery. That was written before stage 1 existed and it does not survive the arithmetic. Hunter's
 * curve is 11,010,000 XP, common hostiles pay 300, and a modest spawner farm turns over a thousand
 * mobs an hour — <b>37 hours to a maxed skill</b>, against an 80 h floor inherited from Agility's
 * D-AG6. One gate chain also means "the kill counted" and "the kill paid" can never drift apart,
 * which is the same structural argument that made {@link #masteryKeyOf} a single shared function.
 *
 * <p>⚠️ <b>What this still does not close, stated rather than hidden:</b> nether-wastes piglins,
 * dark-room hostiles, endermen in the End and guardians in a monument are all legitimately
 * {@code NATURAL}, and no spawn origin will ever exclude them. A grinder the player stands in and
 * swings at is excluded by nothing. D-HU1 holds a rolling per-mob-per-hour cap in reserve as the
 * additive backstop for exactly that, and §G is what decides whether it is needed — see the
 * PLAYTEST_G session 11 rows, which measure the three worst cases by name.
 *
 * <h2>The seam, and the ordering question the plan left open</h2>
 * {@code ServerLivingEntityEvents.AFTER_DEATH}. Fabric fires it from {@code LivingEntity#onDeath} at
 * the {@code World#sendEntityStatus} call — <b>after</b> {@code drop()} has already run (bytecode:
 * {@code drop} is at offset 150 of {@code onDeath}, {@code sendEntityStatus} at 158). So the kill that
 * crosses a threshold does <b>not</b> get that threshold's reward on the same corpse; the next one
 * does. That costs nothing today — mastery pays damage, not loot — but it is the answer stage 6's
 * Trophy Hunter re-roll needs, and it is why that re-roll rides {@code dropLoot} rather than trying to
 * key off a level-up detected here.
 *
 * <p>The event is fired inside {@code onDeath}'s {@code instanceof ServerWorld} branch, so there is no
 * client-side fire to guard against — unlike the {@code UseBlockCallback} trap that made Repair
 * unusable on armour.
 *
 * <h2>⚠️ The gates ARE the feature</h2>
 * 10,000 kills of one mob is roughly 28 hours by hand and under four hours in front of a gold farm.
 * Everything interesting about this skill lives in the distance between those two numbers, so the four
 * gates below are not defensive boilerplate — three are lifted verbatim from
 * {@link CombatUtils#processCombatXP} and the fourth is the whole of stage 1:
 *
 * <ol>
 *   <li><b>Player attribution.</b> {@code source.getAttacker()} must be the player. This is what
 *       excludes the great majority of farms outright — they kill by fall, lava or suffocation — and
 *       it also excludes a wolf's kill, which belongs to Taming.</li>
 *   <li><b>The PVE/PVP switches</b>, via {@link CombatUtils#canCombatSkillsTrigger}. Hunter is a
 *       combat skill and its subject <em>is</em> the target's identity, so these decide whether a
 *       class of target feeds the skill at all rather than merely muting a bonus.</li>
 *   <li><b>Manufactured creatures</b> — Call-of-the-Wild summons, plus the golems a player can stack
 *       out of blocks and kill on demand. See {@link #isManufactured}, which is wider than legacy's
 *       iron-golem check and had to be.</li>
 *   <li><b>Spawn origin</b> — stage 1's {@link MobOrigins} marker. Spawner, trial-spawner, bred,
 *       player-placed and structure mobs pay nothing.</li>
 * </ol>
 *
 * <p>Gate 4 only becomes observable here: until this class existed there was no counter for the
 * marker to refuse, which is why {@code PLAYTEST_G} session 11 tests stage 1 and stage 3 together.
 *
 * @see <a href="file:../../../../../../../../plans/new-skills/hunter.md">plans/new-skills/hunter.md</a>
 */
public final class HunterListener {

    /**
     * Guards a single INFO line the first time a kill is counted this session.
     *
     * <p>Same reason as {@link MobOrigins}' own first-mark line: the counters are invisible for the
     * first 499 kills of a mob, so "the gate refused everything I killed" and "the listener never
     * bound" look identical from inside the game. One line separates them, and it names the mob and
     * the running total so the play-test can check it against {@code mcmmo/players/<uuid>.yml}.
     *
     * <p>{@code AtomicBoolean} rather than a plain flag to match {@code MobOrigins}; deaths are
     * server-thread work, but the two logs should not differ in shape for no reason.
     */
    private static final AtomicBoolean LOGGED_FIRST_KILL = new AtomicBoolean();

    private HunterListener() {
    }

    /** Register the kill counter. Called once from {@code McMMOMod#onInitialize}. */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(HunterListener::onDeath);
    }

    /**
     * A living entity died: if the player killed it and it qualifies, count it.
     *
     * <p>Package-private rather than private so {@code HunterListenerTest} can drive the real handler
     * instead of a re-derived copy of its gates — the gate-versus-wiring trap this port has hit
     * before, where a test proves the predicate and the predicate is never called.
     *
     * @param victim the entity that just died
     * @param source what killed it
     */
    static void onDeath(@NotNull LivingEntity victim, @NotNull DamageSource source) {
        // Gate 1, first because it is both the cheapest read and the most selective: a farm that
        // kills by fall damage, lava or suffocation has no attacker at all. getAttacker() resolves a
        // projectile back to its shooter, so an arrow kill is the player's; a wolf's kill is the
        // wolf's, and Taming owns that hit.
        if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) {
            return;
        }

        // Gate 2: the operator's Enabled_For_PVE / Enabled_For_PVP switches. Tamed animals and
        // players route to the PVP switch; everything else to PVE.
        if (!CombatUtils.canCombatSkillsTrigger(PrimarySkillType.HUNTER, victim)) {
            return;
        }

        // Gate 3: mobs the player manufactures at will. The summon check and the iron-golem half of
        // isManufactured are verbatim from CombatUtils#processCombatXP; the other two golems are
        // Hunter's own and stage 5 had to add them -- see isManufactured.
        if (McMMOMod.getTransientEntityTracker().isTransient(victim.getUuid())) {
            return;
        }
        if (isManufactured(victim)) {
            return;
        }

        // Gate 4: stage 1's spawn-origin marker.
        if (!MobOrigins.countsTowardMastery(victim)) {
            return;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(killer.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }
        final HunterManager hunter = mmoPlayer.getHunterManager();
        if (hunter == null) {
            return;
        }

        final String mobId = masteryKeyOf(victim);
        final int killsBefore = hunter.getKills(mobId);
        final int killsAfter = hunter.recordKill(mobId);
        announceFirstCountedKill(mobId, killsAfter);

        // The vertical axis, added in stage 5. Deliberately behind the SAME four gates the counter
        // just passed rather than the looser CombatUtils#processCombatXP set the plan originally
        // recommended -- see the "Both axes ride the SAME gates" section on this class.
        hunter.awardKillXp(MobTiers.tierOf(victim));

        if (hunter.crossedMasteryThreshold(killsBefore, killsAfter)) {
            announceMastery(mmoPlayer, victim, hunter.masteryTier(killsAfter), killsAfter);
        }
    }

    /**
     * Whether this creature only exists because a player made it — the third half of gate 3.
     *
     * <h2>⚠️ The three constructed golems all reach this listener as ordinary kills</h2>
     * {@code CarvedPumpkinBlock#trySpawnEntity} builds the snow golem, the iron golem and (new in
     * 1.21.11) the copper golem, and it creates all three with {@code SpawnReason.TRIGGERED}. Stage 1
     * maps {@code TRIGGERED} to {@link com.gmail.nossr50.datatypes.mobs.MobOrigin#NATURAL}, so the
     * spawn-origin gate lets every one of them through — and that mapping is <b>correct and must not
     * change</b>, because {@code TRIGGERED} is also how a warden emerges from a sculk shrieker, how
     * silverfish come out of infested stone and how a slime splits when you cut it.
     *
     * <p>So the exclusion has to be by species, and it is narrow because it can afford to be:
     *
     * <ul>
     *   <li><b>Iron golem</b> — only when {@code isPlayerCreated()}. A village's own golem is a real
     *       creature with a real origin; the one you stacked out of four iron blocks is not. This
     *       half is legacy's, verbatim from {@code CombatUtils#processCombatXP}.</li>
     *   <li><b>Snow golem and copper golem</b> — <em>always</em>. Neither has any natural spawn in the
     *       game, so unlike the iron golem there is no honest instance to protect, and neither
     *       carries an {@code isPlayerCreated} flag to test. A pumpkin and two snow blocks is a
     *       dispenser loop.</li>
     * </ul>
     *
     * <p>Before stage 5 this leak was worth nothing — the only reward was mastery against snow golems
     * specifically, i.e. bonus damage versus a mob you manufacture. Paying skill XP is what turns it
     * into an exploit, so it is closed in the same stage that creates it.
     */
    private static boolean isManufactured(@NotNull LivingEntity victim) {
        if (victim instanceof IronGolemEntity golem) {
            return golem.isPlayerCreated();
        }
        return victim instanceof SnowGolemEntity || victim instanceof CopperGolemEntity;
    }

    /**
     * The key one creature's mastery is filed under: its <b>full</b> registry id, namespace included
     * ({@code minecraft:zombie}).
     *
     * <p>⚠️ <b>One function on purpose, and it is not pedantry.</b> Two places need this key — here,
     * where a kill is banked, and {@code EntityDamageListener#applyHunterMastery}, where the resulting
     * bonus is spent. They index the same map, so if the two ever disagreed about the key the counters
     * would keep climbing and the damage bonus would read {@code 0.0} forever, with no error, no log
     * and no failing test on either side alone. That is the one-directional-completeness trap this
     * port has now hit three times; the cheapest possible fix is to make the two calls literally the
     * same call.
     *
     * <p>Deliberately <em>not</em> {@code ConfigStringUtils.getConfigEntityTypeString(getPath())}
     * ("{@code Cow}"), which every Husbandry table uses. Those tables are closed key spaces authored
     * by hand in a YAML file; this one is open-ended, persisted verbatim into the player profile, and
     * has to survive two mods shipping a creature of the same name.
     */
    static @NotNull String masteryKeyOf(@NotNull LivingEntity entity) {
        return Registries.ENTITY_TYPE.getId(entity.getType()).toString();
    }

    /**
     * Tell the player they have just crossed a mastery threshold against this creature.
     *
     * <p>Routed as {@link NotificationType#SUBSKILL_UNLOCKED} rather than {@code SUBSKILL_MESSAGE}:
     * that type ships as action bar <em>plus</em> a chat copy, and a milestone 500 kills in the making
     * must not be a flash on the action bar during a fight that the player then cannot scroll back to.
     * The sound is the one {@code sendPlayerUnlockNotification} uses, for the same reason.
     *
     * <p><b>The message deliberately promises nothing.</b> Stage 3 moves counters; the damage those
     * tiers are worth is not wired until stage 4, and a notification that advertises a bonus the build
     * does not yet apply is the "config that lies" failure this port keeps having to undo. Worded as
     * a statement of fact it stays true after stage 4 lands, so there is no follow-up edit to forget.
     */
    private static void announceMastery(@NotNull McMMOPlayer mmoPlayer, @NotNull LivingEntity victim,
            int tier, int kills) {
        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_UNLOCKED,
                "Hunter.SubSkill.MobMastery.Proc",
                victim.getType().getName().getString(), String.valueOf(tier),
                String.valueOf(kills));
        SoundManager.sendCategorizedSound(mmoPlayer.getPlayer(), SoundType.SKILL_UNLOCKED,
                SoundCategory.MASTER);
    }

    /** See {@link #LOGGED_FIRST_KILL}. */
    private static void announceFirstCountedKill(@NotNull String mobId, int killsAfter) {
        if (LOGGED_FIRST_KILL.compareAndSet(false, true)) {
            McMMOMod.LOGGER.info("Hunter: mob-mastery counters are live — first counted kill this "
                    + "session was '{}' (now {}).", mobId, killsAfter);
        }
    }

    /**
     * Forget that this session has already logged its first counted kill.
     *
     * <p>Test seam only, and the narrowest one available: {@link #LOGGED_FIRST_KILL} is process-wide
     * static state on a JVM JUnit reuses across classes, so without a reset one test's first kill
     * decides whether another's logs at all.
     */
    static void resetFirstKillLogForTesting() {
        LOGGED_FIRST_KILL.set(false);
    }
}
