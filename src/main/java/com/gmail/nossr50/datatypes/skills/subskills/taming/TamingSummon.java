package com.gmail.nossr50.datatypes.skills.subskills.taming;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable config data for one Call of the Wild summon, ported MC-free from legacy
 * {@code datatypes.skills.subskills.taming.TamingSummon}.
 *
 * <p>Two changes from legacy, both to keep it server-free:
 * <ul>
 *   <li>The summon item is held as its registry-path {@code String} (e.g. {@code "bone"}) rather than a
 *       Bukkit {@code Material} — the MC-typed spawner matches it against a held stack's registry id.</li>
 *   <li>Legacy's {@code EntityType entityType} field and its {@code initEntityType()} ocelot-vs-cat
 *       reflection dance are dropped: the spawner switches on the {@link CallOfTheWildType} directly and
 *       always spawns the modern {@code CatEntity} for {@link CallOfTheWildType#CAT}.</li>
 * </ul>
 *
 * <p>The {@code Math.max(_, 1)} clamps on the amount / entities-summoned / cap are legacy's, preserved:
 * a mis-configured zero or negative would otherwise summon nothing or loop forever.
 */
public class TamingSummon {

    private final CallOfTheWildType callOfTheWildType;
    private final String itemId;
    private final int itemAmountRequired;
    private final int entitiesSummoned;
    private final int summonLifespan;
    private final int summonCap;

    public TamingSummon(@NotNull CallOfTheWildType callOfTheWildType, @NotNull String itemId,
            int itemAmountRequired, int entitiesSummoned, int summonLifespan, int summonCap) {
        this.callOfTheWildType = callOfTheWildType;
        this.itemId = itemId;
        this.itemAmountRequired = Math.max(itemAmountRequired, 1);
        this.entitiesSummoned = Math.max(entitiesSummoned, 1);
        this.summonLifespan = summonLifespan;
        this.summonCap = Math.max(summonCap, 1);
    }

    public @NotNull CallOfTheWildType getCallOfTheWildType() {
        return callOfTheWildType;
    }

    /** The registry path of the item required to summon (e.g. {@code "bone"}). */
    public @NotNull String getItemId() {
        return itemId;
    }

    public int getItemAmountRequired() {
        return itemAmountRequired;
    }

    public int getEntitiesSummoned() {
        return entitiesSummoned;
    }

    /** Seconds a summon lives before it despawns; {@code <= 0} means it never expires. */
    public int getSummonLifespan() {
        return summonLifespan;
    }

    public int getSummonCap() {
        return summonCap;
    }
}
