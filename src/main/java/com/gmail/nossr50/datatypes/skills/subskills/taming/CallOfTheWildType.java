package com.gmail.nossr50.datatypes.skills.subskills.taming;

/**
 * The three animals Call of the Wild can summon. Ported MC-free from legacy
 * {@code datatypes.skills.subskills.taming.CallOfTheWildType}.
 *
 * <p>Legacy derived its config key and display name from a Bukkit {@code EntityType} via
 * {@code StringUtils.getPrettyEntityTypeString}; both are pre-computed here as plain strings so the
 * enum carries no server type. Two names are needed and they differ for {@link #CAT}:
 * <ul>
 *   <li>{@link #getConfigEntityTypeEntry()} — the {@code config.yml} section key. For {@code CAT} this
 *       is {@code "Ocelot"}: the config predates the 1.14 cat/ocelot split and mcMMO never renamed the
 *       section (legacy's own comment: "Even though cats will be summoned in 1.14, we specify Ocelot
 *       here"). This port summons a real {@code CatEntity}, but still reads the {@code Ocelot} config.</li>
 *   <li>{@link #getDisplayName()} — the capitalised animal name shown to the player, legacy's
 *       {@code StringUtils.getCapitalized(type.toString())} (so {@code CAT} shows as {@code "Cat"}).</li>
 * </ul>
 */
public enum CallOfTheWildType {
    WOLF("Wolf", "Wolf"),
    CAT("Ocelot", "Cat"),
    HORSE("Horse", "Horse");

    private final String configEntityTypeEntry;
    private final String displayName;

    CallOfTheWildType(String configEntityTypeEntry, String displayName) {
        this.configEntityTypeEntry = configEntityTypeEntry;
        this.displayName = displayName;
    }

    /** The {@code Skills.Taming.Call_Of_The_Wild.<entry>} config section key for this summon. */
    public String getConfigEntityTypeEntry() {
        return configEntityTypeEntry;
    }

    /** The capitalised animal name shown to the player (legacy {@code getCapitalized(name())}). */
    public String getDisplayName() {
        return displayName;
    }
}
