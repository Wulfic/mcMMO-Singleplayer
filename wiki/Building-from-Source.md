# Building from Source

## Requirements

**JDK 21.** That's it — Gradle wrapper handles everything else.

## Build

```bash
./gradlew build
```

The remapped mod jar lands in `build/libs/`.

**The JUnit suite runs as part of `build`, so a failing test fails the build.** That is deliberate — there is no green build with red tests.

## Useful targets

```bash
./gradlew test        # unit tests only
./gradlew runServer   # headless dev server
./gradlew runClient   # dev client
```

`runClient` and `runServer` use `run/` as their game directory, so dev configs land in `run/config/mcmmo/` and dev worlds in `run/saves/`.

---

## Project layout

| Path | What's in it |
|---|---|
| `src/main/java/com/gmail/nossr50/` | The mod. Ported mcMMO code keeps its original package structure. |
| `src/main/java/com/gmail/nossr50/fabric/` | Fabric-specific entry point, listeners, platform adapters. |
| `src/main/resources/` | Shipped YAML configs, mixin manifests, locale. |
| `src/test/java/` | The JUnit suite. |
| `legacy/` | **The vendored upstream Bukkit tree**, kept in-repo for reference during porting. Not compiled. |
| `plans/new-skills/` | Design docs for the Pass-2 and future skills. |
| `scripts/` | Dev helpers, including a `javap` wrapper over the Loom-cached yarn jar. |
| `PLAYTEST_G.md` | The in-game verification plan. |

---

## Architecture notes

A few conventions worth knowing before you send a patch.

### MC-free cores

Skill logic is kept **free of `net.minecraft` types** wherever possible, with Minecraft-typed glue in a thin layer around it. This is what lets most of the suite run as plain JUnit without a game bootstrap. If you're adding a mechanic, put the arithmetic in an MC-free class and the block/entity access in the listener.

### The adapter layer

`platform/` wraps Minecraft types (players, items, blocks) behind final wrapper classes. Mockito 5 mocks final classes out of the box, so these are mockable in MC-free tests.

### Mixins

Some behaviour needs a mixin because Fabric has no event for it. Two hard-won rules:

- **An unresolvable `@Slice` is silently dropped, not raised** — and the injector then binds *everywhere* in the method. `defaultRequire=1` will not catch this, because `require` is a *minimum*. **Always add `allow = N` to a slice-anchored injector.**
- `assertDoesNotThrow(Class.forName(...))` proves nothing for a class the test harness already loads. Assert a structural marker instead — an `@Unique` field, or the handler method in `getDeclaredMethods()`.

### Enum names are save keys

`PrimarySkillType.name()` **is** the on-disk save key and the milestone advancement filename. Renaming a constant silently orphans player data — the profile loader falls back to the starting level and the plaque simply stops firing, with no error.

**Renames go through `util/skills/SkillRenames`.** It costs one line there instead of a bug report.

### Sub-skill parents come from the enum name prefix

`SkillTools` resolves a sub-skill's parent by matching the enum name up to the first `_` against the primary skill names. So `PARKOUR_SNOW_WALKER` gates on Parkour, and `UNARMORED_IRON_SKIN` vs `UNARMED_IRON_GRIP` resolve to different skills — the match is `equalsIgnoreCase` on the *whole* prefix, not `startsWith`, so those two can't collide.

**A rename would silently re-gate a sub-skill.** Be careful.

---

## Testing

The suite is over 1,700 cases and covers the MC-free cores directly plus a `fabric-loader-junit` harness for registry-backed tests. Every band runs the same suite on its own build, and the counts stay within one or two of each other — a band that "passes" by having quietly skipped something would show up as a drop.

```bash
./gradlew test
```

For registry-dependent tests, call `McTestRegistries.bootstrap()` in `@BeforeAll`.

**Beyond unit tests, a change isn't done until it boots.** `./gradlew runServer` and confirm `Done (` in the log with zero exceptions and zero mixin failures. A headless boot catches a surprising amount — a derived index logged at INFO proves a whole config scan ran, without needing a client.

---

## Branches, bands and releases

Each supported Minecraft version band is its own branch. `master` **is** the newest supported band; `mc/**` branches exist only for older ones.

| Branch | Band |
|---|---|
| `master` | 1.21.11 |
| `mc/1.21.10` | 1.21.9 – 1.21.10 |
| `mc/1.21.8` | 1.21.6 – 1.21.8 |
| `mc/1.21.5` | 1.21.5 |
| `mc/1.21.4` | 1.21.4 |
| `mc/1.21.3` | 1.21.2 – 1.21.3 |

A branch pins its own `minecraft_version` and `yarn_mappings` in `gradle.properties`, and its own band range in `fabric.mod.json`. Checking one out and running `./gradlew build` produces that band's jar with no further configuration — there is no preprocessor and no version switch to set.

**Fixes land on `master` first, always**, then propagate to the band branches. Each propagation commit carries a `Backport-of: <sha>` trailer naming the `master` commit it came from, which makes `git log --grep='Backport-of: <sha>'` a mechanical answer to *"did this fix reach every band?"*. A `master` commit that deliberately must not propagate says so in the commit instead. This matters more than it looks: almost every bug this project gets is version-agnostic logic, so a fix that lands on one band and is forgotten on another produces no error anywhere — the bug just quietly comes back for that band's players.

Releases are published per band and tagged `mc<minecraft version>-v<mod version>`, so each Minecraft line keeps its own latest build. The tag and the jar name deliberately differ — the jar is what a player reads (`mcmmo-<version>+mc1.21.6-1.21.8.jar`), while the tag's `mc<version>-v` prefix is what the release automation matches on to find and retire that line's previous release.

---

## Contributing

[Issues](https://github.com/Wulfic/mcMMO-Singleplayer/issues) and PRs welcome — **on this repo, not upstream mcMMO**, which maintains the Bukkit/Spigot plugin and cannot act on a single-player Fabric report. Two things that make a PR much easier to take:

1. **Tests.** New mechanics need unit coverage; bug fixes need a test that fails without the fix.
2. **Say what you observed, not what you expected.** "Seems fine" isn't a verification — a `/mcstats` delta, a message on screen, or a block that changed is.

**Balance feedback on the six new skills is especially wanted right now.** Their XP rates and reference speeds are starting estimates, not measured numbers — see [`plans/PLAYTEST_G.md`](https://github.com/Wulfic/mcMMO-Singleplayer/blob/master/plans/PLAYTEST_G.md).
