package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * mcMMO's cosmetic particle and firework surface. Singleplayer port of legacy
 * {@code util/skills/ParticleEffectUtils}, and the thing that was missing when the 2026-08-06 wiring
 * audit's item 4(c) found eleven {@code Particles.*} switches in {@code config.yml} with no mechanic
 * behind any of them.
 *
 * <p><b>Layering.</b> Every entry point takes a platform wrapper, never a vanilla type, because the
 * callers are deliberately MC-free — {@code RuptureTask} holds a {@link PlatformLivingEntity} so its
 * timer logic stays unit-testable, and {@code MacesManager} only ever sees the wrapper. This class is
 * the one place the MC particle/entity types are allowed in, mirroring how
 * {@link SoundManager} keeps the registry lookup behind {@code PlatformPlayer#playSound}.
 *
 * <p><b>Every method is a no-op unless its {@code Particles.*} switch is on</b>, and every method is
 * a no-op off the server thread's {@link ServerWorld} — a client-side {@code World} cannot spawn
 * server particles, and the callers (combat, block-break, task ticks) all run server-side.
 *
 * <h2>What legacy's Bukkit effects map to on 1.21.11</h2>
 *
 * <p>Bukkit's {@code World#playEffect(Location, Effect, data)} is a different shape from vanilla's
 * {@link ServerWorld#spawnParticles}: Bukkit spawned <em>one</em> particle per call at an exact
 * point, so legacy scattered an effect by calling it repeatedly at hand-picked offsets — nine fixed
 * {@code BlockFace} directions for smoke, and an eleven-way random jitter around the eye position for
 * bleed and cripple ({@code getParticleLocation}). {@code spawnParticles} takes a count and a
 * per-axis spread directly, so both collapse into a single call each. That is a rewrite of the
 * scatter, not of the effect: the same particle type appears in the same volume.
 *
 * <ul>
 *   <li>{@code Effect.STEP_SOUND} + {@code Material.REDSTONE_WIRE} (bleed) →
 *       {@link ParticleTypes#BLOCK} carrying redstone wire's block state — vanilla's block-break
 *       particle, which is what Bukkit's {@code STEP_SOUND} spawned.</li>
 *   <li>{@code Effect.MOBSPAWNER_FLAMES} (flux, call of the wild) → {@link ParticleTypes#FLAME}.</li>
 *   <li>{@code Effect.SMOKE} (dodge) → {@link ParticleTypes#SMOKE}.</li>
 *   <li>{@code Effect.ANVIL_BREAK} (cripple) → {@link ParticleTypes#BLOCK} carrying the anvil's block
 *       state, alongside the {@link SoundType#CRIPPLE} anvil sound legacy already played.</li>
 * </ul>
 *
 * <p><b>DEVIATION — Greater Impact does not create a real explosion.</b> Legacy called
 * {@code World#createExplosion(x, y, z, 0F, false, false)}: a zero-power explosion, used purely for
 * its sound and particle. On 1.21.11 that call still builds an {@code Explosion}, resolves a damage
 * source and sweeps the entity list, all to render one puff on a hot combat path. This port spawns
 * {@link ParticleTypes#EXPLOSION} and plays the explosion sound directly — identical from the
 * player's seat, with no entity iteration and no chance of a future MC version giving a power-0
 * explosion a side effect.
 */
public final class ParticleEffectUtils {

    /** Legacy's {@code offSetVal}: the radius its eleven-way jitter scattered a particle within. */
    private static final double IMPACT_SPREAD = 0.3D;

    /**
     * {@link MetadataStore} flag marking a firework this class spawned, so
     * {@code FireworkRocketEntityMixin} knows to cancel its damage. Replaces legacy's
     * {@code mcMMO.funfettiMetadataKey}.
     */
    public static final String COSMETIC_FIREWORK_KEY = "mcmmo:cosmetic_firework";

    private ParticleEffectUtils() {
    }

    /**
     * The switch gate every entry point runs first.
     *
     * <p>{@link McMMOMod#getGeneralConfig()} is {@code @Nullable} — it is unbound before the server
     * finishes starting, and in unit tests. That matters here more than it does for most callers:
     * {@code RuptureTask} is deliberately MC-free so its timer logic can be unit-tested against a
     * mocked {@link PlatformLivingEntity}, and it now calls {@link #playBleedEffect} on every damage
     * tick. Dereferencing the config directly would have turned a cosmetic into an NPE inside a
     * scheduled task. Mirrors {@code SoundManager#readyConfig}.
     */
    private static boolean disabled(@NotNull Predicate<GeneralConfig> gate) {
        final GeneralConfig config = McMMOMod.getGeneralConfig();
        return config == null || !gate.test(config);
    }

    /**
     * Rupture's bleed puff, once per damage tick. Legacy {@code playBleedEffect}.
     */
    public static void playBleedEffect(@Nullable PlatformLivingEntity livingEntity) {
        if (livingEntity == null || disabled(GeneralConfig::getBleedEffectEnabled)) {
            return;
        }
        spawnAtEyes(livingEntity,
                () -> new BlockStateParticleEffect(ParticleTypes.BLOCK,
                        Blocks.REDSTONE_WIRE.getDefaultState()),
                10);
    }

    /**
     * Maces' Cripple proc. Legacy {@code playCrippleEffect} — the anvil sound plus an anvil-break
     * puff.
     *
     * <p>Legacy's third arm, a {@code Maces.SubSkill.Cripple.Proc} chat line to a <em>player</em>
     * target, is dropped: it fired only when the crippled entity was itself a player, which cannot
     * happen in singleplayer. The attacker's own {@code Cripple.Activated} message is unrelated and
     * is still sent by {@code MacesManager}.
     */
    public static void playCrippleEffect(@Nullable PlatformLivingEntity livingEntity) {
        if (livingEntity == null || disabled(GeneralConfig::getCrippleEffectEnabled)) {
            return;
        }
        spawnAtEyes(livingEntity,
                () -> new BlockStateParticleEffect(ParticleTypes.BLOCK,
                        Blocks.ANVIL.getDefaultState()),
                20);
    }

    /** Acrobatics' Dodge. Legacy {@code playDodgeEffect} → {@code playSmokeEffect}. */
    public static void playDodgeEffect(@Nullable PlatformPlayer player) {
        if (player == null || disabled(GeneralConfig::getDodgeEffectEnabled)) {
            return;
        }
        final ServerWorld world = serverWorldOf(player);
        if (world != null) {
            playSmokeEffect(world, player.getPos());
        }
    }

    /** Axes' Greater Impact and Taming's Fast Food Service knockback. Legacy {@code
     * playGreaterImpactEffect} — see the class note on why this is not a real explosion. */
    public static void playGreaterImpactEffect(@Nullable PlatformLivingEntity livingEntity) {
        if (livingEntity == null || disabled(GeneralConfig::getGreaterImpactEffectEnabled)) {
            return;
        }
        final ServerWorld world = serverWorldOf(livingEntity);
        if (world == null) {
            return;
        }
        final Vec3d eyes = eyePosition(livingEntity);
        world.spawnParticles(ParticleTypes.EXPLOSION, eyes.x, eyes.y, eyes.z, 1, 0D, 0D, 0D, 0D);
        world.playSound(null, eyes.x, eyes.y, eyes.z,
                net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /** Taming's Call of the Wild, at the summoned mob. Legacy {@code playCallOfTheWildEffect}. */
    public static void playCallOfTheWildEffect(@Nullable PlatformLivingEntity livingEntity) {
        if (livingEntity == null || disabled(GeneralConfig::getCallOfTheWildEffectEnabled)) {
            return;
        }
        final ServerWorld world = serverWorldOf(livingEntity);
        if (world == null) {
            return;
        }
        final Vec3d eyes = eyePosition(livingEntity);
        world.spawnParticles(ParticleTypes.FLAME, eyes.x, eyes.y, eyes.z,
                10, 0.2D, 0.2D, 0.2D, 0.01D);
    }

    /**
     * The firework shower on super-ability activation. Legacy declared
     * {@code Particles.Ability_Activation} and never read it — see {@link #spawnFirework}.
     */
    public static void playAbilityEnabledEffect(@Nullable PlatformPlayer player) {
        if (player == null || disabled(GeneralConfig::getAbilityActivationEffectEnabled)) {
            return;
        }
        spawnFirework(player, FIREWORK_GREEN);
    }

    /** The firework shower on super-ability expiry. Legacy {@code playAbilityDisabledEffect}. */
    public static void playAbilityDisabledEffect(@Nullable PlatformPlayer player) {
        if (player == null || disabled(GeneralConfig::getAbilityDeactivationEffectEnabled)) {
            return;
        }
        spawnFirework(player, FIREWORK_RED);
    }

    /**
     * The milestone firework, every {@code Particles.LevelUp_Tier} levels of a single skill.
     *
     * @param newLevel the level just reached, which is what decides whether this is a tier boundary
     */
    public static void playLevelUpEffect(@Nullable PlatformPlayer player, int newLevel) {
        if (player == null || disabled(GeneralConfig::getLevelUpEffectsEnabled)) {
            return;
        }
        if (!isMilestoneLevel(newLevel, McMMOMod.getGeneralConfig().getLevelUpEffectsTier())) {
            return;
        }
        spawnFirework(player, FIREWORK_BLUE);
    }

    /**
     * Whether reaching {@code newLevel} crosses a firework milestone at every {@code tier} levels.
     * Split out from {@link #playLevelUpEffect} so the arithmetic is unit-testable without a world.
     *
     * <p>Guards {@code tier < 1} rather than trusting the config: the value is a ModMenu slider, and
     * {@code newLevel % 0} throws {@link ArithmeticException}. {@code GeneralConfig}'s load-time
     * validation warns about a sub-1 tier but does not correct it, so a hand-edited {@code 0} would
     * otherwise crash the level-up path — which runs inside an XP award.
     */
    static boolean isMilestoneLevel(int newLevel, int tier) {
        return tier >= 1 && newLevel >= 1 && newLevel % tier == 0;
    }

    /** Legacy's nine-{@code BlockFace} smoke burst, collapsed into one spread-based call. */
    private static void playSmokeEffect(@NotNull ServerWorld world, @NotNull Vec3d position) {
        world.spawnParticles(ParticleTypes.SMOKE, position.x, position.y + 0.5D, position.z,
                9, 0.3D, 0.3D, 0.3D, 0.02D);
    }

    // Legacy's fireworkParticleShower took a Bukkit Color; these are the packed RGB equivalents.
    private static final int FIREWORK_GREEN = 0x00FF00;
    private static final int FIREWORK_RED = 0xFF0000;
    private static final int FIREWORK_BLUE = 0x0000FF;

    /**
     * Spawns a single cosmetic firework at {@code player}'s feet that detonates on its next tick.
     *
     * <p><b>⚠️ A firework rocket is not cosmetic by default.</b> {@code FireworkRocketEntity#explode}
     * deals {@code 5 + 2 × explosions} damage to its shooter and to every {@code LivingEntity} within
     * five blocks, so a naive "spawn a firework on level-up" hurts the player it is congratulating.
     * That is why nothing is passed as the rocket's shooter, and why
     * {@code FireworkRocketEntityMixin} cancels the damage half of the detonation for rockets this
     * method spawned. The visual is unaffected: {@code explodeAndRemove} sends the client its burst
     * (entity status {@code 17}) <em>before</em> calling {@code explode}, which is pure damage.
     *
     * <p>Legacy intended exactly this — its commented-out {@code fireworkParticleShower} tagged the
     * firework with a {@code funfettiMetadataKey} so a listener could cancel the damage. That code
     * never shipped; the key it names no longer exists on upstream's {@code mcMMO} class, so the
     * fragment would not compile. The five firework knobs ({@code Ability_Activation},
     * {@code Ability_Deactivation}, {@code LevelUp_Enabled}, {@code LevelUp_Tier},
     * {@code LargeFireworks}) have therefore been dead in upstream for as long as the comment has.
     */
    private static void spawnFirework(@NotNull PlatformPlayer player, int colour) {
        final ServerWorld world = serverWorldOf(player);
        if (world == null) {
            return;
        }

        final FireworkExplosionComponent.Type shape =
                McMMOMod.getGeneralConfig().getLargeFireworks()
                        ? FireworkExplosionComponent.Type.LARGE_BALL
                        : FireworkExplosionComponent.Type.SMALL_BALL;
        final FireworkExplosionComponent explosion = new FireworkExplosionComponent(
                shape, IntList.of(colour), IntList.of(), true, false);

        final ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        // Flight duration 0 => detonates on the tick after it spawns, i.e. at the player rather than
        // somewhere above them. Legacy used the same value (fireworkMeta.setPower(0)).
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(0, List.of(explosion)));

        final Vec3d pos = player.getPos();
        // The (World, ItemStack, x, y, z, shotAtAngle) constructor leaves the shooter null, so the
        // rocket is owned by nobody -- one of the two things keeping this harmless.
        final FireworkRocketEntity firework =
                new FireworkRocketEntity(world, rocket, pos.x, pos.y, pos.z, true);
        MetadataStore.setFlag(firework, COSMETIC_FIREWORK_KEY);
        world.spawnEntity(firework);
    }

    /**
     * ⚠️ {@code effect} is a {@link Supplier}, and that is not a style choice.
     *
     * <p>Building a {@link BlockStateParticleEffect} touches {@code Blocks}, which initialises
     * {@code Registries} and throws {@code "Not bootstrapped"} outside a registry-backed test. Java
     * evaluates arguments eagerly, so passing the effect by value ran that initialiser <em>before</em>
     * this method could check for a server world — which broke {@code MacesManagerTest}, an ordinary
     * MC-free unit test, the moment {@code MacesManager} started calling
     * {@link #playCrippleEffect}. Deferring construction until after the world check keeps every
     * caller safe to unit-test against mocks, which is the whole reason the managers hold platform
     * wrappers in the first place.
     */
    private static void spawnAtEyes(@NotNull PlatformLivingEntity livingEntity,
            @NotNull Supplier<ParticleEffect> effect, int count) {
        final ServerWorld world = serverWorldOf(livingEntity);
        if (world == null) {
            return;
        }
        final Vec3d eyes = eyePosition(livingEntity);
        world.spawnParticles(effect.get(), eyes.x, eyes.y, eyes.z, count,
                IMPACT_SPREAD, IMPACT_SPREAD, IMPACT_SPREAD, 0D);
    }

    private static @NotNull Vec3d eyePosition(@NotNull PlatformLivingEntity livingEntity) {
        return livingEntity.unwrap().getEyePos();
    }

    private static @Nullable ServerWorld serverWorldOf(@Nullable PlatformLivingEntity entity) {
        return entity != null && entity.getWorld() instanceof ServerWorld world ? world : null;
    }

    private static @Nullable ServerWorld serverWorldOf(@Nullable PlatformPlayer player) {
        return player == null ? null : player.getWorld();
    }
}
