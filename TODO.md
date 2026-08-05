# TODO — open GitHub issues

Pulled from https://github.com/Wulfic/mcMMO-Singleplayer/issues on **2026-08-03**.
10 open issues, zero comments on any of them. All of them are §G play-test findings.

Working rules for every item below (AGENTS.md, non-negotiable):
- Reason first, then code. Explore the seam before touching it.
- Unit test **and** a boot/play verification before anything is called done.
- Log every error path. No `@ts-ignore`-equivalents, no empty catches.
- Commit to `master`. No branches. No AI co-author trailer.

---

## ⚠️ Two traps that apply to HALF this list — read before starting

**1. Changing a config default does NOT reach an existing config.**
`copyMissingDefaults` back-fills only **absent** keys. Issues **#1, #3, #5, #6, #9, #10** all
touch `experience.yml` / `advanced.yml` / `config.yml` values. Editing the shipped resource
changes nothing for anyone who already has the file on disk — including the reporter.
Every such item needs an explicit decision: *migrate the on-disk value*, or *ship it as a new key*.
Do not close one of these by editing a YAML default and calling it fixed.

**2. The play-test instance is not `run/`.**
The reporter plays in a PrismLauncher instance, not the repo's `run/` dir. Its
`config/mcmmo/*.yml` and `mcmmo/players/<uuid>.yml` are the ground truth for "is this actually
tuned that way for them". `advancements/<uuid>.json` carries a timestamp per grant and is the
best forensic tool in the port. **Read the save before asking the reporter anything.**

---

## P0 — broken mechanics (a player cannot use the feature)

### [#4] Rolling never procs; holding shift on landing awards Sneak XP instead
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/4>

Symptom: land from a fall holding shift to Graceful Roll → no roll, but Stealth XP fires.

Where to look:
- [AgilityManager.java:120](src/main/java/com/gmail/nossr50/skills/agility/AgilityManager.java#L120) — `processFallDamage`
- [AgilityManager.java:170](src/main/java/com/gmail/nossr50/skills/agility/AgilityManager.java#L170) — `rollCheck(baseDamage, isGraceful, rngSuccess)`
- [AgilityManager.java:222](src/main/java/com/gmail/nossr50/skills/agility/AgilityManager.java#L222) — `canRoll()`
- The fall-damage trigger in [src/main/java/com/gmail/nossr50/fabric/](src/main/java/com/gmail/nossr50/fabric/) — **confirm the seam actually fires at all.**

Open questions to answer with code, not guesses:
1. Is `processFallDamage` reached on a real fall? (If not, this is a wiring bug, not a tuning bug.)
2. Where does `isGraceful` come from — is it reading real server-side sneak state, or a stale flag?
3. Is Stealth's sneak-distance accumulator claiming the landing tick and short-circuiting Agility?

Acceptance: a test that pins graceful-roll on a sneaking landing, a test that pins Stealth pays
**zero** for the landing tick, and a play verification of the actual proc.

### [#5] Super Breaker does not increase drop chance
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/5>

Symptom: mining speed goes up, drop rate does not.

Where to look: [MiningManager.java:182-194](src/main/java/com/gmail/nossr50/skills/mining/MiningManager.java#L182-L194) — `rollBonusDropCount()`.

Read the code before assuming the report is right. Today Super Breaker changes the **quantity**
of a successful roll (2 extra copies instead of 1), gated on
`AdvancedConfig.getAllowMiningTripleDrops()` — shipped `true` at
[advanced.yml:665](src/main/resources/advanced.yml#L665). It does **not** change the *probability*
of the `MINING_DOUBLE_DROPS` roll succeeding.

So there are two candidate defects and they need different fixes:
- **(a)** the reporter's on-disk `advanced.yml` has `AllowTripleDrops: false` → trap #1 above, nothing to fix in code;
- **(b)** legacy Bukkit really does raise the *chance*, and the port only raised the *quantity* → a real port defect.

**Verify against `legacy/` before writing a line.** That vendored tree is in the repo — read the
legacy call site, not just the method (the multiply-by-zero landmine). Then do the same audit for
Giga Drill Breaker and Berserk, which share this shape.

### ✅ [#2] Tamed pets don't follow through a long teleport — DONE 2026-08-05
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/2>

Symptom: wolves stay behind when the teleport distance is large. Wanted: follow unconditionally
**when in the same world**.

**The distance was never the gate.** `FollowOwnerGoal#tick` asks
`TameableEntity#shouldTryTeleportToOwner()` — which is nothing but `squaredDistanceTo(owner) >= 144`
— and on a yes calls `tryTeleportToOwner()`, with **no upper bound at all** (javap on the merged
jar). Vanilla already intends pets to keep up over any distance. What breaks is that a pet outside
the player's simulation distance **stops being ticked**, so the goal never runs at all and the pet
sits dormant where it was left. *Being ticked* is the gate, not the distance.

**Shipped:** `fabric/listeners/PetFollowTeleport`, behind `Skills.Taming.Pets_Follow_Teleport: true`
/ `Pets_Follow_Teleport_Radius: 32` in `config.yml`. New keys ⇒ `copyMissingDefaults` back-fills
them into an existing config for free (verified live against the pre-existing `run/config/mcmmo/`),
so **no `ConfigRetunes` entry was needed**. Documented in `wiki/Skills`, `wiki/Configuration` and
`wiki/Differences-from-mcMMO`.

- 🔑 **It rides `PlayerMovementTracker`'s per-tick sweep, not a teleport mixin.** A player is
  relocated by commands, pearls, chorus fruit, portals, respawn and other mods, and those do not
  share one method — `teleportTo(TeleportTarget)` and `requestTeleport(d,d,d)` bottom out in the
  network handler by different routes. A position delta cannot be bypassed by any of them, and the
  tracker already keeps the baseline. One tick of latency is free: entity unloading is queued.
- 🔑 **Vanilla's own `cannotFollowOwner()` is the eligibility gate** — sitting, leashed, ridden and
  spectating-owner in one `public final` call, so "sit means stay" needs no second implementation.
- ⚠️ **The fallback is refused over an airborne owner.** `tryTeleportNear` can find nowhere to put
  the pet (a ledge, an alcove) and nothing ever retries — so a direct placement fallback is
  load-bearing. But dropping a wolf out of an elytra flight trades "left behind" for "died on
  landing", which is strictly worse than the bug. Airborne ⇒ leave it, which is the vanilla outcome.
- 🔑 **The respawn stale-handle trap does not apply here.** `LazyEntityReference#resolve` checks
  `isRemoved()` on its cached entity and re-looks-up by UUID (bytecode), so vanilla's owner reference
  survives `respawnPlayer` on its own.
- ⚠️ The tracker needed a **world** baseline as well as a position one: the coordinates either side
  of a nether portal are both real and 8× apart, so a box drawn at the old position in the new world
  is somewhere plausible and wrong.
- 3 mutations run (dispatch moved below the profile guard; airborne refusal deleted; search box
  centred on arrival instead of departure) — 3 kills.

### ✅ [#7] Spears paid nothing at all — DONE 2026-08-05
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/7>

**Ruled a docs bug up front; the audit disproved that.** It was a real code defect, and a total one:
[EntityDamageListener.java](src/main/java/com/gmail/nossr50/fabric/listeners/EntityDamageListener.java)'s
`classifyMainHand` had **no spear arm**, so a spear returned `MeleeWeapon.OTHER` and
`applyAttackerWeaponBonus` returned on the spot — **no XP, no Spear Mastery, no Momentum**. Spears
could not leave level 0. Everything else in the skill was already built and shipped: manager, ranks,
config block, `/mcstats` renderer, milestone advancements, locale strings.

The arm was missing on a comment asserting that "neither the `spear` damage type nor any spear item
exists in vanilla 1.21.11". **Both exist** (javap on the merged jar): `Items.WOODEN_SPEAR` …
`NETHERITE_SPEAR`, `data/minecraft/tags/item/spears.json`, `data/minecraft/damage_type/spear.json`.
The belief was true of an earlier MC version and was never re-checked — and it had been copied into
the wiki, which is how the reporter found it.

**Shipped:** `MeleeWeapon.SPEAR` + the `ItemUtils.isSpear` arm + `skillOf` → `SPEARS`; Spear Mastery
composed in `MeleeDamageBonus`; **Momentum implemented** (it never had an effect body) as
`SpearsManager#processMomentum` → `PlatformPlayer#applySpeed`. Docs corrected in README, `wiki/Skills`,
`wiki/Super-Abilities`, `wiki/Differences-from-mcMMO`, and PLAYTEST_G session 1 line 19.

- 🔑 **Keying on the held item is the same test as legacy's damage-type dispatch.**
  `Item.Settings.spear(…)` stamps every spear with `DAMAGE_TYPE = DamageTypes.SPEAR`, and
  `PlayerEntity#attack` builds its source via `ItemStack#getDamageSource(weaponStack)` — so a swing is
  spear-typed *because* a spear is held. The 2-arg `DamageSource` ctor sets `source == attacker`, so
  the existing direct-melee gate needed no change.
- ⚠️ **The rank-0 landmine, third sighting** (§F #9, after Cripple): `getMomentumChanceToApplyOnHit(0)`
  evaluates `defaultMomentumValues[-1]` eagerly as the `getDouble` default and throws. Mutation-proven.
- 🔑 Legacy's hand-rolled `canMomentumBeApplied` comparison is **vanilla's**:
  `addStatusEffect` → `StatusEffectInstance#upgrade` accepts only a stronger-or-longer effect, and
  returns whether anything changed — so the "MOMENTUM ACTIVATED!" message stays honest.
- **Spears' super ability stays a placeholder** — upstream never shipped one either, same as Maces and
  Tridents. That part of the old docs was right for the wrong reason.
- `SPEARS_LIMIT_BREAK` remains a dead enum, consistent with the other seven weapon skills.

---

## P1 — tuning / feel (the mechanic works, the numbers are wrong)

### [#1] Tilling: the special effect triggers far too easily
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/1>

Symptom: tilling a row is painful because the effect constantly tries to activate.

Where to look:
- [SuperAbilityListener.java:135-140](src/main/java/com/gmail/nossr50/fabric/listeners/SuperAbilityListener.java#L135-L140) — every qualifying right-click on a block calls `readyToolSkills(mmoPlayer)`
- [SuperAbilityListener.java:319-322](src/main/java/com/gmail/nossr50/fabric/listeners/SuperAbilityListener.java#L319-L322) — right-click-air does the same
- [SuperAbilityListener.java:368-370](src/main/java/com/gmail/nossr50/fabric/listeners/SuperAbilityListener.java#L368-L370) — the hoe → Green Terra activation arm

Root cause hypothesis: tilling **is** a right-click-with-hoe, so every till re-readies the tool and
re-enters the activation path. Legacy has the same shape but a different interaction cadence.

Decide deliberately: suppress the ready when the click was a *till* (block actually changed to
farmland), add a cooldown, or require a distinct readying gesture. Whatever we pick must not break
the legitimate "ready hoe → strike → Green Terra" flow, which is load-bearing and order-sensitive
(see the comment at line 361).

### [#6] Stealth XP too slow — double the sneak rate
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/6>

Change: [experience.yml:910](src/main/resources/experience.yml#L910)
`Experience_Values.Stealth.Sneak.Baseline_Xp_Per_Second: 25.0 → 50.0`.

Before doing it:
- 25 XP/s already maxes a RetroMode 1000 skill in ~122 h. 50 makes it ~61 h — the fastest continuous
  earner in the mod, ahead of every movement domain. Confirm that's the intent.
- `Reference_Speed: 1.295` at [line 918](src/main/resources/experience.yml#L918) is **derived**
  (walk 4.317 × sneaking_speed 0.3), not tuned. Do not touch it to fix a rate complaint.
- Check whether the real complaint is the rate or the **gate**: `Require_Movement_Input: true`
  ([experience.yml:51](src/main/resources/experience.yml#L51)) pays nothing while carried. If the
  reporter was testing while standing still, the rate is not the problem.
- Trap #1 applies — their on-disk `experience.yml` keeps 25.0 forever unless migrated.

### ✅ [#3] Husbandry caps the wrong thing — DONE 2026-08-04
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/3>

Current: [HusbandryManager.java:128](src/main/java/com/gmail/nossr50/skills/husbandry/HusbandryManager.java#L128)
`DEFAULT_MULTI_BREED_MAX_ADDITIONAL_ANIMALS = 4` caps how many animals **get bred** by one item.

Wanted, two parts:
1. Move the cap from *breeding* to *XP payout* — breed as many as the radius reaches, but only N
   of them pay Husbandry XP per time window.
2. **Double** the cap (4 → 8).

This is a real design change, not a constant bump. The existing 4 exists to stop "one wheat into a
hundred-cow pen" from paying fifty breedings at once (see the rationale at
[lines 108-128](src/main/java/com/gmail/nossr50/skills/husbandry/HusbandryManager.java#L108-L128)).
A time-windowed award cap is the same shape as Unarmored's per-attacker cap and Agility's Dodge cap
— reuse that pattern rather than inventing a third.

⚠️ Cooldowns in this codebase count **world ticks**, not wall-clock. The window must too.

Acceptance: a test proving N+1 animals breed but only N pay; a test proving the window resets.

**Shipped.** `MaxAdditionalAnimals` is retired; the spread is bounded by the radius alone. The gate
is now `ExploitFix.Husbandry.Breed_Xp_Awards_Per_Window: 8` / `Breed_Xp_Award_Window_Seconds: 30` in
`experience.yml`, applied to **every** breed award per player, counted in world ticks.

- The 30 s is **derived, not tuned** — `AnimalEntity#lovePlayer` sets `loveTicks = 600` (bytecode),
  so one handful of feed's whole burst falls inside one window.
- ⚠️ **The old cap never bounded the XP rate at all.** It bounded XP *per item*, and wheat is free:
  twenty clicks in one breath paid fifty breedings straight through it.
- ⚠️ **A refused breeding marks no calf**, so the raise verb pays nothing for it either — otherwise
  the cap is a 20-minute delay, not a cap. Twins inherits the refusal.
- **No `ConfigRetunes` entry was needed** (superseding the earlier note): this shipped as *new keys*,
  which `copyMissingDefaults` back-fills into existing configs for free. Verified live.
- The retired key gets a `SkillRenames.LEGACY_CONFIG_PATHS` warning naming the new **file**.

---

## P2 — new features

### [#10] Per-skill enable/disable toggle
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/10>

Add an option to disable an entire skill. Scope this properly before coding — "disabled" has to
mean *all* of: no XP gain, no sub-skill procs, no super ability, no XP bar, no `/mcstats` section,
no milestone plaques. A half-disabled skill is worse than none.

Interacts with **#9** (both want a settings surface) and with the child-skill structure: disabling
Parkour/Swimming/Flying changes Agility's level, since Agility is the **mean of its parents** and
earns no XP of its own. Decide what a disabled parent contributes to that mean.

### [#9] Cheats tab in ModMenu
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/9>

A ModMenu tab with a toggle per major cheat method.

Where: [McMMOSettings.java](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java),
[McMMOModMenuIntegration.java](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOModMenuIntegration.java),
[ConfigDocument.java](src/main/java/com/gmail/nossr50/fabric/client/modmenu/ConfigDocument.java).

First task is not code — it's enumerating what "major cheat methods" means. Get that list from the
reporter. Then reuse the existing key-validation test that stops typo'd keys becoming silent
no-ops; every new toggle must be covered by it.

Do #10 and #9 together if the toggle surface is shared.

### [#8] `/mcrefresh` → OP only
<https://github.com/Wulfic/mcMMO-Singleplayer/issues/8>

Smallest item on the list. Add a permission-level requirement to the `mcrefresh` registration in
[McMMOCommands.java](src/main/java/com/gmail/nossr50/commands/McMMOCommands.java).

While in there: audit **every** command for the same hole. `mcrefresh` is unlikely to be the only
cheat-adjacent command registered without a level. Fix them in one pass, not one issue at a time.

---

## Suggested order

1. **#4** and **#5** — both are "the mechanic doesn't work", both are diagnosis-first.
2. **#1** — same listener family as #4's investigation; batch the play-test.
3. **#8** — small, self-contained, ship it.
4. **#3** — real design work.
5. **#6** — trivial edit, but blocked on the trap-#1 migration decision, which #3 also needs. Do them together.
6. ✅ **#7** — done. Was never a docs bug; the skill was wired to nothing.
7. ✅ **#2** — done. The distance was never the gate; being ticked was.
8. **#9** + **#10** — one settings pass, blocked on the cheat-method list. ⬅️ **next**

## Blocked on the reporter

- **#9** — which cheat methods get a toggle?
- **#6 / #3 / #5** — do we migrate their on-disk configs, or ship new keys?
