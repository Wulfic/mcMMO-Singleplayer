package com.gmail.nossr50.fabric;

import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/**
 * The mod's persistent Fabric data attachments — mcMMO state that has to live on a Minecraft object
 * itself and survive the world being closed and reopened.
 *
 * <h2>Why this exists at all, when {@code MetadataStore} already holds per-entity state</h2>
 * {@link com.gmail.nossr50.platform.MetadataStore} is the port's replacement for Bukkit entity
 * metadata and is deliberately <b>transient</b>: a side table keyed by entity {@link UUID}, dropped
 * when the JVM stops. That is the right shape for what it holds — an arrow's draw force, a rupture
 * timer, a per-attacker award counter — all of which are meaningless a minute later.
 *
 * <p>A data attachment is the opposite bargain, and this is the first thing in the mod that needs
 * it. The value is written into the entity's own NBT ({@code Entity#load} /
 * {@code Entity#saveWithoutId}), so it rides the animal into the region file and back, and it is
 * <b>deleted with the animal</b> — there is nothing to garbage-collect, no file of our own to
 * version, and no way for the table to outlive the things it describes. That last property is what
 * rules out the obvious alternative of a side-car save file next to
 * {@code <worldRoot>/mcmmo/placed_blocks.dat}: nothing ever tells the mod that a calf was killed at
 * five minutes old, so a flat file of animal→breeder rows could only ever grow.
 *
 * <h2>⚠️ These must be registered before any world loads</h2>
 * A persistent attachment is deserialized by looking its identifier up in Fabric's registry. An
 * identifier that is not registered <em>at read time</em> is dropped with a
 * {@code "Skipping invalid attachments"} warning and never comes back, so registering lazily — on
 * first use, say — would silently discard the markers of every animal bred in a previous session,
 * which is the exact failure this class exists to prevent. {@link #register()} is therefore called
 * from {@code McMMOMod#onInitialize}, before any server can start.
 */
public final class McMMOAttachments {

    /**
     * The {@link UUID} of the player who bred an animal — Husbandry's D-H6 "bred by" marker.
     *
     * <p>Husbandry's raise verb pays roughly twenty real minutes after the act it rewards, so the
     * baby has to carry its breeder with it for that whole time. Persisting it is what makes
     * {@code PLAYTEST_G} row HU16 pass: breed a calf, quit to title, reload the world, and the
     * payout still lands when it matures.
     *
     * <p><b>Consumed on read.</b> {@code HusbandryListener} takes the marker off the animal with
     * {@code removeAttached} at the moment it pays, so the once-per-animal rule is a property of the
     * data rather than of a guard someone has to remember — an animal driven back across the
     * baby→adult boundary afterwards has nobody left to credit.
     *
     * <p><b>Not {@code copyOnDeath}, on purpose.</b> Fabric transfers attachments to the new entity
     * instance on cross-world teleportation regardless of that flag, so a calf walked through a
     * nether portal keeps its marker. What {@code copyOnDeath} would additionally cover is entity
     * <em>conversion</em> — a bred piglet struck by lightning, a mooshroom sheared into a cow — and
     * an animal that has stopped being the animal you bred should stop paying you for raising it.
     *
     * <p><b>Not synced.</b> The client never renders or reasons about this; syncing it would put a
     * packet on the wire every time a baby is born for no observable effect.
     *
     * <p>Stored with {@link Uuids#INT_STREAM_CODEC} — the NBT-native four-int form vanilla itself
     * uses for entity UUIDs, and half the size on disk of {@link Uuids#CODEC}'s 36-character string.
     */
    public static final AttachmentType<UUID> BRED_BY = AttachmentRegistry.createPersistent(
            Identifier.of(McMMOMod.MOD_ID, "bred_by"), Uuids.INT_STREAM_CODEC);

    private McMMOAttachments() {
    }

    /**
     * Force this class to initialize, which is what performs the registrations above.
     *
     * <p>A method rather than a comment at the call site because the dependency is invisible
     * otherwise: the constants are read from {@code HusbandryListener}, so without an explicit touch
     * at mod init the registry entries would not exist until the first animal was bred — long after
     * the world holding last session's markers had already been read and discarded them.
     */
    public static void register() {
        // Referencing BRED_BY is the whole point; the assignment keeps the reference from being
        // optimized away and documents that class initialization is the mechanism.
        assert BRED_BY != null;
    }
}
