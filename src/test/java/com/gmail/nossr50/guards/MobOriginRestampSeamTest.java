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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * <b>Proves the mob-origin re-stamp still lands after the NBT read that erases it</b> — the
 * {@code mc/1.21.4} band defect from TODO 8.1a.A, item A4.
 *
 * <h2>What broke, and why no ordinary test can see it</h2>
 * This band's newest available fabric-api pins {@code data-attachment-api 1.6.2}, whose
 * {@code fabric_readAttachmentsFromNbt} assigns the deserialized map <b>unconditionally</b>; later
 * releases early-return when it is null. {@code deserializeAttachmentData} returns null when the NBT
 * carries no attachments, so on this band {@code Entity#readNbt} <b>wipes the whole attachment
 * map</b>. mcMMO stamps {@code MOB_ORIGIN} at {@code EntityType#create(World, SpawnReason)}, which
 * runs <em>before</em> the NBT read on every NBT-carrying spawn — so {@code /summon}, spawners and
 * chunk load all produced a mob reading as {@code NATURAL} and Hunter's anti-farm gate stood open.
 *
 * <p>The defect lives in <b>fabric-api's bytecode</b> and the fix lives in <b>an injection point</b>.
 * No Mockito test can reach either: mocking {@code Entity} gives you an attachment map that behaves
 * correctly, which is precisely the thing that is false here. Every static gate the project already
 * runs is blind to it too — the build compiles, the mixin resolves, {@code boot-check} logs no ERROR
 * and {@code mixin-allow-audit} reports its counts happily. It was found by playing.
 *
 * <p>So this guard pins the two facts the fix actually rests on, from the two authorities that can
 * still see them:
 *
 * <ol>
 *   <li><b>Minecraft's half, read from this band's own bytecode</b> — the NBT read really does
 *       complete inside {@code EntityType#getEntityFromNbt}, which is what makes injecting at its
 *       RETURN <em>ordering-proof</em> rather than merely ordered.</li>
 *   <li><b>mcMMO's half, read from source</b> — an injector selects that method at RETURN, and
 *       nothing selects {@code Entity#readNbt}, the racy alternative that was deliberately
 *       rejected.</li>
 * </ol>
 *
 * <h2>⚠️ Why the bytecode half needs a call-graph walk and not a string scan</h2>
 * {@code getEntityFromNbt}'s own body contains <b>no</b> reference to {@code readNbt}. Its body is
 * {@code fromNbt(nbt).map(...)} then {@code Util.ifPresentOrElse(...)}, and the read happens inside
 * the {@code Consumer} lambda — a synthetic method whose name on this band is {@code method_17839},
 * an unmapped yarn identifier that is not stable across versions and must never be hardcoded. A scan
 * of the method's instructions alone would therefore report "no read here" and be wrong; a scan of
 * the whole class file would report "a read somewhere in EntityType" and be unfalsifiable. The walk
 * below follows {@code invokedynamic} bootstrap handles into EntityType's own lambdas, which is the
 * only formulation that answers the actual question.
 *
 * <h2>Anti-vacuity</h2>
 * A detector that always answered "yes" would pass {@link #theNbtReadCompletesInsideGetEntityFromNbt()}
 * for free — this project has now shipped six guards that did exactly that. It is pinned from both
 * sides: {@link #theSpawnFactoryDoesNotItselfReadNbt()} drives the <em>same</em> walk against the
 * <em>other</em> method and requires the opposite answer, so a stuck detector fails one test or the
 * other whichever way it is stuck. That converse is not decoration — it is also the ordering premise
 * restated, because "create does not read, getEntityFromNbt does" is exactly why the first stamp is
 * written before the erasure and has to be written again after it.
 */
class MobOriginRestampSeamTest {

    private static final String ENTITY_TYPE = "net/minecraft/entity/EntityType";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String READ_NBT = "readNbt";

    private static final String GET_FROM_NBT = "getEntityFromNbt";
    private static final String GET_FROM_NBT_DESC =
            "(Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/SpawnReason;)Ljava/util/Optional;";

    private static final String CREATE = "create";
    private static final String CREATE_DESC =
            "(Lnet/minecraft/world/World;Lnet/minecraft/entity/SpawnReason;)"
                    + "Lnet/minecraft/entity/Entity;";

    private static final Path MIXIN_DIR =
            Path.of("src", "main", "java", "com", "gmail", "nossr50", "fabric", "mixin");
    private static final Path RESTAMP_MIXIN = MIXIN_DIR.resolve("EntityTypeSpawnOriginMixin.java");

    // --- 1. Minecraft's half: the read really does finish before the method returns ---------------

    @Test
    void theNbtReadCompletesInsideGetEntityFromNbt() {
        final ClassNode entityType = read(ENTITY_TYPE);

        // Anti-vacuity: an empty or wrong class would satisfy every negative claim below for free.
        assertTrue(entityType.methods.size() >= 20,
                () -> "only " + entityType.methods.size() + " methods parsed out of " + ENTITY_TYPE
                        + ". The class is not being read, so nothing this test reports means "
                        + "anything.");

        final Set<MethodNode> reached = closure(entityType, find(entityType, GET_FROM_NBT,
                GET_FROM_NBT_DESC));

        // Anti-vacuity: the read is inside a lambda, so a walk that never left the root method
        // would report absence and look like a real finding. Require that it followed at least one.
        assertTrue(reached.size() >= 2,
                () -> "the call-graph walk reached only " + reached.size() + " method(s) from "
                        + GET_FROM_NBT + ". On this band the NBT read lives in a synthetic lambda, so "
                        + "a walk that did not follow invokedynamic cannot see it and would report a "
                        + "false absence. Fix the walk before trusting either verdict.");

        assertTrue(invokes(reached, ENTITY, READ_NBT),
                () -> "no call to " + ENTITY + "#" + READ_NBT + " is reachable inside "
                        + GET_FROM_NBT + " any more (walked " + describe(reached) + ").\n"
                        + "EntityTypeSpawnOriginMixin#mcmmo$restampAfterNbtRead injects at this "
                        + "method's RETURN specifically because the read completes before it, which "
                        + "is what makes the re-stamp ordering-proof instead of a race against "
                        + "fabric's own injector on Entity#readNbt. If the read has moved, the "
                        + "re-stamp may now run BEFORE the erasure and silently do nothing -- every "
                        + "spawner and /summon mob would read as NATURAL and Hunter's anti-farm gate "
                        + "would be open again, with no error anywhere. Re-derive the seam; do not "
                        + "relax this assertion.");
    }

    /**
     * The converse, and the ordering premise restated. Driving the same walk against
     * {@code create(World, SpawnReason)} must reach the opposite answer — that is what proves the
     * detector discriminates rather than always agreeing, and it is also the reason a single stamp
     * at creation was never enough on this band.
     */
    @Test
    void theSpawnFactoryDoesNotItselfReadNbt() {
        final ClassNode entityType = read(ENTITY_TYPE);
        final Set<MethodNode> reached = closure(entityType, find(entityType, CREATE, CREATE_DESC));

        assertFalse(reached.isEmpty(), () -> "the walk reached no methods at all from " + CREATE
                + "; it is broken, so its 'no read here' verdict is meaningless.");

        assertFalse(invokes(reached, ENTITY, READ_NBT),
                () -> CREATE + " now reaches " + ENTITY + "#" + READ_NBT + " within EntityType "
                        + "(walked " + describe(reached) + "). That inverts the ordering this whole "
                        + "seam is built on: mcmmo$stampSpawnOrigin fires at create's RETURN, so if "
                        + "the read now happens inside create, the stamp is erased AFTER it is "
                        + "written and the re-stamp at getEntityFromNbt may be redundant, "
                        + "insufficient, or both. Re-derive the seam from the disassembly.");
    }

    /**
     * {@code allow = 1} on the re-stamp injector is a claim about this method's shape. Mixin's own
     * audit checks the declared count reproduces; this pins <em>why</em> the number is 1, so a future
     * version that grows a second exit path fails here with the reason attached rather than in a
     * bare count mismatch.
     */
    @Test
    void getEntityFromNbtStillHasExactlyOneReturn() {
        final ClassNode entityType = read(ENTITY_TYPE);
        final MethodNode target = find(entityType, GET_FROM_NBT, GET_FROM_NBT_DESC);

        final long returns = Stream.of(target.instructions.toArray())
                .filter(i -> i.getOpcode() == Opcodes.ARETURN)
                .count();

        assertEquals(1, returns, () -> GET_FROM_NBT + " now has " + returns + " return instruction(s), "
                + "not 1. mcmmo$restampAfterNbtRead declares allow = 1 at @At(\"RETURN\"), so it will "
                + "either fail to apply or bind fewer sites than exist -- and an early return that "
                + "skips the re-stamp is a silently open anti-farm gate.");
    }

    // --- 2. mcMMO's half: where the re-stamp is hooked ---------------------------------------------

    @Test
    void theRestampInjectorSelectsGetEntityFromNbtAtReturn() {
        // ⚠️ CODE ONLY. This file is ~70% javadoc, and the prose names every symbol asserted below --
        // including {@link #mcmmo$restampAfterNbtRead}. Scanning the raw text made the "the re-stamp
        // method is gone" assertion satisfiable BY THE COMMENT THAT DESCRIBES IT: deleting the whole
        // injector left the javadoc mention behind and that check still passed. Caught by mutation,
        // not by review, which is the only reason it is not still in here.
        final String source = stripComments(readSource(RESTAMP_MIXIN));

        // Anti-vacuity: prove we are reading the file we think we are before asserting about it.
        assertTrue(source.contains("@Mixin(EntityType.class)"),
                () -> RESTAMP_MIXIN + " is not the EntityType mixin any more; this guard is reading "
                        + "the wrong file and proves nothing.");

        assertTrue(source.contains("method = \"" + GET_FROM_NBT + "("),
                () -> "no injector in " + RESTAMP_MIXIN + " selects " + GET_FROM_NBT + " any more.\n"
                        + "That injector IS the fix for the 1.21.4 mob-origin defect: this band's "
                        + "data-attachment-api 1.6.2 wipes the attachment map during Entity#readNbt, "
                        + "so the stamp written at create() is destroyed and must be written again "
                        + "after the read. Removing it restores the defect, and nothing else in the "
                        + "suite will notice.");
        assertTrue(source.contains("mcmmo$restampAfterNbtRead"),
                () -> "the re-stamp method is gone from " + RESTAMP_MIXIN + ". See above -- this is "
                        + "the fix, not an optimisation.");
        assertTrue(source.contains("MobOrigins.stampOnSpawn"),
                () -> RESTAMP_MIXIN + " no longer calls MobOrigins.stampOnSpawn, so whatever the "
                        + "injector is doing now, it is not stamping the origin.");
        assertTrue(source.contains("@At(\"RETURN\")"),
                () -> "no @At(\"RETURN\") remains in " + RESTAMP_MIXIN + ". The re-stamp is only "
                        + "ordering-proof at the RETURN of getEntityFromNbt -- anywhere earlier and "
                        + "it races the very read it exists to survive.");
    }

    /**
     * The rejected alternative, kept rejected. A second injector on {@code Entity#readNbt} is the
     * obvious general fix and lands on the same target as fabric's own injector, where the winner is
     * decided by cross-mod mixin priority. When it loses, nothing throws and nothing logs — the farm
     * simply works again.
     */
    @Test
    void nothingInjectsIntoEntityReadNbt() {
        // Code only -- EntityTypeSpawnOriginMixin's javadoc explains at length why it does NOT
        // inject into readNbt, and a raw scan would read that explanation as the offence.
        final List<Path> offenders = mixinSources().stream()
                .filter(p -> stripComments(readSource(p)).contains("method = \"" + READ_NBT))
                .collect(Collectors.toList());

        // Anti-vacuity: a glob that matched nothing would make the emptiness below meaningless.
        final List<Path> all = mixinSources();
        assertTrue(all.size() >= 10, () -> "only " + all.size() + " mixin sources found under "
                + MIXIN_DIR + "; the scan is broken, so 'nobody injects into readNbt' is not a "
                + "finding.");

        assertTrue(offenders.isEmpty(), () -> "an injector now selects " + READ_NBT + " in "
                + offenders + ".\nThat target is shared with fabric's data-attachment injector and "
                + "the ordering between them is cross-mod mixin priority, which is not a guarantee "
                + "worth betting a silent exploit gate on. The re-stamp is at getEntityFromNbt's "
                + "RETURN -- strictly outside readNbt -- precisely so it is ordering-proof rather "
                + "than merely ordered.");
    }

    // --- Plumbing ----------------------------------------------------------------------------------

    /**
     * Every method in {@code cn} reachable from {@code root} <em>without leaving the class</em>,
     * following both ordinary calls and {@code invokedynamic} bootstrap handles.
     *
     * <p>The lambda following is the part that matters: {@code javac} compiles a lambda body to a
     * synthetic method on the enclosing class and references it from the indy instruction's bootstrap
     * arguments, so a walk that only reads {@link MethodInsnNode} sees an {@code Optional.map} call
     * and stops exactly where the interesting code begins.
     */
    private static Set<MethodNode> closure(ClassNode cn, MethodNode root) {
        final Set<MethodNode> seen = new LinkedHashSet<>();
        final Deque<MethodNode> queue = new ArrayDeque<>();
        queue.add(root);
        seen.add(root);

        while (!queue.isEmpty()) {
            final MethodNode current = queue.poll();
            for (AbstractInsnNode insn : current.instructions) {
                if (insn instanceof MethodInsnNode call && cn.name.equals(call.owner)) {
                    enqueue(cn, seen, queue, call.name, call.desc);
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (Object arg : indy.bsmArgs) {
                        if (arg instanceof Handle h && cn.name.equals(h.getOwner())) {
                            enqueue(cn, seen, queue, h.getName(), h.getDesc());
                        }
                    }
                }
            }
        }
        return seen;
    }

    private static void enqueue(ClassNode cn, Set<MethodNode> seen, Deque<MethodNode> queue,
            String name, String desc) {
        for (MethodNode m : cn.methods) {
            if (m.name.equals(name) && m.desc.equals(desc) && seen.add(m)) {
                queue.add(m);
            }
        }
    }

    /** Whether any method in {@code methods} calls {@code owner#name}, at any descriptor. */
    private static boolean invokes(Set<MethodNode> methods, String owner, String name) {
        for (MethodNode m : methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call
                        && owner.equals(call.owner) && name.equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MethodNode find(ClassNode cn, String name, String desc) {
        return cn.methods.stream()
                .filter(m -> m.name.equals(name) && m.desc.equals(desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError(cn.name + "#" + name + desc + " does not exist "
                        + "on this band. The mob-origin seam is derived from it, so its absence is a "
                        + "seam change, not a test bug -- re-derive against the merged jar with "
                        + "scripts/javap-mc.sh before editing this file."));
    }

    private static ClassNode read(String internalName) {
        final String resource = internalName + ".class";
        try (InputStream in = MobOriginRestampSeamTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new AssertionError(resource + " is not on the test classpath. Minecraft "
                        + "classes are required to read this seam from bytecode.");
            }
            final ClassNode cn = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_FRAMES);
            return cn;
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + resource, e);
        }
    }

    private static List<Path> mixinSources() {
        try (Stream<Path> files = Files.walk(MIXIN_DIR)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + MIXIN_DIR.toAbsolutePath(), e);
        }
    }

    private static String readSource(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file.toAbsolutePath(), e);
        }
    }

    /**
     * {@code source} with block and line comments blanked out, so a claim about what the code does
     * cannot be satisfied by prose describing it.
     *
     * <p>Deliberately naive — it does not track string literals, so a {@code "//"} inside a string
     * would truncate that line. That is the safe direction here: it can only ever <em>hide</em> code
     * from the scan, and every assertion that matters is a {@code contains} that then fails. The one
     * direction it must not get wrong is leaving comment text visible, and it does not.
     */
    private static String stripComments(String source) {
        final StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            if (source.startsWith("/*", i)) {
                final int end = source.indexOf("*/", i + 2);
                final int stop = end < 0 ? source.length() : end + 2;
                // Preserve newlines so line-oriented reading of a failure still lines up.
                source.substring(i, stop).chars().filter(c -> c == '\n')
                        .forEach(c -> out.append('\n'));
                i = stop;
            } else if (source.startsWith("//", i)) {
                final int end = source.indexOf('\n', i);
                i = end < 0 ? source.length() : end;
            } else {
                out.append(source.charAt(i++));
            }
        }
        return out.toString();
    }

    /**
     * The stripper's own guard. A stripper that returned {@code ""} would make every
     * {@code assertFalse} in this file pass for free, and the {@code assertTrue}s would fail with a
     * misleading message pointing at the mixin instead of at this helper.
     */
    @Test
    void theCommentStripperRemovesProseAndKeepsCode() {
        final String sample = """
                /** javadoc mentioning method = "readNbt" and mcmmo$restampAfterNbtRead */
                class A {
                    // line comment mentioning method = "readNbt"
                    int keep = 1; // trailing comment
                }
                """;
        final String stripped = stripComments(sample);

        assertTrue(stripped.contains("int keep = 1;"),
                () -> "the stripper ate real code; every assertion in this file is now unreliable.\n"
                        + stripped);
        assertTrue(stripped.contains("class A"), () -> "the stripper ate a declaration.\n" + stripped);
        assertFalse(stripped.contains("readNbt"),
                () -> "the stripper left comment text behind, so prose can still satisfy a code "
                        + "assertion -- the exact hole this helper exists to close.\n" + stripped);
        assertFalse(stripped.contains("mcmmo$restampAfterNbtRead"),
                () -> "javadoc @link text survived stripping.\n" + stripped);
    }

    private static List<String> describe(Set<MethodNode> methods) {
        final List<String> names = new ArrayList<>();
        for (MethodNode m : methods) {
            names.add(m.name + m.desc);
        }
        return names;
    }
}
