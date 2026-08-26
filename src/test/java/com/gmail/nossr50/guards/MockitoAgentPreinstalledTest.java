package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.management.ManagementFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * <b>The Mockito agent guard</b> (multi-version TODO &sect;45, risk <b>R14</b>): Mockito's inline
 * mock maker must get its {@code Instrumentation} from a {@code -javaagent} installed at VM start,
 * never by self-attaching once the fork is already running.
 *
 * <p><b>Why this guard exists.</b> Measured on {@code mc/1.21.10} 2026-08-25: <b>449 of 1846</b>
 * tests failed with {@code Could not self-attach to current VM using external process}, and a clean
 * re-run of the <em>same commit with the identical configuration</em> was <b>1846 / 0</b>. On
 * Windows, self-attach spawns an external helper process, once per fork, and
 * {@code maxParallelForks = 4} races that spawn.
 *
 * <p>&#128308; The danger is not the lost time. {@code release.yml} runs this suite on every push,
 * where a red run is already the ordinary outcome, and a 449-failure red is indistinguishable from a
 * real regression. <b>The failure mode is the inverse of the obvious one: a genuine regression gets
 * re-run away as "probably the flake."</b> Nobody re-runs a red they believe.
 *
 * <p><b>The mechanism, read out of {@code mockito-core-5.23.0.jar} with {@code javap -c} rather than
 * recalled.</b> {@code org.mockito.internal.PremainAttachAccess.getInstrumentation()} resolves in
 * this order:
 *
 * <ol>
 *   <li>{@code org.mockito.internal.PremainAttach.getInstrumentation()}, loaded <b>from the system
 *       classloader</b> — the {@code -javaagent} path. Non-null, return.</li>
 *   <li>{@code net.bytebuddy.agent.Installer.getInstrumentation()} — the same, for a byte-buddy
 *       agent.</li>
 *   <li>If the VM is &gt;= Java 21 <em>and</em> the VM input arguments do not contain the literal
 *       string {@code -XX:+EnableDynamicAgentLoading}, print the <em>"Mockito is currently
 *       self-attaching"</em> warning to {@code System.err}.</li>
 *   <li>{@code ByteBuddyAgent.install()} — <b>unconditionally, whatever step 3 decided.</b></li>
 * </ol>
 *
 * <p>&#9888;&#9888; <b>So {@code -XX:+EnableDynamicAgentLoading} is not the fix</b>, despite being
 * the remedy R14 was first recorded with. It is tested against a warning string at step 3 and is
 * never consulted at step 4: it suppresses the message and leaves the racing call exactly where it
 * was. Because that message is the only visible tell that a fork took the attach path, adding it
 * would have made R14 <em>harder to see while still flaking</em>. Satisfying <b>step 1</b> is what
 * removes the race.
 *
 * <p><b>&#128273; This guard reads the JVM it is running in — not a Gradle property, and not the
 * text of {@code build.gradle}.</b> {@link CompilerErrorCapTest} has to be handed
 * {@code mcmmo.build.compilerArgs} because the compiler is not the JVM that test runs in. Here the
 * thing under test <em>is</em> this JVM, so the stronger reading is available and the weaker one
 * would not be excusable.
 *
 * <p>&#9888; <b>This class must not live in {@code com.gmail.nossr50.fabric.mixin}</b> — same reason
 * as {@link BandToolchainLevelTest}: the Mixin transformer claims every class in the package
 * {@code mcmmo.mixins.json} declares, including a test.
 */
class MockitoAgentPreinstalledTest {

    /**
     * Step 1's holder: Mockito's own {@code Premain-Class}, per {@code mockito-core}'s
     * {@code META-INF/MANIFEST.MF}.
     */
    private static final String PREMAIN_ATTACH = "org.mockito.internal.PremainAttach";

    /**
     * Step 2's holder: byte-buddy's {@code Premain-Class}/{@code Agent-Class}, per
     * {@code byte-buddy-agent}'s {@code META-INF/MANIFEST.MF}.
     *
     * <p>Checked alongside step 1 <b>on purpose</b>. Either one returning non-null makes step 4
     * unreachable, which is the entire property this guard exists to hold. A guard that insisted on
     * {@code mockito-core} specifically would go red on a byte-buddy agent that fixes R14 just as
     * completely — and a guard that fails a correct configuration is one somebody eventually
     * weakens.
     */
    private static final String BYTE_BUDDY_INSTALLER = "net.bytebuddy.agent.Installer";

    /** The artifact {@code build.gradle} wires today. Named in messages, not asserted on. */
    private static final String WIRED_AGENT_ARTIFACT = "mockito-core";

    /**
     * The flag that is <b>deliberately not</b> the fix. Asserted absent so that nobody re-introduces
     * R14's originally-recorded remedy on top of the real one and mistakes silence for a cure.
     */
    private static final String WARNING_SUPPRESSOR = "-XX:+EnableDynamicAgentLoading";

    /**
     * The launcher actually received the agent argument.
     *
     * <p>Reads the real command line of this fork via {@code RuntimeMXBean}, which is the same
     * source Mockito itself consults at step 3.
     */
    @Test
    void theTestJvmWasLaunchedWithAnAgent() {
        final List<String> args = jvmInputArguments();

        final String agentArg = args.stream()
                .filter(a -> a.startsWith("-javaagent:"))
                .findFirst()
                .orElse(null);

        assertNotNull(agentArg, () -> "This test JVM was launched with " + args + ", which carry no "
                + "-javaagent at all. Mockito's inline mock maker will therefore fall through to "
                + "ByteBuddyAgent.install() and SELF-ATTACH through an external helper process, once "
                + "per fork -- which four parallel forks race. That race put 449 of 1846 tests red "
                + "on mc/1.21.10 on 2026-08-25, and green on a re-run of the same commit. Restore "
                + "the mockitoAgent configuration and the -javaagent jvmArgs line in build.gradle's "
                + "test block; it wires " + WIRED_AGENT_ARTIFACT + ". See TODO.md 45.1.");
    }

    /**
     * The load-bearing assertion: the premain <em>ran</em>, and left an {@code Instrumentation}
     * where Mockito looks for it.
     *
     * <p>Kept separate from the argument check above because it fails for a different reason and has
     * to say so. An agent argument can be present and still not satisfy step 1 — the wrong artifact
     * on the command line, or a premain that threw. This evaluates the <b>exact expression Mockito
     * evaluates</b>, against the <b>exact classloader Mockito uses</b>: anything else would be a
     * re-implementation that could agree with itself while the real lookup failed.
     */
    @Test
    void mockitosOwnPremainLookupFindsAnInstrumentation() {
        final Object fromMockito = premainInstrumentation(PREMAIN_ATTACH);
        final Object fromByteBuddy = premainInstrumentation(BYTE_BUDDY_INSTALLER);

        assertTrue(fromMockito != null || fromByteBuddy != null, () -> "Neither " + PREMAIN_ATTACH
                + " (step 1) nor " + BYTE_BUDDY_INSTALLER + " (step 2) holds an Instrumentation "
                + "on the SYSTEM classloader, which is the only classloader Mockito looks them up "
                + "on. A -javaagent jar is appended to the system class path by the VM, so both "
                + "being empty means no agent premain ran -- whatever is on the test classpath. "
                + "Mockito therefore reaches step 4, ByteBuddyAgent.install(), and self-attaches "
                + "through an external helper process once per fork; four parallel forks race that "
                + "spawn. build.gradle wires " + WIRED_AGENT_ARTIFACT + " for this. See TODO.md "
                + "45.1.");
    }

    /**
     * Reads one premain holder the way Mockito reads it: <b>by name, off the system classloader</b>.
     *
     * <p>Not a re-implementation — a {@code -javaagent} jar is appended to the system class path, so
     * the class the agent populated is a <em>different class object</em> from the same-named one on
     * the test classpath. Resolving it any other way would look right and read the wrong static
     * field.
     *
     * <p>Absent is not an error here: exactly one of the two holders is expected to be missing on a
     * correctly configured build. The caller asserts on the pair.
     *
     * <p>&#9888; <b>Catching {@code Exception} and returning null is not laziness — it is what
     * Mockito does</b>, and treating a throwing holder as anything else would make this guard
     * disagree with the code it is modelling. {@code PremainAttachAccess.doGetInstrumentation}
     * wraps its whole body in {@code catch (Exception) -> return null}; verified by reading its
     * exception table, after byte-buddy's {@code Installer.getInstrumentation()} was measured
     * <em>throwing</em> rather than returning null when no byte-buddy agent is loaded. An earlier
     * draft of this method called {@link org.junit.jupiter.api.Assertions#fail} on that throw and
     * went red on a <b>correctly configured</b> build.
     */
    private static Object premainInstrumentation(String holder) {
        try {
            final Class<?> loaded = ClassLoader.getSystemClassLoader().loadClass(holder);
            return loaded.getMethod("getInstrumentation").invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The vacuity guard: the two assertions above only matter while the <b>inline</b> mock maker is
     * the active one.
     *
     * <p>The subclass mock maker needs no {@code Instrumentation} at all, so under it there is no
     * attach, no race, and nothing for this class to protect — while every assertion above would go
     * on passing, asserting a fact nobody depends on. <b>That is the vacuity shape this repository
     * has caught fifteen times, and it is cheaper to close here than to discover later.</b>
     *
     * <p>Proved by doing something only the inline maker can do rather than by reading a
     * configuration file that claims it: static mocking. The subclass maker refuses it by name, so
     * the failure message identifies the maker that took over.
     */
    @Test
    void theInlineMockMakerIsStillTheOneThisGuardIsProtecting() {
        try (MockedStatic<InlineOnly> mocked = Mockito.mockStatic(InlineOnly.class)) {
            mocked.when(InlineOnly::marker).thenReturn("mocked");
            assertTrue("mocked".equals(InlineOnly.marker()),
                    "Static mocking was accepted but did not take effect, which means the inline "
                            + "mock maker is installed and not working. See TODO.md 45.3.");
        } catch (RuntimeException e) {
            fail("Mockito refused to create a static mock (" + e + "). Static mocking is supported "
                    + "ONLY by the inline mock maker, so this says the active mock maker has been "
                    + "changed. If that was deliberate, the two assertions above have stopped "
                    + "meaning anything -- the subclass maker needs no Instrumentation, never "
                    + "attaches, and cannot lose the race risk R14 describes -- and this whole class "
                    + "should be deleted rather than left passing. See TODO.md 45.3.");
        }
    }

    /**
     * R14's originally-recorded remedy must not come back alongside the real one.
     *
     * <p>{@code -XX:+EnableDynamicAgentLoading} silences the {@code "Mockito is currently
     * self-attaching"} warning without changing whether the self-attach happens. With the agent
     * correctly installed it is dead weight; without it, it is actively harmful, because that
     * warning is the only signal that a fork took the racing path. Either way it does not belong on
     * this command line, and a future reader deserves to be told why rather than left to re-derive
     * it.
     */
    @Test
    void theWarningSuppressorIsNotUsedAsASubstituteForTheFix() {
        final List<String> args = jvmInputArguments();
        assertTrue(!args.contains(WARNING_SUPPRESSOR), () -> "This test JVM was launched with "
                + WARNING_SUPPRESSOR + ". That flag is read by Mockito at step 3 of "
                + "PremainAttachAccess.getInstrumentation() and compared against a warning string; "
                + "it is NOT consulted at step 4, which calls ByteBuddyAgent.install() regardless. "
                + "It therefore hides the only visible evidence that a fork self-attached while "
                + "leaving the race in place. If the agent is installed the flag buys nothing; if it "
                + "is not, the flag makes R14 harder to see. Remove it. See TODO.md 45.0.");
    }

    private static List<String> jvmInputArguments() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

    /**
     * A static method for {@link #theInlineMockMakerIsStillTheOneThisGuardIsProtecting} to mock.
     *
     * <p>Deliberately local to this test and free of any Minecraft type: this guard has to be able
     * to run in any fork, including one that never bootstrapped the vanilla registries.
     */
    static final class InlineOnly {
        static String marker() {
            return "real";
        }

        private InlineOnly() {}
    }
}
