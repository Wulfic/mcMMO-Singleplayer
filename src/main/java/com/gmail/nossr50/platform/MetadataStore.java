package com.gmail.nossr50.platform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Transient replacement for Bukkit's entity metadata ({@code get/set/has/removeMetadata},
 * ~50 entity refs). Bukkit metadata has no vanilla equivalent; mcMMO uses it for short-lived
 * flags — mob-origin markers, per-arrow state (bow force, travel distance, tracked/bounce),
 * rupture/dodge trackers, etc. (keys in legacy {@code util/MetadataConstants}).
 *
 * <p>Design (chosen 2026-07-06): a side-table keyed by entity {@link UUID}, NOT persisted. Data
 * lives for the server session and is cleaned when the entity is removed (call {@link #clear}).
 * Values are stored as {@link Object}; typed accessors cast. Thread-safe map so off-thread
 * cleanup is safe, though writes should stay on the server thread.
 *
 * <p>LIMITATION / Phase 5 follow-up: a handful of mob-origin flags in legacy {@code
 * MobMetadataUtils} were stored persistently (PDC/NBT) so a spawner/egg/tamed mob keeps its
 * marker across chunk unload+reload. Those specific keys need the persistence path (Fabric data
 * attachment or entity NBT) built in Phase 5 — do NOT rely on this transient store for them.
 * Block/item metadata (few refs: traveling-block, replant, super-ability-boosted item) are
 * handled by their own adapters, not here.
 */
public final class MetadataStore {

    private MetadataStore() {}

    private static final Map<UUID, Map<String, Object>> ENTITY_DATA = new ConcurrentHashMap<>();

    /** Set a transient flag/value on an entity. */
    public static void set(@NotNull Entity entity, @NotNull String key, @NotNull Object value) {
        ENTITY_DATA.computeIfAbsent(entity.getUuid(), k -> new ConcurrentHashMap<>()).put(key, value);
    }

    /** Set a presence-only flag (Bukkit {@code FixedMetadataValue(plugin, true)} pattern). */
    public static void setFlag(@NotNull Entity entity, @NotNull String key) {
        set(entity, key, Boolean.TRUE);
    }

    public static boolean has(@NotNull Entity entity, @NotNull String key) {
        final Map<String, Object> data = ENTITY_DATA.get(entity.getUuid());
        return data != null && data.containsKey(key);
    }

    /** Raw value, or {@code null} if unset. */
    public static @Nullable Object get(@NotNull Entity entity, @NotNull String key) {
        final Map<String, Object> data = ENTITY_DATA.get(entity.getUuid());
        return data == null ? null : data.get(key);
    }

    /**
     * Typed value, or {@code null} if unset or of the wrong type.
     *
     * @param type expected value class
     */
    public static <T> @Nullable T get(@NotNull Entity entity, @NotNull String key,
            @NotNull Class<T> type) {
        final Object value = get(entity, key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    /** Remove a single key from an entity. */
    public static void remove(@NotNull Entity entity, @NotNull String key) {
        remove(entity.getUuid(), key);
    }

    /*
     * UUID-keyed overloads. The store is a UUID side-table already, so these are the same operations
     * without the Minecraft type. They exist for ported code that holds a
     * {@link PlatformLivingEntity} rather than a raw entity and must stay free of net.minecraft
     * imports to remain unit-testable outside the Knot harness (e.g. {@code RuptureTask}).
     */

    /** Set a transient flag/value on an entity, addressed by {@link UUID}. */
    public static void set(@NotNull UUID entityId, @NotNull String key, @NotNull Object value) {
        ENTITY_DATA.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public static boolean has(@NotNull UUID entityId, @NotNull String key) {
        final Map<String, Object> data = ENTITY_DATA.get(entityId);
        return data != null && data.containsKey(key);
    }

    /** Typed value for an entity id, or {@code null} if unset or of the wrong type. */
    public static <T> @Nullable T get(@NotNull UUID entityId, @NotNull String key,
            @NotNull Class<T> type) {
        final Map<String, Object> data = ENTITY_DATA.get(entityId);
        final Object value = data == null ? null : data.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    /** Remove a single key from an entity, addressed by {@link UUID}. */
    public static void remove(@NotNull UUID entityId, @NotNull String key) {
        final Map<String, Object> data = ENTITY_DATA.get(entityId);
        if (data != null) {
            data.remove(key);
            if (data.isEmpty()) {
                ENTITY_DATA.remove(entityId);
            }
        }
    }

    /** Drop all stored data for an entity. Call when the entity is removed/dies. */
    public static void clear(@NotNull Entity entity) {
        ENTITY_DATA.remove(entity.getUuid());
    }

    /** Clear the entire store (e.g. on server stop). */
    public static void clearAll() {
        ENTITY_DATA.clear();
    }
}
