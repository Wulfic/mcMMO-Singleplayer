package com.gmail.nossr50.fabric.client.modmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * <b>The third direction</b> of the ModMenu catalogue guard, and the one that actually stops the
 * defect this project keeps re-finding.
 *
 * <p>{@code McMMOSettingsTest} proves two things and neither is enough:
 *
 * <ol>
 *   <li>every key the catalogue offers exists in the shipped yml, and</li>
 *   <li>every key of certain shipped families is offered by the catalogue.</li>
 * </ol>
 *
 * <p>Both are statements about <em>files</em>. Neither says the value ever reaches gameplay code, so
 * a switch can pass both and still write to a key nothing reads. The 2026-08-06 wiring audit found
 * four of them at once — {@code General.Show_Profile_Loaded},
 * {@code Skills.Fishing.Override_Vanilla_Treasures}, {@code EarlyGameBoost.Enabled} and
 * {@code General.Level_Up_Chat_Broadcasts.Enabled}, the last with no getter in the codebase at all.
 * A settings toggle over a dead mechanic is worse than an unimplemented mechanic: it converts
 * "missing" into "lying".
 *
 * <p><b>How it decides.</b> A source scan, deliberately, rather than a runtime probe: the question
 * "does anybody call this?" is a fact about the code, and a runtime probe would need a live world.
 * For each catalogue path it finds the config method that reads that literal (or, for a key built by
 * concatenation, the method reading the longest literal prefix of it) and then asks whether that
 * method is reachable from production code outside {@code com.gmail.nossr50.config}.
 *
 * <p><b>Two traps this had to be built around, both real:</b>
 *
 * <ul>
 *   <li><b>Reachability is transitive.</b> The four
 *       {@code Experience_Formula.*.Multiplier} getters have no caller outside the config package —
 *       they are called by {@code ExperienceConfig#getMobOriginXpMultiplier}, which
 *       {@code CombatUtils#processCombatXP} calls. A direct-caller rule reports four false alarms
 *       and gets itself deleted, so the closure follows intra-config calls.</li>
 *   <li><b>…but never out of a validator.</b> Config classes validate their own keys at load time,
 *       from a {@code validate*} method that <em>is</em> called from outside. Propagating through it
 *       would mark every validated key "live" — which is exactly how {@code SerratedStrikes
 *       .BleedTicks} hid: its only caller was the load-time validator, and it was dead. So
 *       {@code validate*} methods are roots that do not propagate.</li>
 * </ul>
 *
 * <p><b>Known limit, measured by mutation rather than assumed.</b> Outside the config package this
 * asks only whether a file <em>mentions</em> the getter, not whether the mentioning code is itself
 * reachable. Deleting the single call to {@code PlayerSessionListener#announceProfileLoaded} left
 * this test green, because the orphaned helper still names the getter. Closing that would need
 * whole-program reachability from the mod's entry points, and it is a rarer shape than the one this
 * guards: a key that <em>nothing at all</em> reads. Do not read a pass here as proof the mechanic
 * works — that is what the behavioural test for each switch is for.
 */
class CatalogueKeysReachCodeTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    private static final Path CONFIG_PACKAGE =
            MAIN_SOURCES.resolve(Path.of("com", "gmail", "nossr50", "config"));

    /** A method declaration: modifier … name(params) {. Indented 1–8 columns, i.e. a member. */
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "(?m)^[ \\t]{1,8}(?:public|protected|private)[\\w<>\\[\\], .?@]*?\\s(\\w+)\\s*\\([^;{]*\\{");

    /** A config read: {@code config.getBoolean("Some.Key"} — the receiver is not constrained. */
    private static final Pattern CONFIG_READ =
            Pattern.compile("\\.get\\w+\\(\\s*\"([^\"]+)\"(\\s*\\+)?", Pattern.DOTALL);

    /** Any method invocation, used both for intra-config edges and for outside callers. */
    private static final Pattern INVOCATION = Pattern.compile("\\b(\\w+)\\s*\\(");

    /**
     * A method <em>reference</em> — {@code GeneralConfig::getBleedEffectEnabled}.
     *
     * <p>⚠️ Without this the scan is blind to an entire calling convention, and blind in the
     * dangerous direction: a getter used only through a method reference has no {@code (} after its
     * name, so {@link #INVOCATION} never sees it and the key it reads is reported dead. Wiring the
     * {@code Particles.*} family produced exactly that — eight live switches failing this test
     * because {@code ParticleEffectUtils} gates them with {@code GeneralConfig::getXxxEnabled}. The
     * tell was that {@code Particles.LevelUp_Tier} and {@code Particles.LargeFireworks} passed from
     * the same file, being the two the same class calls with parentheses.
     */
    private static final Pattern METHOD_REFERENCE = Pattern.compile("::\\s*(\\w+)");

    @Test
    void everyCatalogueKeyReachesProductionCode() throws IOException {
        final ConfigIndex index = ConfigIndex.build();
        final Set<String> reachable = index.reachableFromGameplay();

        assertFalse(index.keyToReaders.isEmpty(),
                "the scan found no config reads at all — CONFIG_READ no longer matches this "
                        + "codebase, and this test is asserting nothing");
        assertFalse(reachable.isEmpty(),
                "the scan found no reachable config methods — INVOCATION or the source layout "
                        + "changed, and this test is asserting nothing");

        final List<String> offenders = new ArrayList<>();
        for (ConfigSetting setting : McMMOSettings.all()) {
            if (isExemptDynamicKey(setting)) {
                continue;
            }
            final Set<String> readers = index.readersOf(setting.path());
            if (readers.isEmpty()) {
                offenders.add(setting.file() + ":" + setting.path()
                        + "\n      no config getter reads this key at all — the switch writes a "
                        + "value nothing in the mod can ever look at");
                continue;
            }
            if (reachable.stream().noneMatch(readers::contains)) {
                offenders.add(setting.file() + ":" + setting.path()
                        + "\n      read only by " + new TreeSet<>(readers)
                        + ", and no production code outside com.gmail.nossr50.config calls any of "
                        + "them (load-time validators do not count)");
            }
        }

        assertTrue(offenders.isEmpty(),
                "ModMenu offers " + offenders.size() + " setting(s) that reach no gameplay code. A "
                        + "toggle over a dead mechanic tells the player something false — either "
                        + "wire the key or drop the switch AND cull the shipped key:\n  - "
                        + String.join("\n  - ", offenders));
    }

    /**
     * The one family this source scan structurally cannot judge: the {@code coreskills.yml} master
     * switches.
     *
     * <p>{@code CoreSkillsConfig} reads them as {@code config.getBoolean(enabledPath(skill), true)},
     * where {@code enabledPath} assembles {@code "Mining" + ".Enabled"} from the enum constant. There
     * is <b>no string literal</b> for {@link #CONFIG_READ} to match and no literal prefix for
     * {@code readersOf}'s concat fallback either, so every one of those 26 rows would be reported as
     * "no config getter reads this key at all" — a confident, wrong finding about the most thoroughly
     * enforced switch in the mod.
     *
     * <p><b>Why the regex is not widened instead.</b> Matching a non-literal argument means matching
     * <em>any</em> {@code getBoolean(...)} call, which would mark every key in the file live and
     * quietly destroy the guard for the other 130 rows. The exemption is narrow, it is asserted to be
     * exactly this file by {@link #theDynamicKeyExemptionCoversOnlyCoreskills()}, and the behaviour it
     * cannot check is checked properly — by execution — in {@code CoreSkillsCatalogueTest}.
     */
    private static boolean isExemptDynamicKey(ConfigSetting setting) {
        return McMMOSettings.CORESKILLS_YML.equals(setting.file());
    }

    /**
     * The exemption's own guard: it must cover exactly the {@code coreskills.yml} rows, and the getter
     * it excuses must still be reachable from gameplay.
     *
     * <p>An exemption nobody re-checks is just a hole with a comment on it. Two things could rot: the
     * skip could grow to cover a file that <em>can</em> be scanned (silently un-guarding it), or
     * {@code SkillGating} could stop calling {@code isPrimarySkillEnabled} — which would make all 26
     * switches dead in exactly the way this class exists to detect, while the exemption hid it.
     */
    @Test
    void theDynamicKeyExemptionCoversOnlyCoreskills() throws IOException {
        final List<String> exempt = new ArrayList<>();
        for (ConfigSetting setting : McMMOSettings.all()) {
            if (isExemptDynamicKey(setting)) {
                exempt.add(setting.file() + ":" + setting.path());
            }
        }
        assertFalse(exempt.isEmpty(), "nothing is exempt any more — either the Skills tab was removed "
                + "or the exemption stopped matching; if the tab is gone, delete this test with it");
        for (String key : exempt) {
            assertTrue(key.startsWith(McMMOSettings.CORESKILLS_YML + ":"),
                    "the dynamic-key exemption has grown beyond coreskills.yml and now excuses "
                            + key + " — that key is scannable and must be proven live like the rest");
        }

        // ...and the getter the exemption excuses is genuinely reached from gameplay. This is the
        // part the skip gives up, bought back at the only granularity a source scan can offer.
        final ConfigIndex index = ConfigIndex.build();
        assertTrue(index.calledFromOutsideConfig.contains("isPrimarySkillEnabled"),
                "nothing outside com.gmail.nossr50.config calls CoreSkillsConfig#isPrimarySkillEnabled "
                        + "— the per-skill master switches read nothing, so all 26 rows on the Skills "
                        + "tab are dead controls. SkillGating is what is supposed to call it.");
    }

    /**
     * The reverse sanity check on the scanner itself: a hand-picked key that is unambiguously live
     * must come back live. Without it, a regex that silently stops matching turns the test above
     * green forever — the same one-directional-completeness trap the guard exists to close.
     */
    @Test
    void aKnownLiveKeyIsSeenAsLive() throws IOException {
        final ConfigIndex index = ConfigIndex.build();
        final Set<String> reachable = index.reachableFromGameplay();

        // Read by ExperienceConfig#getMobOriginXpMultiplier, called from CombatUtils#processCombatXP:
        // live only through the transitive step, so this pins that half of the algorithm too.
        final Set<String> readers = index.readersOf("Experience_Formula.Mobspawners.Multiplier");
        assertFalse(readers.isEmpty(), "no getter found for the spawner XP multiplier");
        assertTrue(reachable.stream().anyMatch(readers::contains),
                "the scanner reports the spawner XP multiplier as dead, but CombatUtils reads it "
                        + "through getMobOriginXpMultiplier — the transitive step is broken");
    }

    /**
     * The second scanner self-check, and the reason {@link #METHOD_REFERENCE} exists.
     *
     * <p>{@code Particles.Bleed} is read by {@code GeneralConfig#getBleedEffectEnabled}, whose only
     * caller — {@code ParticleEffectUtils#playBleedEffect} — names it as a method reference rather
     * than calling it. Drop {@code METHOD_REFERENCE} from the scan and this key looks dead, which is
     * how eight live {@code Particles.*} switches first failed the test above. Without this check the
     * blind spot returns silently the next time someone tidies the regexes.
     */
    @Test
    void aKeyReadOnlyThroughAMethodReferenceIsSeenAsLive() throws IOException {
        final ConfigIndex index = ConfigIndex.build();
        final Set<String> reachable = index.reachableFromGameplay();

        final Set<String> readers = index.readersOf("Particles.Bleed");
        assertFalse(readers.isEmpty(), "no getter found for the Rupture bleed particle switch");
        assertTrue(reachable.stream().anyMatch(readers::contains),
                "the scanner reports the bleed particle switch as dead, but ParticleEffectUtils "
                        + "gates on it via GeneralConfig::getBleedEffectEnabled — the scan no "
                        + "longer understands method references");
    }

    /**
     * The stripper's own test, and it is not optional.
     *
     * <p>A {@link ConfigIndex#stripComments} that returned {@code ""} would make the scan find no
     * calls and no reads at all — at which point every catalogue key reports "no getter reads this",
     * which looks like a catastrophic finding rather than a broken tool. The two emptiness assertions
     * at the top of {@link #everyCatalogueKeyReachesProductionCode} catch that particular shape, so
     * what this pins is the subtler half: comments gone, code and <b>string literals</b> intact, and
     * offsets unmoved.
     */
    @Test
    void theCommentStripperRemovesDocsWithoutTouchingCodeOrStringLiterals() {
        final String source = """
                /** Doc that calls {@link #getGhost()} and mentions "Not.A.Real.Key". */
                public boolean getReal() {
                    // getCommentedOut() must not count as a call
                    return config.getBoolean("General.Real_Key", true); /* getBlockDoc() */
                }
                """;
        final String stripped = ConfigIndex.stripComments(source);

        assertEquals(source.length(), stripped.length(),
                "the stripper moved offsets — enclosing() would mis-attribute every match");

        // The three names that appear ONLY in comments must be gone. These are the defect: each one
        // would otherwise be indexed as a call and mark a dead getter reachable.
        assertFalse(stripped.contains("getGhost"), "a javadoc {@link} still reads as a call");
        assertFalse(stripped.contains("getCommentedOut"), "a // comment still reads as a call");
        assertFalse(stripped.contains("getBlockDoc"), "a /* */ comment still reads as a call");
        assertFalse(stripped.contains("Not.A.Real.Key"),
                "a key named only in a comment still reads as a config read");

        // ...and everything real survives, or the scan would report the whole codebase dead.
        assertTrue(stripped.contains("public boolean getReal()"), "the declaration was destroyed");
        assertTrue(stripped.contains("config.getBoolean(\"General.Real_Key\", true)"),
                "the string literal was destroyed — CONFIG_READ reads its keys out of these");
    }

    /** A parsed view of the config package: who reads which key, and who calls whom. */
    private record ConfigIndex(Map<String, Set<String>> keyToReaders,
            Map<String, Set<String>> concatPrefixToReaders,
            Map<String, Set<String>> configCallGraph,
            Set<String> calledFromOutsideConfig) {

        static ConfigIndex build() throws IOException {
            final Map<String, Set<String>> keyToReaders = new HashMap<>();
            final Map<String, Set<String>> concatPrefixToReaders = new HashMap<>();
            final Map<String, Set<String>> callGraph = new HashMap<>();

            for (Path source : javaFilesUnder(CONFIG_PACKAGE)) {
                final String text = stripComments(Files.readString(source));
                final List<int[]> starts = new ArrayList<>();
                final List<String> names = new ArrayList<>();
                final Matcher declarations = METHOD_DECLARATION.matcher(text);
                while (declarations.find()) {
                    starts.add(new int[] {declarations.start()});
                    names.add(declarations.group(1));
                }

                final Matcher reads = CONFIG_READ.matcher(text);
                while (reads.find()) {
                    final String owner = enclosing(starts, names, reads.start());
                    if (owner == null) {
                        continue;
                    }
                    keyToReaders.computeIfAbsent(reads.group(1), k -> new HashSet<>()).add(owner);
                    if (reads.group(2) != null) {
                        concatPrefixToReaders.computeIfAbsent(reads.group(1), k -> new HashSet<>())
                                .add(owner);
                    }
                }

                for (Pattern shape : List.of(INVOCATION, METHOD_REFERENCE)) {
                    final Matcher calls = shape.matcher(text);
                    while (calls.find()) {
                        final String owner = enclosing(starts, names, calls.start());
                        if (owner != null) {
                            callGraph.computeIfAbsent(owner, k -> new HashSet<>())
                                    .add(calls.group(1));
                        }
                    }
                }
            }

            final Set<String> outside = new HashSet<>();
            for (Path source : javaFilesUnder(MAIN_SOURCES)) {
                if (source.startsWith(CONFIG_PACKAGE)
                        || source.getFileName().toString().equals("McMMOSettings.java")) {
                    continue; // the catalogue naming a getter would be circular.
                }
                final String text = stripComments(Files.readString(source));
                for (Pattern shape : List.of(INVOCATION, METHOD_REFERENCE)) {
                    final Matcher calls = shape.matcher(text);
                    while (calls.find()) {
                        outside.add(calls.group(1));
                    }
                }
            }

            return new ConfigIndex(keyToReaders, concatPrefixToReaders, callGraph, outside);
        }

        /**
         * Config methods reachable from production code outside the config package. Seeded with
         * every config method production code names, then closed over intra-config calls — except
         * out of {@code validate*}, which is a load-time consumer, not a gameplay one.
         */
        Set<String> reachableFromGameplay() {
            final Set<String> live = new HashSet<>();
            final Deque<String> queue = new ArrayDeque<>();
            for (String method : configCallGraph.keySet()) {
                if (calledFromOutsideConfig.contains(method) && live.add(method)) {
                    queue.add(method);
                }
            }
            for (Set<String> readers : keyToReaders.values()) {
                for (String reader : readers) {
                    if (calledFromOutsideConfig.contains(reader) && live.add(reader)) {
                        queue.add(reader);
                    }
                }
            }

            while (!queue.isEmpty()) {
                final String caller = queue.poll();
                if (caller.startsWith("validate")) {
                    continue;
                }
                for (String callee : configCallGraph.getOrDefault(caller, Set.of())) {
                    if (configCallGraph.containsKey(callee) && live.add(callee)) {
                        queue.add(callee);
                    }
                }
            }
            return live;
        }

        /**
         * The config methods that read {@code path}: an exact literal match, or — for a key the code
         * assembles by concatenation ({@code "Skills." + skill + ".Level_Cap"}) — every method
         * reading a literal that is a prefix of it.
         */
        Set<String> readersOf(String path) {
            final Set<String> exact = keyToReaders.get(path);
            if (exact != null) {
                return exact;
            }
            final Set<String> byPrefix = new HashSet<>();
            concatPrefixToReaders.forEach((prefix, readers) -> {
                if (path.startsWith(prefix)) {
                    byPrefix.addAll(readers);
                }
            });
            return byPrefix;
        }

        /**
         * Blanks out Java comments, preserving every offset and every string literal.
         *
         * <p>⚠️⚠️ <b>Without this the scan cannot tell a call from a doc cross-reference</b>, and it
         * fails in the direction that matters: {@link #INVOCATION} matches {@code getFoo(} whether it
         * is real code or {@code {@link #getFoo()}} inside a javadoc block — and because javadoc sits
         * <em>above</em> the member it documents, {@link #enclosing} attributes it to the
         * <em>preceding</em> method. So one live getter documenting its dead neighbour marks that
         * neighbour reachable, and this whole test goes quietly green over a dead switch. Found by
         * accident, and measured: deleting a single {@code {@link #getPetEngageRange()}} from the
         * javadoc of the getter above it took the offender count from 2 to 3.
         *
         * <p>Blanking rather than deleting, so every index into the returned text still lines up with
         * the original — {@link #enclosing} maps match offsets to method declarations and would
         * silently mis-attribute every match if the text shifted. Newlines are kept for the same
         * reason.
         *
         * <p>String literals are preserved deliberately and are the reason this is a state machine
         * rather than two regexes: {@link #CONFIG_READ} reads its keys out of exactly those literals,
         * so a stripper that blanked {@code "Skills.Taming.…"} would report every key unread — which
         * renders as "no config getter reads this key at all" and would look like a finding rather
         * than a broken scanner.
         */
        static String stripComments(String java) {
            final char[] out = java.toCharArray();
            final int n = out.length;
            int i = 0;
            while (i < n) {
                final char c = out[i];
                if (c == '"' || c == '\'') {
                    // Skip the literal whole, honouring escapes, so a // or /* inside one survives.
                    final char quote = c;
                    i++;
                    while (i < n && out[i] != quote) {
                        i += (out[i] == '\\') ? 2 : 1;
                    }
                    i++;
                } else if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                    while (i < n && out[i] != '\n') {
                        out[i++] = ' ';
                    }
                } else if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                    while (i < n && !(out[i] == '*' && i + 1 < n && out[i + 1] == '/')) {
                        if (out[i] != '\n') {
                            out[i] = ' ';
                        }
                        i++;
                    }
                    if (i < n) {
                        out[i++] = ' '; // the '*'
                    }
                    if (i < n) {
                        out[i++] = ' '; // the '/'
                    }
                } else {
                    i++;
                }
            }
            return new String(out);
        }

        private static String enclosing(List<int[]> starts, List<String> names, int position) {
            String owner = null;
            for (int i = 0; i < starts.size(); i++) {
                if (starts.get(i)[0] < position) {
                    owner = names.get(i);
                } else {
                    break;
                }
            }
            return owner;
        }

        private static List<Path> javaFilesUnder(Path root) throws IOException {
            try (Stream<Path> walk = Files.walk(root)) {
                return walk.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
            }
        }
    }
}
