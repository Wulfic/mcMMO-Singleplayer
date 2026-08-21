package com.gmail.nossr50.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.mobs.MobOrigin;
import com.gmail.nossr50.util.McTestRegistries;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The mod's persistent data attachments, tested at the only level a unit test can reach them.
 *
 * <p>Actually saving a world and reopening it is a {@code PLAYTEST_G} row (HU16), not something this
 * suite can do. What it <em>can</em> pin is every part of the contract that, if broken, would make
 * that play-test fail silently — and each of these has a real failure mode behind it rather than
 * being a restatement of the declaration:
 *
 * <ul>
 *   <li><b>Persistence is declared.</b> {@code AttachmentRegistry.create} and
 *       {@code createPersistent} differ by one word and produce an attachment that behaves
 *       identically for a whole session. The non-persistent one is simply never written to NBT, so
 *       the mistake surfaces only after a reload — the exact scenario HU16 exists to check.</li>
 *   <li><b>The codec survives NBT specifically.</b> A {@code Codec} that works against JSON can
 *       still fail against {@code NbtOps}, and Fabric's serializer swallows a failed entry with a
 *       {@code "Skipping invalid attachments"} warning rather than throwing.</li>
 *   <li><b>The identifier is under the mod's own namespace.</b> Attachments share one flat registry
 *       across every installed mod, and a duplicate id only logs a warning before one silently
 *       overwrites the other.</li>
 * </ul>
 */
class McMMOAttachmentsTest {

    @BeforeAll
    static void bootstrapRegistries() {
        // Identifier.of validates against the registry-name charset, so the game has to be booted
        // far enough for that to exist before the class initializer above can run.
        McTestRegistries.bootstrap();
        McMMOAttachments.register();
    }

    @Test
    void theBredByMarkerIsDeclaredPersistent() {
        assertTrue(McMMOAttachments.BRED_BY.isPersistent(),
                "a non-persistent bred-by marker is never written to the animal's NBT, so every "
                        + "calf bred before a reload silently stops paying its raise XP");
    }

    @Test
    void theBredByMarkerRoundTripsThroughNbt() {
        final Codec<UUID> codec = McMMOAttachments.BRED_BY.persistenceCodec();
        assertNotNull(codec, "a persistent attachment must carry a codec");
        final UUID breeder = UUID.randomUUID();

        final Tag encoded = codec.encodeStart(NbtOps.INSTANCE, breeder).getOrThrow();
        final UUID decoded = codec.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(breeder, decoded, "the breeder's identity must survive the write/read cycle");
    }

    @Test
    void attachmentIdentifiersLiveUnderTheModsOwnNamespace() {
        for (AttachmentType<?> attachment : List.of(McMMOAttachments.BRED_BY,
                McMMOAttachments.MOB_ORIGIN)) {
            assertEquals(McMMOMod.MOD_ID, attachment.identifier().getNamespace(),
                    "attachments share one registry across every installed mod; a foreign or default "
                            + "namespace is a collision waiting for the first mod that picks the same "
                            + "path, and the loser is overwritten with only a warning");
        }
    }

    @Test
    void theMobOriginMarkerIsDeclaredPersistent() {
        assertTrue(McMMOAttachments.MOB_ORIGIN.isPersistent(),
                "a non-persistent mob-origin marker survives only until the world closes, so every "
                        + "spawner mob already loaded would start counting toward Hunter mastery "
                        + "again on the next reload — the gate would appear to work and then quietly "
                        + "stop");
    }

    @Test
    void theMobOriginMarkerRoundTripsThroughNbt() {
        final Codec<String> codec = McMMOAttachments.MOB_ORIGIN.persistenceCodec();
        assertNotNull(codec, "a persistent attachment must carry a codec");

        final Tag encoded = codec.encodeStart(NbtOps.INSTANCE,
                MobOrigin.SPAWNER.storageKey()).getOrThrow();
        final String decoded = codec.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(MobOrigin.SPAWNER, MobOrigin.byName(decoded),
                "the origin must survive the write/read cycle and still resolve");
    }

    @Test
    void theMobOriginMarkerIsStoredAsAStringRatherThanAnEnumCodec() {
        // ⚠️ Not a restatement of the declaration — the failure direction is what matters. Fabric
        // drops an attachment whose codec fails to decode, with only a "Skipping invalid attachments"
        // warning. For this attachment a dropped marker reads as "this mob counts", so a strict enum
        // codec would turn any future rename into a silent re-opening of every farm the gate closes.
        // A String always decodes, and MobOrigins maps anything it cannot interpret to UNKNOWN.
        final Codec<String> codec = McMMOAttachments.MOB_ORIGIN.persistenceCodec();
        assertNotNull(codec);
        assertTrue(codec.parse(NbtOps.INSTANCE, StringTag.of("NOT_A_REAL_ORIGIN")).isSuccess(),
                "an unrecognised stored value must still decode, so that MobOrigins gets the chance "
                        + "to fail closed on it rather than Fabric discarding the marker entirely");
    }
}
