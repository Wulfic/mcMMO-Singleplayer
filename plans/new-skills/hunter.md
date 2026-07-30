# Hunter (Mob Mastery) — Plan

**Read [00-OVERVIEW.md](00-OVERVIEW.md) first.** Hunter is a **standalone primary skill** (D1 shape) and
belongs in **`COMBAT_SKILLS`**. It needs **no F1** (tick sampler) and **no F2** (attribute service) —
it is entirely event-driven. It does need **two things nothing else in the port has**: a
**per-mob-type persistent counter** and a **mob loot-drop seam**.

> 🔶 **STATUS: IN PROGRESS as of 2026-07-30. Stages 1 (the anti-farm gate) and 2 (the enum, the
> manager and the kill-count persistence) are DONE.** Stage 0 — the §G
> play-test of Pass 1 + Pass 2 — was **consciously skipped on the user's instruction**, not satisfied.
> Pass 2 now has five skills in the tree (Agility/Parkour/Swimming/Flying, Stealth, Unarmored,
> Husbandry) that are code-complete and **never played**; Hunter is the sixth, stacked on an unverified
> base. `PLAYTEST_G.md` sessions 8–10 remain the open job. See `[[resume-here]]`.
>
> **Stage gate in use (the Husbandry precedent):** code + config + locale + unit tests +
> `./gradlew build` exit 0 + a clean headless boot, per stage. Live play is deferred to one §G session
> at the end. Each stage pauses for review and is committed to memory before the next starts.

---

## Concept

Hunter is the **mob-knowledge** skill: the more of a given creature you have personally killed, the
better you get at killing *that* creature specifically. It progresses on **two independent axes**, and
keeping them independent is the whole design:

| Axis | Currency | Reward | Shape |
|---|---|---|---|
| **Mob mastery** (horizontal) | kills of **one** mob type | flat bonus damage **vs that mob only** | 3 hard thresholds, per mob, never resets |
| **Hunter level** (vertical) | XP from **any** kill | increased loot drops, unlocked per **mob tier** | the standard `10N² + 1010N` curve |

Killing 10,000 zombies makes you a zombie specialist. Killing 200 of everything makes you a
generalist with better loot. Neither substitutes for the other. That separation is the reason this
skill is worth building and it must survive the balance pass intact.

**It is not a weapon skill.** Swords/Axes/Unarmed/Archery already own "how hard do I hit with X."
Hunter owns "how well do I know Y." Its bonus is deliberately weapon-agnostic — that is what the
request's *"for all combat skills"* means, and it is why the bonus does **not** live in
`MeleeDamageBonus` with the per-weapon arms.

---

## Mechanic 1 — Mob mastery (the kill counters)

Per player, per mob type, a monotonically increasing kill count. Thresholds and rewards:

> ✅ **RULED 2026-07-30 (user): HALVED. The table below is superseded.** The shipped numbers are
> **+1.0 / +2.0 / +3.0** damage — half a heart per tier, not a full one. The reasoning the user was
> shown and chose on: at the drafted +6.0 a **bare fist hits for 7.0**, a diamond sword's worth of
> punch from kills alone and on top of whatever Unarmed already adds, and it compounds with Stealth
> Assassin (`[[stealth-skill-build]]` row ST14 already flags Assassin as dangerous on its own). At
> +3.0 the multipliers are netherite **1.375×**, wooden **1.75×**, bare fist **4×**.
>
> ⚠️ **The coherence check in §Balance is computed against the OLD numbers** and is unaffected — it
> keys off kill *counts*, not the bonus size. But the "is a capped Hunter survivable as a design"
> play-test row now measures 4×, not 7×.

| Kills of mob X | Bonus damage vs mob X | Superseded draft |
|---|---|---|
| 500 | **+1.0** (half a heart) | ~~+2.0~~ |
| 2,500 | **+2.0** (one heart) | ~~+4.0~~ |
| 10,000 | **+3.0** (1.5 hearts, cap) | ~~+6.0~~ |

⚠️ **Read those numbers as damage, not as flavour.** A diamond sword is **7.0** base. At the cap,
Hunter adds **+6.0** — it very nearly doubles a diamond sword and it is a **flat** add, so it is
proportionally *worst* for the strongest weapon and *absurd* for the weakest:

| Weapon | Base | At +6.0 | Multiplier |
|---|---|---|---|
| Netherite sword | 8.0 | 14.0 | 1.75× |
| Diamond sword | 7.0 | 13.0 | 1.86× |
| Wooden sword | 4.0 | 10.0 | 2.50× |
| **Bare fist** | **1.0** | **7.0** | **7.00×** |

A capped Hunter punches for a diamond sword. That may be exactly what you want (it is a *mastery*
skill), but it is a decision, not an accident — **D-HU4** below.

### ⚠️ D-HU1 — What counts as a kill? THIS IS THE SKILL'S ONLY REAL RISK.

10,000 kills sounds like a mountain until you remember mob farms. A standard gold farm produces
**3,000+ zombified piglins/hour**. At that rate the 10,000-kill cap — a permanent +6 damage — falls in
**under four hours of AFK**. Hand-killing at a sustained ~6 kills/min it is **~28 hours**. The gate
between those two numbers *is* the feature.

The port already has three of the gates, in `CombatUtils#processCombatXP`
([CombatUtils.java:263](src/main/java/com/gmail/nossr50/util/skills/CombatUtils.java#L263)) —
**reuse them verbatim, do not re-derive**:

1. `McMMOMod.getTransientEntityTracker().isTransient(uuid)` → Call-of-the-Wild summons pay nothing.
2. `target instanceof IronGolemEntity golem && golem.isPlayerCreated()` → built golems pay nothing.
3. The killing blow must be **player-attributed** (`source.getAttacker()` is the player). Most farms
   kill by fall/lava/suffocation and are therefore *already* excluded — but a grinder you stand in and
   swing at is not.

The fourth gate — **spawn origin** (spawner / egg / bred / nether portal) — **does not exist in this
port.** Legacy carried it as `MobMetaFlagType`
([legacy/…/metadata/MobMetaFlagType.java](legacy/src/main/java/com/gmail/nossr50/metadata/MobMetaFlagType.java));
it is **unported**, and `CombatUtils#processCombatXP`'s own javadoc already admits the resulting
mob-origin multipliers "do nothing yet." That is a tolerable gap for XP. It is **not** tolerable for a
permanent damage buff.

> ✅ **RULED 2026-07-28 (user): port the spawn-origin gating.** A persistent `MOB_SPAWNER_MOB` /
> `PLAYER_BRED_MOB` flag written on spawn (spawner mixin + the Husbandry breed hook when that lands)
> and read at death. Spawner and bred mobs count **zero** toward mastery. This is real work — a new
> mixin plus per-entity persistent data — and it is **Stage 1, a prerequisite**, not a follow-up.
> Locked.
>
> ✅ **WIDENED 2026-07-30 (user), then corrected against bytecode. BUILT — see §"Stage 1 as built".**
> The excluded set is spawner + trial spawner, bred, player-placed, and portal/structure. Two
> deviations from the ruling as worded, both stated rather than absorbed:
>
> - **`DIMENSION_TRAVEL` is NOT flagged; `STRUCTURE` is.** The ruling was offered to the user as
>   "`DIMENSION_TRAVEL` / portal spawns" and that label was wrong. `NetherPortalBlock#randomTick`
>   spawns its zombified piglins with **`SpawnReason.STRUCTURE`** (bytecode: `getstatic
>   SpawnReason.STRUCTURE` then `EntityType.spawn`); nothing in 1.21.11 is named after a portal.
>   `DIMENSION_TRAVEL` is the unrelated case of an *existing* mob being re-created on the far side of a
>   portal, so flagging it would disqualify any mob a player lured through one and close no hole — the
>   marker already survives the crossing, because Fabric transfers attachments on cross-world
>   teleportation. `STRUCTURE` is the faithful implementation of the ruling's intent (legacy's
>   `NETHER_PORTAL_MOB`). Its only false negatives are structure-placed one-offs — a monument's elder
>   guardians, a mansion's evokers, an end city's shulkers — every one of them non-renewable and
>   therefore far below the 500-kill threshold anyway.
> - **`COMMAND` and `DISPENSER` were added** to the spawn-egg bucket. `/summon` and a dispenser firing
>   an egg are the same cheese the egg ruling closed. `BUCKET` was deliberately left counting —
>   releasing an axolotl you caught is not free mob generation.

Rejected alternatives, recorded so they are not re-litigated: shipping the three existing gates alone
(a spawner farm caps the skill in an afternoon), and a rolling per-mob-per-hour cap (blunt, and it
throttles legitimate heavy play). The rolling cap remains available as a **backstop** if play-testing
finds a farm the origin flags miss — it is additive, not an alternative.

Note that mastery kills and Hunter XP may use **different** gates: it is entirely reasonable for a
spawner zombie to pay XP but not mastery, and that is the recommended split (XP keeps the looser
`processCombatXP` gates it already has).

### ⚠️ D-HU2 — Persistence: this is a genuinely new shape in this codebase

Everything in `PlayerProfile` is an `EnumMap` over a closed enum, and `FlatFileProfileStore` writes a
**fixed** key set derived from `.values()`
([FlatFileProfileStore.java:215-224](src/main/java/com/gmail/nossr50/database/FlatFileProfileStore.java#L215-L224)).
Kill counters are an **open-ended, string-keyed** map (`minecraft:zombie`, plus anything a mod adds).
Nothing in the port persists that today.

**Recommended: a `kills:` section in the existing per-player YAML.** Not a side file — a second file
means two save paths, two failure modes and a desync between them.

```yaml
kills:
  minecraft:zombie: 1204
  minecraft:creeper: 88
```

`YamlConfiguration` already has everything required: `getConfigurationSection(path)` and
`getKeys(false)` ([YamlConfiguration.java:233,241](src/main/java/com/gmail/nossr50/config/YamlConfiguration.java#L233)).

**Four guards, all mandatory:**

- **Never resolve the key to an `EntityType` at load time.** Store the raw string; resolve at use.
  Registry lookups at load are the `isIn(TagKey)`-throws trap from `[[phase-d-hylian-luck]]`, and a
  mob from an uninstalled mod must not nuke the profile.
- **Cap the section size on read** (e.g. 4,096 keys, log and truncate past it). This is
  `[[placed-block-persistence]]` defect #16's lesson generalised: **never let a number or a
  collection size read off disk drive an allocation.**
- **Only persist non-zero entries.** Keeps the file proportional to what the player actually did.
- **`markProfileDirty()` on every increment.** Every kill dirties the profile; the save is already
  debounced, so this is fine — but confirm it in the boot test rather than assuming.

**Add an old-profile regression fixture**: a `<uuid>.yml` written before Hunter existed must load with
an empty kill map and a defaulted Hunter level, and must not lose its other skills.

### ⚠️ D-HU3 — Where the damage bonus is applied (seam is already identified)

`EntityDamageListener#onModifyAppliedDamage` is the single K1 seam
([EntityDamageListener.java:282-349](src/main/java/com/gmail/nossr50/fabric/listeners/EntityDamageListener.java#L282-L349)).
Hunter's bonus is **structurally identical to Agility Smash and Stealth Assassin**: it is keyed on
*state* (here, the target's identity), not on the held weapon, so it is a **fourth sibling** in that
chain — not a new arm in `MeleeDamageBonus`, which is per-weapon by construction.

```java
result = applySprintSmash(entity, source, result);   // existing, line 312
result = applyAssassin(entity, source, result);      // existing, line 317
result = applyHunterMastery(entity, source, result); // NEW — runs last
```

**Runs last, deliberately.** Assassin multiplies the *whole* melee total
(`[[stealth-skill-build]]`, playtest row ST14 flags this as already dangerous). If Hunter ran first, a
capped backstab would multiply the +6 as well. Landing it last makes "+1 heart" mean literally +2.0
damage, never +2.0 × crit × Assassin. **Pin the ordering with a test** — it is invisible in review and
a future refactor will move it.

### D-HU4 — Two sub-rulings on the bonus itself

> ✅ **RULED 2026-07-28 (user): melee + player projectiles, attack-strength-scaled on melee only.**
> Locked.

- **Ranged: yes.** *"for all combat skills"* reads as including Archery, Crossbows and thrown
  Tridents. The bonus applies to **any damage attributable to the player**, melee or projectile.
  Explicitly **not** the wolf-bite arm (`applyWolfAttackBonus`) — that is the wolf's damage and
  Taming's Sharpened Claws/Gore already own it; adding Hunter there double-dips on the same hit.
- **Scaled by attack strength: yes on melee, no on ranged.** *Every* melee bonus in this port
  multiplies by `mmoPlayer.getAttackStrength()`
  ([MeleeDamageBonus.java:66](src/main/java/com/gmail/nossr50/skills/MeleeDamageBonus.java#L66)).
  An unscaled flat +6 means **spam-clicking beats a charged swing** — both an exploit and off-pattern
  for the whole codebase. The melee/ranged asymmetry is already precedent: Trident Impale does exactly
  this, and `MeleeDamageBonus.java:102-108` documents why (a throw has no swing to charge).

⚠️ **Watch in §G: a capped +6.0 on a fully-drawn bow.** The ranged ruling is the one most likely to
need a follow-up tuning pass — a separate `Ranged_Multiplier` knob (default 1.0) is cheap insurance
and costs nothing if it stays at 1.0.

---

## Mechanic 2 — Hunter level & the drop bonus

XP is paid per qualifying kill, scaled by the mob's tier. Level unlocks **increased drops**, per tier.

### ⚠️ D-HU5 — Tiers: what they are and where they live

"Certain animals/monsters at different levels depending on tier" needs an explicit table — do **not**
hand-wave "hostile = tier 2". Proposed shape (numbers are starting points):

> ✅ **RULED 2026-07-30 (user): a DERIVED default plus a small override table.** Not a full ~90-row
> table. `tierOf(entity)` derives a tier structurally from the live entity — passive vs hostile, max
> health, attack damage — and a short `Hunter.Tier_Overrides` section in `advanced.yml` names only the
> exceptions (a ghast has 10 HP and enormous damage; a witch is 26 HP and barely fights back). **The
> reason: an unlisted mob resolves to a sane tier instead of silently resolving to `0`**, which is
> exactly what bit Husbandry twice (`Nautilus` and `Happy_Ghast` absent from the breed table ⇒ two
> verbs paid nothing for both species) and Fishing once (upstream defect #10). A derived default cannot
> go stale when Mojang adds a mob or a player installs one.
>
> ⚠️ **`experience.yml` → `Experience_Values.Combat.Multiplier` is NOT usable as the tier source**,
> despite being a ready-made ~90-row per-mob table. It prices XP-per-damage, not danger: Witch is
> `0.1`, **Ender Dragon is `1.0`**, Warden is `6.0`. Deriving tiers from it would put the dragon in T1.
> It is still the right *shape* precedent for the override section's keying.
>
> ✅ **RULED 2026-07-30 (user): T4 ships with members, priced conservatively at ~1,500 XP, not 5,000.**
> That keeps the inherited 80 h guardrail intact even against a wither farm while still making bosses
> the top of the ladder. Supersedes the "T4 is the designated cut" line under §Cuts — **there is now no
> empty tier and no dead config section.**

| Tier | Membership | Drop bonus unlocks at | Example |
|---|---|---|---|
| **T1** | passive / trivial | Hunter 100 | chicken, cow, sheep, rabbit |
| **T2** | common hostiles | Hunter 300 | zombie, skeleton, creeper, spider |
| **T3** | dangerous / nether / rare | Hunter 600 | blaze, wither skeleton, guardian, ravager |
| **T4** | bosses — **ships**, at ~1,500 XP | Hunter 900 | wither, ender dragon, warden |

**There is already precedent for a per-mob-name table in this repo**: `experience.yml`
→ `Experience_Values.Combat.Multiplier` (line ~739) is exactly this, keyed by mob name. Mirror its
shape and key by **entity registry path** (`zombie`, `wither_skeleton`) rather than a Bukkit enum name
— that is the lesson from `[[fishing-shake]]` (upstream defect #10: the shipped YAML addressed enum
names Bukkit had renamed, so three mobs silently shook out nothing). Walk **the config's own section
names**, never `EntityType.values()`.

Put it in `advanced.yml` under `Hunter:` if it stays small; promote it to `hunter_tiers.yml` through
the existing `TreasureConfig` machinery only if it grows past a screenful.

### D-HU6 — What "increased drops" actually means (seam verified)

> ✅ **RULED 2026-07-28 (user): a chance-gated second roll of the mob's own loot table.** Not a
> bespoke per-mob item table (a ~120-mob data-authoring job that ignores Looting), and not
> rare-slot weighting (needs loot-table introspection). Locked.

A re-roll respects Looting, needs zero new data, and is one mixin. It gives "more of what that mob
drops" — more rotten flesh, but also more gunpowder and ender pearls.

⚠️ **The known cost of this ruling:** the reward is *proportional*, so common-drop mobs mostly pay out
in junk. If §G finds that unsatisfying, rare-slot weighting is the upgrade path — it reuses this exact
seam and only changes what the bonus roll is biased toward, so choosing the simple version now costs
nothing later.

**The seam, verified by `javap` against the 1.21.11 merged jar** (`[[javap-mc-script]]` — do not
trust this from memory, re-verify at implementation time):

```
protected void dropLoot(ServerWorld, DamageSource, boolean)          // resolves getLootTableKey()
public    void dropLoot(ServerWorld, DamageSource, boolean, RegistryKey<LootTable>)
public    void generateLoot(ServerWorld, DamageSource, boolean, RegistryKey<LootTable>, Consumer<ItemStack>)
```

The 3-arg `protected` overload reads `getLootTableKey()`, returns early on `Optional.isEmpty()`, and
delegates to the 4-arg `public` one. So: **`@Inject(at = TAIL)` on the 3-arg overload**, re-invoke the
**4-arg** one for the bonus roll.

⚠️ **Two traps, both of which produce an item-duplication bomb rather than a clean failure:**

1. **The no-recursion property is load-bearing and accidental.** It holds only because the inject is
   on the 3-arg method and the re-invoke targets the 4-arg one. Move the inject to the 4-arg overload
   and it recurses infinitely. **Write a test that asserts a single bonus roll produces exactly one
   extra table roll**, and put the reason in a comment at the injection site.
2. **`dropLoot` fires for every mob death, including ones with no killer.** The bonus must be gated on
   the same player attribution as the counter, or a mob dying in lava on the far side of the world
   drops double.

> ✅ **ANSWERED 2026-07-30 (stage 3, bytecode): `AFTER_DEATH` fires AFTER `drop()`.** Fabric's
> `LivingEntityMixin#notifyDeath` injects at the `World#sendEntityStatus` call inside
> `LivingEntity#onDeath`, and in the 1.21.11 merged jar `drop(ServerWorld, DamageSource)` is at offset
> **150** of that method while `sendEntityStatus` is at **158**. So **the kill that crosses a threshold
> does not get that threshold's reward on the same corpse — the next one does.** Costs nothing today
> (mastery pays damage, not loot) and it is the reason stage 6's Trophy Hunter re-roll must ride
> `dropLoot` rather than react to anything observed at death. Two further facts from the same read: the
> event fires inside `onDeath`'s `instanceof ServerWorld` branch, so **there is no client-side fire to
> guard against**; and `onDeath` early-returns on its own `dead` flag, so it **cannot fire twice** for
> one mob.

---

## Sub-skills

| Sub-skill | Type | Mechanic | Ranks | Risk |
|---|---|---|---|---|
| **Mob Mastery** | passive | The 500/2500/10000 per-mob damage thresholds | 3 (fixed thresholds, not level-gated) | **High** — D-HU1, D-HU2 |
| **Trophy Hunter** | passive | Bonus loot-table re-roll, unlocked per tier | 4 (one per tier) | Med — D-HU6 |
| **Field Dressing** | passive | Higher chance for the *rare* slot of the re-roll at high level | some | Low |
| **Quarry Sense** *(optional)* | passive | Beast Lore on a mob also shows your kill count / mastery tier | 1 | Low — see D-HU7 |

⚠️ **Mob Mastery does not fit the `RankUtils` model.** Every other sub-skill in the mod unlocks on
*skill level* via `skillranks.yml`. Mastery unlocks on a *per-mob counter*. Do not force it through
`RankUtils` — give it its own resolver on the manager and let `skillranks.yml` carry only the other
three. Forcing it will produce a sub-skill whose rank display lies.

### ⚠️ D-HU7 — The player cannot see any of this

Three thresholds across ~120 mob types is a lot of invisible state. If the player cannot see progress,
the whole horizontal axis feels like nothing is happening for the first 499 kills.

Minimum viable surfacing, in priority order:

1. **A notification on crossing a threshold.** The milestone-plaque machinery already exists
   (`[[milestone-advancement-plaques]]`) — but note it grants *hidden vanilla advancements* keyed per
   skill, so a per-mob plaque is a different shape. A `NotificationManager` chat/actionbar line is the
   cheap correct answer.
2. **`/mcstats hunter`** showing the top N most-killed mobs and their tier — not all 120.
3. **Quarry Sense**: reuse Taming's Beast Lore renderer
   ([EntityDamageListener.java:246](src/main/java/com/gmail/nossr50/fabric/listeners/EntityDamageListener.java#L246))
   to add a "Killed: 1,204 — Mastery II" line. This is the *good* answer: it is diegetic, it costs
   almost nothing, and it reuses an existing screen.

---

## MC-free core (`HunterManager extends SkillManager`)

Zero Minecraft imports. `AcrobaticsManager` is the reference shape.

```java
int    masteryTier(int killsOfThisMob)            // 0 / 1 / 2 / 3, from the threshold table
double masteryDamageBonus(int killsOfThisMob)     // tier * 2.0, capped at 6.0
boolean qualifiesForMastery(KillContext ctx)      // the D-HU1 gate, MC-free over a small DTO
float  xpForKill(String mobId, int tier)          // tier-scaled XP
boolean canTrophyHunt(int tier)                   // Hunter level >= that tier's unlock
boolean rollBonusDrop(int tier)                   // injected RNG, pinned in tests
boolean canQuarrySense()                          // standard RankUtils + isSubSkillEnabled gate
```

The manager decides *whether* and *how much*. The listener and mixin do the entity/loot work. Kill
counts live on `PlayerProfile`; the manager reads them through `McMMOPlayer`, never off an entity.

---

## Registration — deltas the generic checklist misses

Do all 22 items in [00-OVERVIEW.md](00-OVERVIEW.md) §"add a PrimarySkillType". **These four are the
ones that will bite:**

1. ~~**`DatatypeEnumTest.java:24` asserts `assertEquals(23, PrimarySkillType.values().length)`** → bump
   to 24.~~ **STALE — corrected 2026-07-30.** That test does **not** assert a bare literal: it
   *re-derives* the count (`19 + 3 + 1 + 1 + 1`) with a comment naming each addition, so Hunter is
   `+ 1` on the expression rather than a bumped number. It still fails loudly, which is the point.
2. **`scripts/gen-milestone-advancements.sh` does `rm -rf "$ROOT"` at line 101** and regenerates from
   its own `ICON` map. **Add Hunter to that map *before* anyone runs it**, or the next run silently
   deletes `hunter.json` and the plaques stop firing. This is the exact trap that would have deleted
   `agility.json` (`[[agility-child-skill-restructure]]`). `MilestoneAdvancementResourcesTest` iterates
   every skill and will catch the missing file — it will **not** catch the generator deleting it later.
3. **`SkillTools.buildCombatSkills()`** (line 261) is a hardcoded `ImmutableList` — add Hunter there.
   That is what wires `Enabled_For_PVE`/`Enabled_For_PVP` and the `/mcstats` grouping.
4. **`PrimarySkillType.name()` is the save key.** `HUNTER` is a new constant, so there is no rename
   risk — but the *kill map* is new persistence with no precedent, which is D-HU2's whole point.

---

## Balance — the numbers, derived not guessed

Curve: shipped LINEAR `base 1020` / `multiplier 20` ⇒ total XP to RetroMode level *N* =
**`10N² + 1010N`** ⇒ **max (level 1000) = 11,010,000 XP**.

Target: **~100 h to max** at a sustained manual rate of ~6 kills/min (360/h) ⇒ ~36,000 kills ⇒
**≈306 XP per kill on average**. Tier-scaled starting points:

| Tier | XP/kill | Kills to max Hunter on that tier alone |
|---|---|---|
| T1 | 100 | 110,100 |
| T2 | 300 | 36,700 |
| T3 | 800 | 13,762 |
| T4 (boss) | 5,000 | 2,202 |

**Coherence check worth keeping:** 10,000 T2 kills (one mob's full mastery) pays 3,000,000 XP ≈
**Hunter level 505**. Maxing one creature's mastery lands you at roughly half the level cap. The two
axes stay in step without either being a shortcut to the other. If a rebalance breaks that, say so.

**Guardrail, inherited from Agility (D-AG6):** no single source may max the skill in under 80 h of
doing only that thing. T4 at 5,000 XP/kill breaks this badly if bosses are farmable — which is
**exactly** why D-HU1 matters and why T4 is the designated cut.

`Diminished_Returns` ships `Enabled: false` at 20,000 XP / 10 min. A boss farm would blow through
that; if it is ever enabled, Hunter needs its own threshold.

---

## Testing

**Unit (MC-free) — non-negotiable:**
- `masteryTier` at the exact boundaries: 499/500, 2499/2500, 9999/10000, and **10,000,000** (cap holds).
- `masteryDamageBonus` = 0/2/4/6 and never 8.
- `qualifiesForMastery` truth table: transient summon, player-created golem, no attacker, spawner mob
  (per D-HU1's ruling).
- `xpForKill` per tier; `rollBonusDrop` with pinned RNG (0 → always, 100 → never).
- **Persistence round-trip**: write 3 mob counts → reload → identical. Then the adversarial ones —
  a key for a mob that no longer exists, a section with 10,000 keys (must truncate, not OOM), a
  negative count on disk.
- **Old-profile regression**: a pre-Hunter `<uuid>.yml` loads clean.

**Ordering tests (the ones that catch a refactor):**
- Hunter's bonus is applied **after** Assassin — assert a sneaking capped Hunter's total is
  `(base × assassin) + 6`, **not** `(base + 6) × assassin`. Mutation-check by swapping the two lines;
  if the test still passes it is worthless.
- Melee bonus scales with attack strength; ranged does not.

**Mixin test:** assert a **structural marker** on the loot mixin (an `@Unique` field or the handler in
`getDeclaredMethods()`), **never** `assertDoesNotThrow(Class.forName(...))` — that proves nothing for a
class the registry bootstrap already loaded (`[[mixin-slice-allow-guard]]`). Assert the single-extra-roll
property explicitly.

**§G rows** (new `PLAYTEST_G2.md` session, or a Hunter block in session 8):
- Kill 500 of one mob → threshold notification fires → measure damage vs that mob *and* vs a different
  mob (the second one must be unchanged — that is the test that proves it is per-mob).
- Kill in a spawner farm → mastery does **not** move (per D-HU1).
- Punch a mastered mob bare-fisted → confirm the 7× multiplier is survivable as a design.
- Hit the tier unlock → visibly more loot; a mob dying with no killer drops normally.
- Quit → reopen → kill counts survived.

---

## ✅ Stage 1 as built (2026-07-30)

**The seam the plan implied was unsafe, and the trap it walked into was the most-farmed mob in the
game.** The plan pointed at `MobEntity#initialize(ServerWorldAccess, LocalDifficulty, SpawnReason,
EntityData)` — reasonable, since both spawner logics visibly call it with their reason. Of the **57**
classes overriding it in 1.21.11, exactly **one** does not call `super`: **`CaveSpiderEntity`**, whose
entire override is

```
  0: aload 4
  2: areturn
```

a bare pass-through that skips `SpiderEntity`'s jockey and effect logic. An injection there would have
missed **every cave spider**, and a mineshaft cave-spider spawner is one of the two or three most-built
grinders there is — the miss would have landed exactly on the case the gate exists for, while passing
any test written with a zombie. **The funnel rule bites for the sixth time in this port.**

**The seam actually used: `EntityType#create(World, SpawnReason)`.** Every reason-carrying path bottoms
out there, verified against the merged jar:

| Path | Chain |
|---|---|
| `MobSpawnerLogic`, `TrialSpawnerLogic` | `loadEntityWithPassengers` → `loadEntityFromData` → `getEntityFromData` → `create(World, SpawnReason)` |
| `SpawnEggItem` | `spawnFromItemStack` → `spawn` → `create(6-arg)` → `create(World, SpawnReason)` |
| `NetherPortalBlock` | `spawn(ServerWorld, BlockPos, SpawnReason)` → the same |
| ~40 `createChild` implementations | `create(World, SpawnReason)` **directly**, with `BREEDING` |

It is an instance method on a class with **no vanilla subclasses**, so nothing can override it away,
and **its body ignores the `SpawnReason` it is handed** (a feature-flag check and a factory call) —
vanilla passes the reason down only so callers further up can branch on it, which makes reading it here
free of behavioural risk. **One injection covers ~40 breeding species with zero per-species work** —
the exact opposite of Husbandry, where every verb needed a species roster.

**A second hole the plan did not have: conversion.** A drowned farm is a zombie spawner over water. The
zombies are stamped, then each drowns into a **different entity** that vanilla builds fresh through
`create(world, SpawnReason.CONVERSION)` — which qualifies, so the drowned would arrive unmarked and the
farm would launder its own origin one conversion later. Closed by a second injector on
`MobEntity#convertTo` (the 4-arg overload; the 3-arg one is a one-line delegate that pushes
`CONVERSION`). Fabric's `copyOnDeath` does **not** cover this: conversion is not death, and vanilla
builds a new entity rather than transferring the old one.

**⚠️ The load-bearing invariant: a qualifying origin is NEVER written.** `create(World, SpawnReason)` is
also the path taken by `SpawnReason.LOAD` — every mob in every chunk that loads — and by
`DIMENSION_TRAVEL`. Both arrive carrying mobs that already own a marker from a previous session or from
the far side of a portal, so writing "NATURAL" there would erase it. The symptom would be a spawner farm
quietly starting to count again **after a world reload**, indistinguishable from the gate never having
worked. Pinned by `MobOriginsTest.aQualifyingOriginIsNeverWritten` and mutation-proven.

**Fail closed, twice over.** The marker is stored as a raw `String` and resolved at read time, not
through an enum codec: Fabric **drops** an attachment whose codec fails to decode, with only a
`"Skipping invalid attachments"` warning, and for this attachment a dropped marker reads as *"this mob
counts"* — a single future rename would silently re-open every farm. A `String` always decodes, and an
unrecognised value becomes `MobOrigin.UNKNOWN`, which does **not** count.

**⚠️ No `default` arm on `MobOrigins#classify`.** A switch expression over an enum must be exhaustive,
so a Minecraft version that adds a `SpawnReason` **fails the compile** rather than falling through to
"counts". `MobOriginsTest.everySpawnReasonMapsToAnOrigin` is the runtime companion for the case that
guard cannot see — running against a newer Minecraft than the mod was built against.

**Deliberately left counting, and flagged as §G watch items:** raids (`EVENT`), patrols (`PATROL`), an
evoker's vexes (`MOB_SUMMONED`), a zombie's reinforcements (`REINFORCEMENT`). Each is farmable, but a
defended village raid is also about the most legitimate combat in the game and excluding it would take
more from honest play than it saves. **The known leak, stated rather than hidden:** `JOCKEY`. The
skeleton riding a spawner-spawned spider arrives with `JOCKEY`, not `SPAWNER`, so it escapes the gate its
mount does not. The rolling per-mob-per-hour cap D-HU1 holds in reserve is the additive backstop for all
of these, not a re-mapping of the switch.

**Also unchanged from the plan and worth noting: no natural-spawn origin closes a dark-room or
nether-wastes farm.** Those mobs are `NATURAL` and legitimately so. What excludes them today is the
pre-existing player-attribution gate (most such farms kill by fall, lava or suffocation) — a grinder you
stand in and swing at is not excluded by anything, and that is the case §G has to measure.

**Files:** `datatypes/mobs/MobOrigin.java` (MC-free vocabulary + predicate),
`util/MobOrigins.java` (the `SpawnReason` mapping + attachment access),
`fabric/mixin/EntityTypeSpawnOriginMixin.java`, `fabric/mixin/MobConversionOriginMixin.java`,
`McMMOAttachments.MOB_ORIGIN`. **1169 tests green (+18), `./gradlew build` exit 0, 4 mutations run and
each reddened exactly its own test.** A one-shot INFO line (`"Hunter: mob-origin gate is live"`) fires
the first time a mob is marked in a session — the `[[smelting-furnace-arm]]` trick, because this gate is
invisible by construction until a mastery counter exists to refuse, so "it refused the mob" and "the
injector never bound" would otherwise look identical in-game.

---

## ✅ Stage 2 as built (2026-07-30)

**The enum, the manager, the kill-count persistence and all 22 boilerplate items. No mechanics — the
skill is in the tree, on `/mcstats`, on the XP bar and in the advancement tab, and nothing fires it.**
1189 tests green (+20), `./gradlew build` exit 0, boot **`Done (1.264s)`, 0 ERROR, 0 WARN, 0
exceptions, 0 mixin failures**, and **1910 advancements = 1584 vanilla + our 326** (+7 on stage 1's
1903: one hub, five level tiers, one maxed — exactly a skill with no sub-skills). **6 mutations run.**

### 🔑 Zero sub-skill enums, deliberately

`SubSkillType` gained **nothing**. Hunter's four sub-skills land with their behaviour — Mob Mastery in
stages 3–4, Trophy Hunter and Field Dressing in 6, Quarry Sense in 7 — because a rank-less,
config-less, behaviour-less constant reads as a half-wired sub-skill to **everything that iterates
that enum**, `/mcstats` and the milestone generator included. This is the same call the Stealth plan
made for Thief. Two useful consequences: `skillranks.yml` and `advanced.yml` needed no Hunter section
at all this stage, and Hunter correctly falls through to `GenericSkillStatsRenderer` (which is right,
not a gap — the precedent is Swimming and Flying, pinned in `SkillStatsRendererTest`).

### ⚠️⚠️ THE TRAP IN D-HU2 THE PLAN DID NOT HAVE: A MOB ID MAY CONTAIN A DOT

The plan specified the section shape (`kills:` in the per-player YAML) and four guards, and prescribed
`getConfigurationSection(path)` + `getKeys(false)` to read it. **That is a bug for modded mobs, and it
fails silently.** A registry `Identifier` namespace legally contains `.` (`[a-z0-9_.-]`), and
`YamlConfiguration`'s addresses are **dot-delimited** — so writing this section key-by-key as
`set("kills." + mobId, count)` buries `some.pack:dread.beast` in a phantom nested subsection, and
reading it back through the same dotted path yields **`0`**. Vanilla ids have no dots, so every test
written with `minecraft:zombie` passes.

**Fixed by never touching a dotted path for this section:** the whole map goes through one
`set("kills", map)`, and the read pulls the raw `Map` out with `get("kills")` and iterates its entries.
**Mutation-proven** — restoring the key-by-key write reddens
`FlatFileProfileStoreTest.aMobIdContainingADotSurvivesTheRoundTrip` and only that test. Same family as
the `[[dynamic-locale-key-families]]` problem: a key built by concatenation is a key nothing greps.

### The four D-HU2 guards, as built

| Guard | Where | Pinned by |
|---|---|---|
| Key stays a raw `String`, resolved at use | the map's type | (type-level) |
| Entry count capped at 4,096 **on read** | `FlatFileProfileStore#readMobKills` | `anOversizedKillSectionIsTruncatedOnReadRatherThanLoadedWhole` |
| Only positive counts persisted / accepted | read + write | `unusableKillEntriesAreDroppedRatherThanTrusted`, `aProfileWithNoKillsWritesNoKillsSectionAtAll` |
| `markProfileDirty()` on every increment | `PlayerProfile#incrementMobKills` | `aCountedKillAloneIsEnoughToMakeTheProfileSave` |

Two additions the plan did not ask for, both because the cap has two sides: the **in-memory** map is
also capped on insert (a refused new type logs one WARN per profile and returns `0`, and **must not
freeze the counters that already exist** — otherwise a modded world stops the skill dead rather than
merely stops widening it), and a `kills:` value that is **not a section at all** degrades to "no kills"
instead of throwing on a cast and costing the player every skill they had.

⚠️ **A `TreeMap`, not a `HashMap`.** A save file that reorders itself on every write is unreviewable,
and this is the one section a player might plausibly read by hand.

### ⚠️ A TEST BUG WORTH REMEMBERING: `"skills"` CONTAINS `"kills"`

`assertFalse(Files.readString(file).contains("kills"))` — meant to prove a kill-less profile writes no
`kills:` section — **passes unconditionally**, because every profile writes `skills:`. It was the one
red test in the first full build. Assert through the parser (`YamlConfiguration#contains`), never with
a substring search on the raw document.

### Balance numbers restated

The mastery ladder ships as the **ruled** `500 / 2,500 / 10,000` kills → **+1.0 / +2.0 / +3.0** damage,
as parallel tables on `HunterManager` with the index of one belonging to the index of the other (pinned,
because a threshold added without its bonus would fail at runtime, in the damage path, on somebody's
10,000th kill). **Nothing calls `masteryDamageBonus` yet** — that is stage 4, and it must run **last**
in the `onModifyAppliedDamage` chain, after Sprint Smash and after Stealth Assassin.

⚠️ **`Diminished_Returns.Threshold.Hunter` is the one entry in that table that is not decorative.**
Every other skill sits far out of its reach; a T4 boss at ~1,500 XP means **fourteen kills in ten
minutes** trips 20,000 — unreachable by hand, trivial for a farm. Commented in `experience.yml` so
nobody "tidies" it to match its neighbours.

### Files

`skills/hunter/HunterManager.java` (new), `PrimarySkillType.HUNTER`,
`PlayerProfile` (the `mobKills` `TreeMap`, `MAX_TRACKED_MOB_TYPES`, three accessors, a 9-arg loaded
constructor with the 8-arg one delegating), `FlatFileProfileStore` (`readMobKills` + the single-map
write), `McMMOPlayer` (`initManager` case + `getHunterManager`), `SkillTools.buildCombatSkills`,
`experience.yml` ×3, `config.yml` ×3, five locale keys, `McMMOSettings` ×2,
`gen-milestone-advancements.sh` (`ICON[hunter]=skeleton_skull`, `ROLE[hunter]=Slayer`) + the
regenerated datapack, and `HunterManagerTest` / `FlatFileProfileStoreTest` / `DatatypeEnumTest` /
`SkillToolsTest`.

---

## ✅ Stage 3 as built (2026-07-30)

**Hunter now counts.** One new listener, one locale string, no new mixin, no new config, no new
advancement. Suite **1210 green** (+14), `./gradlew build` exit 0, headless boot `Done (1.195s)` with
**0 mcMMO ERROR/WARN, 0 exceptions, 0 mixin failures** and **1910 advancements** unchanged (correct —
this stage ships no datapack file). **4 mutations run, each reddening exactly the tests it should.**

### The seam and the ordering answer

`ServerLivingEntityEvents.AFTER_DEATH`, registered by a new `HunterListener` alongside
`ProjectileListener`'s existing handler — Fabric events fan out, so the two are independent and neither
had to be widened to host the other. The plan's open ordering question is answered in the D-HU6 box
above: **it fires after `drop()`**, it fires only server-side, and it cannot fire twice.

### The gate chain as built, in this order

| # | Gate | Why it is where it is |
|---|---|---|
| 1 | `source.getAttacker() instanceof ServerPlayerEntity` | Cheapest read *and* the most selective — fall/lava/suffocation farms have no attacker at all. Also excludes a wolf's kill, which is Taming's. |
| 2 | `CombatUtils.canCombatSkillsTrigger(HUNTER, victim)` | The `Enabled_For_PVE`/`Enabled_For_PVP` switches. **Free reuse the plan did not list**: it already existed for six other combat call sites. |
| 3 | transient summon, then player-created iron golem | Verbatim from `CombatUtils#processCombatXP`, as ruled. |
| 4 | `MobOrigins.countsTowardMastery(victim)` | Stage 1's marker. |

🔑 **Gate 4 became observable for the first time in this stage.** Stage 1's marker had nothing to
refuse until a counter existed, which is why `PLAYTEST_G` session 11 tests stages 1 and 3 **together**
rather than back-filling a session-10-style block per stage.

### ⚠️ The counter is keyed on the FULL registry id, and that differs from Husbandry on purpose

`Registries.ENTITY_TYPE.getId(type).toString()` → `minecraft:zombie`, **not**
`ConfigStringUtils.getConfigEntityTypeString(getId(type).getPath())` → `Cow`, which is what every
Husbandry table uses. The kills map is an open key space that has to survive two mods shipping a mob of
the same name, and it is the same key `FlatFileProfileStore` persists. A silent switch to `getPath()`
passes every "the counter moved" assertion, so it is pinned by name in
`aPlayerKillIsCountedAgainstTheVictimsNamespacedRegistryId` and mutation-proven.

### ⚠️ The notification promises nothing, deliberately

`Hunter.SubSkill.MobMastery.Proc` is a **statement of fact** — "You have mastered the Zombie — Mastery
1, 500 slain" — with no mention of the damage it is worth. Stage 3 moves counters; the bonus is not
wired until stage 4, and a string advertising a bonus the build does not apply is the "config that
lies" failure this port keeps having to undo. Worded this way it stays true *after* stage 4 lands, so
there is no follow-up locale edit to forget.

Routed as `NotificationType.SUBSKILL_UNLOCKED` rather than `SUBSKILL_MESSAGE`: that type ships action
bar **plus a chat copy**, and 500 kills in the making must not be a flash on the action bar mid-fight
that the player cannot scroll back to. Sound is `SKILL_UNLOCKED`, matching
`sendPlayerUnlockNotification`.

⚠️ **Only `.Proc` shipped — no `.Name`/`.Description`.** Mob Mastery still has no `SubSkillType`
constant (see stage 2), so nothing renders those yet and shipping them would add two more dead strings
to the seven this port has already had to find. They land with the enum, if it ever lands, where
`SkillLocaleCompletenessTest` enforces them.

### ⚠️ A TEST TRAP THIS STAGE WALKED INTO: ASSERTING A BARE DIGIT

The first draft of `everyThresholdAnnouncesItsOwnTier` asserted `message.contains(String.valueOf(tier))`.
**It is vacuous at tier 2**: the message also carries the kill count, and 2,500 contains a `2`. Same
family as the Stealth "assert OFF the reference point" lesson. Fixed by asserting the rendered wording
`"Mastery " + tier` **and** the count separately — which also makes it the only guard on the
**argument order** of the three substitutions, and mutation-proven by swapping tier and kills.

### The invisible-counter problem, solved the way stage 1 solved the invisible gate

One `AtomicBoolean`-guarded INFO line on the first *counted* kill of a session:
`"Hunter: mob-mastery counters are live — first counted kill this session was 'minecraft:zombie' (now 1)."`
Without it, "every mob I killed was gated" and "the listener never bound" are indistinguishable for the
first 499 kills. ⚠️ It does **not** appear in a headless boot of a quiet world — nothing dies there —
which is expected, not a failure. The flag has a package-private reset because it is process-wide
static that JUnit would otherwise carry between test classes.

### Files

`fabric/listeners/HunterListener.java` (new), `McMMOMod#onInitialize` (+1 registration),
`locale_en_US.properties` (+1 key), `HunterListenerTest` (new, 14 tests).

### ⬜ Next: stage 4

`applyHunterMastery` as the **fourth** sibling in `EntityDamageListener#onModifyAppliedDamage`, running
**after** Sprint Smash and Assassin. Everything it needs already exists and is unit-tested:
`masteryDamageBonusAgainst(mobId)` resolves the whole thing from the victim's id. The ordering test is
the one that matters and it must be mutation-checked by swapping the two lines.

---

## Staged build order

One stage lands **fully** — code + config + locale + unit tests + green boot + played §G rows — before
the next starts. No half-wired skill sitting in the tree.

| Stage | Content | Gate to proceed |
|---|---|---|
| **0** | 🔴 **§G play-test of Pass 1 + Pass 2** | `PLAYTEST_G.md` session 8 actually played |
| **1** | **The anti-farm gate (D-HU1, ruled).** Spawn-origin flag mixin + per-entity persistence | spawner mobs provably do not count, in a live world |
| **2** | ✅ **DONE** — Enum + manager + profile persistence + all boilerplate. **No mechanics.** | old-profile regression green; the 25 → 26 skill assert updated |
| **3** | ✅ **DONE** — Kill counters wired on `AFTER_DEATH` + threshold notification | counters move in-game and survive a restart (§G session 11) |
| **4** | The damage bonus on the K1 seam (D-HU3/D-HU4) | ordering tests green; damage measured in-game |
| **5** | Hunter XP + the tier table | XP rate measured against the 100 h target |
| **6** | Trophy Hunter loot mixin (D-HU6) | single-extra-roll test green; no dupe in a live world |
| **7** | Quarry Sense / `/mcstats hunter` (D-HU7) | — |

Stages 1 and 2 are pure risk with zero visible feature. Ship them alone anyway. That is the Agility
Stage 0 lesson: when a profile comes back zeroed you need to know *which* change did it.

---

## Cuts / deferrals

- ~~**T4 bosses (D-HU5).**~~ **REVERSED 2026-07-30 (user): T4 ships with members**, priced at ~1,500
  XP/kill rather than the drafted 5,000, which is what keeps the 80 h guardrail intact against a wither
  farm. See the ruling box under D-HU5.
- **Quarry Sense** is the first thing to cut if Beast Lore's renderer fights back — but it is also the
  cheapest fix for D-HU7, so cut it last among the nice-to-haves.
- **Per-mob milestone plaques.** The existing plaque machinery is keyed per *skill*; a per-mob variant
  is a different shape and not worth it. Chat/actionbar notification only.
- **Do not** build a kill-count HUD or scoreboard. `[[stealth-skill-build]]` already ruled out the
  wiki's detection-level HUD for the same reason: it is a permanent screen element for a number that
  changes 6 times a minute.
