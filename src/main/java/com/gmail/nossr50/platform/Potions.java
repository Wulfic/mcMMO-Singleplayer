package com.gmail.nossr50.platform;

import com.gmail.nossr50.datatypes.skills.alchemy.EffectSpec;
import com.gmail.nossr50.datatypes.skills.alchemy.PotionForm;
import com.gmail.nossr50.datatypes.skills.alchemy.PotionSpec;
import com.gmail.nossr50.util.PotionNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Minecraft-facing half of mcMMO's potion handling: resolving config potion/effect names against
 * {@code Registries.POTION} / {@code Registries.STATUS_EFFECT}, reading a Minecraft-free
 * {@link PotionSpec} off a live stack, and writing one back onto a potion item.
 *
 * <p>Phase 2 slice 5 split the old {@code util/PotionUtil} in two: the legacy Bukkit name tables and
 * the {@code strong_}/{@code long_}/{@code water} prefix predicates are pure string work and moved to
 * {@link PotionNames}; everything that touches a registry or a data component is here, inside the
 * sealed {@code platform/} boundary. Alchemy's decision logic — brew stage, potion similarity, the
 * child-transition tree — now runs entirely on {@link PotionSpec} and never sees a Minecraft type.
 *
 * <p>Both registries are populated by {@code Bootstrap.initialize()}, so this stays unit-testable
 * under the {@code fabric-loader-junit} harness (unlike the dynamic enchantment registry). Callers
 * must still be at/after server start: during mod-load static init the registries are empty and every
 * lookup here correctly returns empty rather than crashing.
 */
public final class Potions {

    private Potions() {
    }

    // --- Name → registry resolution ----------------------------------------

    /**
     * Resolve a config potion-type string into its namespaced registry id, applying the
     * {@code strong_}/{@code long_} variant prefixes and falling back to the unprefixed base when the
     * variant does not exist (legacy {@code resolveVariant}).
     *
     * @param partialName the {@code PotionType} string from the config (may be a legacy Bukkit name)
     * @param upgraded    whether the config marks this potion Upgraded (amplified, {@code strong_})
     * @param extended    whether the config marks this potion Extended ({@code long_})
     * @return the namespaced potion id (e.g. {@code minecraft:long_swiftness}), or empty if unknown
     */
    public static @NotNull Optional<String> resolvePotionId(@Nullable String partialName,
            boolean upgraded, boolean extended) {
        return matchPotion(partialName, upgraded, extended).map(Potions::idOf);
    }

    /**
     * Resolve a config effect string into its namespaced registry id, translating legacy Bukkit
     * effect names.
     *
     * @param effectName the effect token from the config (e.g. {@code "SLOW_DIGGING"})
     * @return the namespaced status-effect id (e.g. {@code minecraft:mining_fatigue}), or empty
     */
    public static @NotNull Optional<String> resolveEffectId(@Nullable String effectName) {
        final Holder<MobEffect> entry = matchEffect(effectName);
        return entry == null
                ? Optional.empty()
                : Optional.of(BuiltInRegistries.STATUS_EFFECT.getId(entry.value()).toString());
    }

    /**
     * Resolve a config potion-type string into its (possibly prefixed) potion registry entry.
     * The MC-typed form of {@link #resolvePotionId}, for callers inside {@code platform/}/{@code
     * fabric/} that need the entry itself.
     */
    public static @NotNull Optional<Holder<Potion>> matchPotion(@Nullable String partialName,
            boolean upgraded, boolean extended) {
        // Candidates are most-specific-first (strong_/long_ variant, then the plain base).
        for (String path : PotionNames.variantPaths(partialName, upgraded, extended)) {
            final Optional<Holder<Potion>> entry = lookupPotion(path);
            if (entry.isPresent()) {
                return entry;
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve a config effect string into its status-effect registry entry, translating legacy Bukkit
     * effect names.
     *
     * @return the status-effect entry, or {@code null} if unknown
     */
    public static @Nullable Holder<MobEffect> matchEffect(@Nullable String effectName) {
        if (effectName == null || effectName.isEmpty()) {
            return null;
        }
        final Identifier id = Identifier.ofVanilla(PotionNames.convertLegacyEffectName(effectName));
        return BuiltInRegistries.STATUS_EFFECT.getEntry(id).orElse(null);
    }

    private static @NotNull Optional<Holder<Potion>> lookupPotion(@NotNull String path) {
        // The registry hands back Optional<RegistryEntry.Reference<Potion>>; widen it so callers see
        // the interface rather than the implementation type.
        return BuiltInRegistries.POTION.getEntry(Identifier.ofVanilla(path))
                .map(entry -> (Holder<Potion>) entry);
    }

    private static @NotNull String idOf(@NotNull Holder<Potion> entry) {
        return BuiltInRegistries.POTION.getId(entry.value()).toString();
    }

    // --- Stack ⇄ PotionSpec -------------------------------------------------

    /**
     * The Minecraft-free description of the potion this stack carries, or {@code null} when the stack
     * has no potion-contents component at all (a non-potion item, or a potion item that somehow lost
     * it). Callers treat {@code null} as "not a potion", which is exactly what the pre-seal
     * {@code getPotionContents() == null} branch did.
     */
    public static @Nullable PotionSpec specOf(@NotNull PlatformItem item) {
        final ItemStack stack = item.unwrap();
        final PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return null;
        }

        final String basePotionId = contents.potion().map(Potions::idOf).orElse(null);
        final boolean baseHasEffects = contents.potion()
                .map(base -> !base.value().getEffects().isEmpty())
                .orElse(false);

        final List<EffectSpec> customEffects = new ArrayList<>();
        for (MobEffectInstance effect : contents.customEffects()) {
            customEffects.add(new EffectSpec(
                    BuiltInRegistries.STATUS_EFFECT.getId(effect.getEffectType().value()).toString(),
                    effect.getAmplifier(), effect.getDuration()));
        }

        return new PotionSpec(basePotionId, baseHasEffects, customEffects, formOf(stack));
    }

    /**
     * Write a base potion + custom effects onto a potion stack, replacing any contents it already
     * carries. Ids are the namespaced registry ids produced by {@link #resolvePotionId} /
     * {@link #resolveEffectId}; an id that no longer resolves is skipped rather than crashing the
     * config load, and reported through the returned flag.
     *
     * @return {@code true} if the base potion resolved and the component was written
     */
    public static boolean applyContents(@NotNull PlatformItem item, @NotNull String basePotionId,
            @NotNull List<EffectSpec> effects) {
        final Optional<Holder<Potion>> base = lookupId(basePotionId);
        if (base.isEmpty()) {
            return false;
        }

        final List<MobEffectInstance> instances = new ArrayList<>();
        for (EffectSpec effect : effects) {
            final Holder<MobEffect> type = lookupEffectId(effect.effectId());
            if (type == null) {
                continue; // already logged by the config when it resolved the name.
            }
            instances.add(new MobEffectInstance(type, effect.duration(), effect.amplifier()));
        }

        item.unwrap().set(DataComponents.POTION_CONTENTS, new PotionContents(
                Optional.of(base.get()), Optional.empty(), instances, Optional.empty()));
        return true;
    }

    private static @NotNull Optional<Holder<Potion>> lookupId(@NotNull String namespacedId) {
        final Identifier id = Identifier.tryParse(namespacedId);
        return id == null
                ? Optional.empty()
                : BuiltInRegistries.POTION.getEntry(id).map(entry -> (Holder<Potion>) entry);
    }

    private static @Nullable Holder<MobEffect> lookupEffectId(@NotNull String namespacedId) {
        final Identifier id = Identifier.tryParse(namespacedId);
        return id == null ? null : BuiltInRegistries.STATUS_EFFECT.getEntry(id).orElse(null);
    }

    // --- Item identity ------------------------------------------------------

    /**
     * Which of the three vanilla potion items this stack is. Exact {@code isOf} identity checks, never
     * an id-path comparison: {@code "splash_potion".equals(path)} would match another namespace's
     * item too. Anything that is not a thrown potion is {@link PotionForm#NORMAL}.
     */
    private static @NotNull PotionForm formOf(@NotNull ItemStack stack) {
        if (stack.isOf(Items.SPLASH_POTION)) {
            return PotionForm.SPLASH;
        }
        if (stack.isOf(Items.LINGERING_POTION)) {
            return PotionForm.LINGERING;
        }
        return PotionForm.NORMAL;
    }

    /**
     * Whether the stack is an empty glass bottle — the brewing-stand slot state that is neither an
     * absent bottle nor a brewable potion. Exact item identity, for the same reason as
     * {@link #formOf}.
     */
    public static boolean isGlassBottle(@NotNull PlatformItem item) {
        return item.unwrap().isOf(Items.GLASS_BOTTLE);
    }
}
