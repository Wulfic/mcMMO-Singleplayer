package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * <b>Proves an AFK armadillo brush farm still pays zero</b> — the half {@code HusbandryListenerTest}
 * structurally cannot reach.
 *
 * <h2>Why this file exists on this band and not on the newest one</h2>
 * Vanilla ships a dispenser that brushes armadillos. On newer versions the scute arrives through
 * {@code LivingEntity#forEachBrushedItem}, which <em>takes the brusher as a parameter</em>, and the
 * dispenser passes {@code null} there — so mcMMO's exclusion is a property of the signature, and a
 * unit test can feed that {@code null} directly and watch it pay nothing.
 *
 * <p><b>Neither of those is true here.</b> {@code forEachBrushedItem} does not exist on this band;
 * {@code brushScute()} takes no arguments and inlines the drop. So {@code ArmadilloBrushMixin} hooks
 * {@code interactMob}, which the dispenser never enters — and that makes the exclusion a property of
 * the <em>call graph</em>, which no Mockito test can observe. Both halves of it are pinned here
 * instead:
 *
 * <ol>
 *   <li>{@link #vanillasBrushingDispenserNeverEntersInteractMob()} — the Minecraft half, read out of
 *       this band's own bytecode rather than believed from a comment.</li>
 *   <li>{@link #theBrushVerbIsHookedOnInteractMobAndNotOnTheHarvestItself()} — the mcMMO half. Moving
 *       the injector down onto {@code brushScute} would leave every listener test green and quietly
 *       start paying dispensers, which is exactly the shape of regression this project keeps
 *       catching late.</li>
 * </ol>
 *
 * <p><b>Anti-vacuity.</b> A scan that finds nothing must fail, not pass: "no dispenser calls
 * {@code brushScute}" and "the scan is broken" render identically otherwise. Every assertion below is
 * therefore preceded by a positive one that proves the scan located real material first.
 */
class ArmadilloBrushDispenserExclusionTest {

    private static final String DISPENSER_PACKAGE = "net/minecraft/block/dispenser/";

    /** The anonymous {@code DispenserBehavior$N} population is comfortably inside this range. */
    private static final int MAX_ANONYMOUS_INNER = 64;

    private static final Path MIXIN_SOURCE = Path.of("src", "main", "java", "com", "gmail",
            "nossr50", "fabric", "mixin", "ArmadilloBrushMixin.java");

    // --- 1. The Minecraft half: the dispenser's call graph ----------------------------------------

    @Test
    void vanillasBrushingDispenserNeverEntersInteractMob() {
        final List<String> scanned = new ArrayList<>();
        final List<String> brushers = new ArrayList<>();

        for (int i = 1; i <= MAX_ANONYMOUS_INNER; i++) {
            final String name = DISPENSER_PACKAGE + "DispenserBehavior$" + i + ".class";
            final byte[] bytes = classBytes(name);
            if (bytes == null) {
                continue;
            }
            scanned.add(name);
            if (references(bytes, "brushScute")) {
                brushers.add(name);
            }
        }

        // Anti-vacuity, in two steps. A classloader that serves no Minecraft resources at all would
        // otherwise sail through every assertion below by finding nothing to contradict them.
        assertTrue(scanned.size() >= 10, () -> "only " + scanned.size() + " DispenserBehavior inner "
                + "classes could be read off the classpath. The scan is broken, so nothing it reports "
                + "about the brushing dispenser means anything.");
        assertEquals(1, brushers.size(), () -> "expected exactly one DispenserBehavior inner class to "
                + "call brushScute (vanilla's armadillo-brushing dispenser); found " + brushers
                + " among " + scanned.size() + " scanned. Zero means this guard proves nothing; more "
                + "than one means there is a second automation path the brush verb has never been "
                + "checked against.");

        // The property itself. The dispenser reaches the harvest directly, so a hook on brushScute
        // would pay it -- and it never reaches interactMob, so the hook we actually use cannot.
        final byte[] dispenser = classBytes(brushers.get(0));
        assertFalse(references(dispenser, "interactMob"), () -> brushers.get(0) + " now references "
                + "interactMob. ArmadilloBrushMixin's brush verb is hooked there precisely because "
                + "this dispenser could not reach it, so an AFK brush farm may now be earning "
                + "Husbandry XP. Re-derive the seam before touching this assertion.");
    }

    // --- 2. The mcMMO half: where the verb is hooked ----------------------------------------------

    @Test
    void theBrushVerbIsHookedOnInteractMobAndNotOnTheHarvestItself() {
        final String source = read(MIXIN_SOURCE);

        assertTrue(source.contains("@Mixin(ArmadilloEntity.class)"),
                () -> MIXIN_SOURCE + " is not the armadillo mixin any more; this guard is reading the "
                        + "wrong file and proves nothing.");
        assertTrue(source.contains("HusbandryListener.onArmadilloBrushed"),
                () -> "the brush verb no longer calls onArmadilloBrushed from " + MIXIN_SOURCE
                        + ". Re-point this guard at whatever replaced it -- do not delete it.");

        // Every injector in the file must select interactMob. brushScute is the harvest itself, and
        // it is what vanilla's dispenser calls: an injector naming it is reachable from automation.
        assertFalse(source.contains("\"brushScute\""), () -> "an injector in " + MIXIN_SOURCE
                + " now selects brushScute. That is the method vanilla's armadillo dispenser calls "
                + "directly, so the brush verb would pay an AFK farm. The verb is hooked on "
                + "interactMob on this band for exactly this reason.");
        assertTrue(source.contains("method = \"interactMob("), () -> "no injector in " + MIXIN_SOURCE
                + " selects interactMob. The dispenser exclusion on this band is the fact that the "
                + "dispenser cannot enter that method; hooking anything else forfeits it.");
    }

    // --- Plumbing ---------------------------------------------------------------------------------

    /** The raw class file, or {@code null} if the classpath has no such resource. */
    private static byte[] classBytes(String resourceName) {
        try (InputStream in = ArmadilloBrushDispenserExclusionTest.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + resourceName, e);
        }
    }

    /**
     * Whether this class file names {@code member} anywhere.
     *
     * <p>A raw scan of the class file's bytes rather than a constant-pool walk: every method a class
     * calls appears in its pool as a modified-UTF8 entry, and for an ASCII identifier that is a plain
     * byte sequence. It can only ever over-report — a name present without being called — which is
     * the safe direction for a guard whose failure means "stop and re-derive the seam".
     */
    private static boolean references(byte[] classFile, String member) {
        final byte[] needle = member.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= classFile.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (classFile[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file.toAbsolutePath(), e);
        }
    }
}
