package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * <b>Every workflow file must be a workflow GitHub will actually start.</b>
 *
 * <p><b>The defect this exists to catch, measured 2026-09-15.</b> {@code ccb97fc4e} (TODO.md
 * &sect;64.3) removed the {@code BAND_COUNT} entry from {@code drift-audit.yml} — correctly, the
 * number moved to {@code scripts/expected-bands.txt} — but left the {@code env:} header that had
 * introduced it. A mapping key with no entries under it parses as <b>null</b>, GitHub rejects the
 * whole file, and every run ends in {@code startup_failure} after {@code 0s}.
 *
 * <p>&#128308; <b>Nothing in this repository could see that.</b> The YAML is well-formed, so no
 * parser complains. {@code branch-file-identity-audit.py} (ship gate 10) compares the file across
 * branches byte-for-byte and is perfectly happy when all nine carry the same broken copy —
 * <em>identical is not correct</em>. The drift audit itself is the thing that stopped running, so
 * it cannot report its own death, and it reports to a tab nobody opens (risk <b>R11</b>). The
 * failure is therefore silent in the one direction that matters: the weekly run is the <em>only
 * unattended leg</em> of risk <b>R8</b> — a fix that lands on {@code master} and never reaches a
 * band — and it would have been dead from the next Monday on with every local gate green.
 *
 * <p>&#9888; <b>A null key is not the same as an absent key, and only one of them is a bug.</b>
 * Deleting {@code env:} entirely is fine; deleting only what lived <em>under</em> it is what breaks
 * the file. That asymmetry is the whole check.
 *
 * <p>&#9888; <b>This guard reads files, so those files must be declared {@code :test} inputs</b> —
 * see the {@code inputs.files(... '.github/workflows' ...)} entry in {@code build.gradle}. With
 * {@code org.gradle.caching=true}, a guard over an undeclared file is served a cached pass after
 * the first green run and silently stops executing. That is not hypothetical here: TODO.md
 * &sect;66.2 measured exactly that for {@code scripts/**}, and &sect;45 measured it for
 * {@code release.yml}, where three real mutations all scored "not caught" because {@code :test}
 * never re-ran.
 */
class WorkflowYamlWellFormedTest {

    /** Relative to the project dir, which Gradle sets as the test working directory. */
    private static final Path WORKFLOWS_DIR = Path.of(".github", "workflows");

    /**
     * The detector, used by both the real scan and the controls below.
     *
     * <p>&#128273; <b>The controls call THIS method, not a re-implementation of it.</b> A control
     * that inlines its own copy of the rule proves the copy works and says nothing about the code
     * that ships — the repository has caught that shape more than once, most recently a self-test
     * case that compared two literals.
     *
     * @return the dotted paths of every key whose value is null, top level and per job
     */
    private static List<String> nullValuedKeys(Map<String, Object> doc) {
        final List<String> found = new ArrayList<>();
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            if (e.getValue() == null) {
                found.add(String.valueOf(e.getKey()));
            }
        }
        final Object jobs = doc.get("jobs");
        if (jobs instanceof Map<?, ?> jobMap) {
            for (Map.Entry<?, ?> job : jobMap.entrySet()) {
                final String jobName = String.valueOf(job.getKey());
                if (job.getValue() == null) {
                    found.add("jobs." + jobName);
                } else if (job.getValue() instanceof Map<?, ?> body) {
                    for (Map.Entry<?, ?> e : body.entrySet()) {
                        if (e.getValue() == null) {
                            found.add("jobs." + jobName + "." + e.getKey());
                        }
                    }
                }
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String yaml) {
        final Object loaded = new Yaml().load(yaml);
        assertTrue(loaded instanceof Map, "fixture did not parse to a mapping");
        return (Map<String, Object>) loaded;
    }

    private static List<Path> workflowFiles() throws IOException {
        assertTrue(Files.isDirectory(WORKFLOWS_DIR),
                WORKFLOWS_DIR + " is not a directory -- run from the project dir");
        try (Stream<Path> s = Files.list(WORKFLOWS_DIR)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        final String n = p.getFileName().toString();
                        return n.endsWith(".yml") || n.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();
        }
    }

    /**
     * &#9888; <b>Fail closed on an empty set.</b> "Found no workflows" and "found no defects" print
     * the same green tick otherwise, which is the exact failure mode ship gates 9, 10 and 11 each
     * carry a {@code --require-bands} floor to prevent.
     */
    @Test
    void thereIsAtLeastOneWorkflowToCheck() throws IOException {
        final List<Path> files = workflowFiles();
        assertFalse(files.isEmpty(),
                "no workflow files found under " + WORKFLOWS_DIR + " -- this guard just became "
                        + "incapable of failing, which is indistinguishable from passing");
    }

    /** Every workflow parses to a mapping and declares the two keys GitHub requires. */
    @Test
    void everyWorkflowParsesAndDeclaresTriggersAndJobs() throws IOException {
        for (Path p : workflowFiles()) {
            final Object loaded;
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                loaded = new Yaml().load(r);
            }
            assertNotNull(loaded, p + " parsed to nothing");
            assertTrue(loaded instanceof Map, p + " did not parse to a mapping");

            @SuppressWarnings("unchecked") final Map<String, Object> doc = (Map<String, Object>) loaded;

            // SnakeYAML resolves a bare `on:` to the BOOLEAN TRUE, not the string "on" -- the YAML
            // 1.1 booleans. Accept either spelling rather than pretending only one occurs.
            assertTrue(doc.containsKey("on") || doc.containsKey(Boolean.TRUE),
                    p + " declares no `on:` trigger block");
            assertTrue(doc.containsKey("jobs"), p + " declares no `jobs:` block");
        }
    }

    /**
     * &#128308; <b>The assertion that carries the weight.</b> No key anywhere may be null.
     *
     * <p>The message names the file and the key, because a failure here is somebody halfway through
     * deleting a block — they need to be told which half is left, not merely that something is off.
     */
    @Test
    void noWorkflowCarriesAKeyWithNothingUnderIt() throws IOException {
        final List<String> defects = new ArrayList<>();
        for (Path p : workflowFiles()) {
            final Object loaded;
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                loaded = new Yaml().load(r);
            }
            if (!(loaded instanceof Map)) {
                continue; // covered by the test above
            }
            @SuppressWarnings("unchecked") final Map<String, Object> doc = (Map<String, Object>) loaded;
            for (String key : nullValuedKeys(doc)) {
                defects.add(p + ": `" + key + ":` has no entries under it");
            }
        }
        assertTrue(defects.isEmpty(),
                "a YAML key with no entries under it parses as null and GitHub REFUSES the whole "
                        + "workflow -- every run ends in startup_failure at 0s. Delete the header "
                        + "too, or give it entries. Found:\n  " + String.join("\n  ", defects));
    }

    // ---------------------------------------------------------------------
    // Controls. Two quiet, three firing -- against the SAME nullValuedKeys().
    // ---------------------------------------------------------------------

    /** A well-formed workflow must produce NO findings, or every "clean" result above is vacuous. */
    @Test
    void controlQuiet_aWellFormedWorkflowIsNotFlagged() {
        final Map<String, Object> doc = parse("""
                name: ok
                on:
                  schedule:
                    - cron: '0 7 * * 1'
                permissions:
                  contents: read
                jobs:
                  audit:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo hi
                """);
        assertEquals(List.of(), nullValuedKeys(doc), "a valid workflow must not be flagged");
    }

    /** Dropping the whole key is LEGAL -- the guard must not punish a complete deletion. */
    @Test
    void controlQuiet_anAbsentKeyIsNotADefect() {
        final Map<String, Object> doc = parse("""
                name: ok
                on:
                  workflow_dispatch:
                jobs:
                  audit:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo hi
                """);
        assertFalse(nullValuedKeys(doc).contains("env"),
                "env: is absent, not empty -- that is the correct way to remove it");
    }

    /** &#128273; The exact defect: a top-level {@code env:} whose entries were removed. */
    @Test
    void controlFiring_aTopLevelKeyWithNothingUnderItIsCaught() {
        final Map<String, Object> doc = parse("""
                name: broken
                on:
                  workflow_dispatch:
                env:
                  # the entry that used to live here was deleted; the header was not
                jobs:
                  audit:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo hi
                """);
        assertTrue(nullValuedKeys(doc).contains("env"),
                "the real drift-audit.yml defect must be detected");
    }

    /** The same mistake one level down, which is just as fatal and easier to miss. */
    @Test
    void controlFiring_aJobLevelKeyWithNothingUnderItIsCaught() {
        final Map<String, Object> doc = parse("""
                name: broken
                on:
                  workflow_dispatch:
                jobs:
                  audit:
                    runs-on: ubuntu-latest
                    env:
                    steps:
                      - run: echo hi
                """);
        assertTrue(nullValuedKeys(doc).contains("jobs.audit.env"),
                "a null key inside a job must be detected, not just at the top level");
    }

    /** An entirely empty job body. */
    @Test
    void controlFiring_anEmptyJobIsCaught() {
        final Map<String, Object> doc = parse("""
                name: broken
                on:
                  workflow_dispatch:
                jobs:
                  audit:
                """);
        assertTrue(nullValuedKeys(doc).contains("jobs.audit"),
                "a job with no body must be detected");
    }

    /**
     * &#9888; <b>Anti-vacuity for the detector itself.</b> {@code nullValuedKeys} returning an empty
     * list for everything would make all three firing controls above the only thing keeping it
     * honest; this pins that it distinguishes the two cases from ONE document rather than two, so a
     * detector that keyed off something incidental to the fixtures cannot pass.
     */
    @Test
    void controlDetectorSeparatesNullFromPresentWithinOneDocument() {
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("name", "x");
        doc.put("env", null);
        doc.put("permissions", Map.of("contents", "read"));
        final List<String> found = nullValuedKeys(doc);
        assertTrue(found.contains("env"), "the null key must be reported");
        assertFalse(found.contains("permissions"), "the populated key must NOT be reported");
        assertFalse(found.contains("name"), "the populated key must NOT be reported");
        assertEquals(1, found.size(), "exactly one finding expected, got " + found);
    }
}
