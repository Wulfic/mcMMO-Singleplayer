package com.gmail.nossr50.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.McTestRegistries;
import com.mojang.serialization.Codec;
import java.util.UUID;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
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

        final NbtElement encoded = codec.encodeStart(NbtOps.INSTANCE, breeder).getOrThrow();
        final UUID decoded = codec.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(breeder, decoded, "the breeder's identity must survive the write/read cycle");
    }

    @Test
    void attachmentIdentifiersLiveUnderTheModsOwnNamespace() {
        assertEquals(McMMOMod.MOD_ID, McMMOAttachments.BRED_BY.identifier().getNamespace(),
                "attachments share one registry across every installed mod; a foreign or default "
                        + "namespace is a collision waiting for the first mod that picks the same "
                        + "path, and the loser is overwritten with only a warning");
    }
}
