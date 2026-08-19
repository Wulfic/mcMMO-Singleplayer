package com.gmail.nossr50.platform;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Entity-registry lookups by id string — the entity-side sibling of {@link Materials}.
 *
 * <p>Exists so code that stores creatures as raw id strings (Hunter's per-creature kill map, the mob
 * tier tables) can render and classify them without importing {@code net.minecraft} (Phase 2).
 */
public final class Entities {

    private Entities() {
    }

    /**
     * The localized display name of the entity type with this id (e.g. {@code minecraft:zombie} →
     * "Zombie"), falling back to the raw id when it names no known entity type.
     *
     * <h2>⚠️ {@code Registries.ENTITY_TYPE} is a {@code DefaultedRegistry} and its {@code get} LIES</h2>
     * {@code SimpleDefaultedRegistry#get(Identifier)} answers an <em>unknown</em> id with the
     * registry's default entry — {@code minecraft:pig} — rather than {@code null} (bytecode: it calls
     * {@code SimpleRegistry.get}, tests for null, and substitutes {@code defaultEntry}). That is not
     * hypothetical here: Hunter deliberately stores these keys as raw strings, so a key written by an
     * older version or a since-removed mod would silently render as "Pig".
     *
     * <p>Hence {@code getOptionalValue}, which does not substitute, and an explicit fall back to the
     * raw id — showing the player {@code somemod:wraith} is honest; showing them "Pig" is a lie.
     */
    public static @NotNull String displayName(@NotNull String entityId) {
        final Identifier id = Identifier.tryParse(entityId);
        if (id == null) {
            return entityId;
        }
        return Registries.ENTITY_TYPE.getOrEmpty(id)
                .map(type -> type.getName().getString())
                .orElse(entityId);
    }
}
