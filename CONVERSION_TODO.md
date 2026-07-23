# mcMMO → Singleplayer Fabric — WHAT'S LEFT

**This file is only the open work.** Everything already landed — with the seam it rides, its deliberate
deviations, and the 15 upstream defects found on the way — is in
[CONVERSION_DONE.md](CONVERSION_DONE.md). Read the relevant entry there before touching a subsystem.

---

## State of play (2026-07-23)

| | Status |
|---|---|
| 19 skill managers | ✅ all live |
| §A keystones (K1–K9) | ✅ all landed or honestly collapsed |
| §B — every skill earns XP | ✅ feature-complete |
| §C — combat on-hit sub-skills | ✅ complete |
| §D/§E — gathering bodies, super abilities, runnables | ✅ all ported |
| Unit suite | ✅ **750 green** |
| Headless boot | ✅ `Done (1.159s)`, 0 exceptions, 0 mixin failures, shutdown verified |
| **§G — real gameplay verification** | ❌ **never done** |

> **🔴 The critical path is §G, not more code.** Pass 1's remaining code work is a short tail of
> refinements — none of it blocks a play test. Everything in §A–§F is **"boot-verified, never played":**
> the boot proves mixins apply and configs parse, and proves *nothing* about whether a super ability
> actually fires, whether Tree Feller fells a tree, or whether the XP rates are sane. That is now the
> port's single largest risk, and it grows with every additional body ported blind.

**Recommended next action: run §G.** Do the code tail below only if a play test is blocked.

---

## §G. Pass-1 exit criteria — THE CRITICAL PATH

None of this can be closed by a headless boot. It needs a real client session.

- [ ] All 19 skills earn XP from their real action.
- [ ] Every super ability activates, applies its effect, expires, and cools down correctly — all eight:
      Giga Drill Breaker, Super Breaker, Berserk, Serrated Strikes, Skull Splitter, Tree Feller,
      Green Terra, Blast Mining.
- [ ] Every weapon skill's core on-hit sub-skill fires.
- [ ] `./gradlew build` green **and** a real client session confirms the above by observation.
- [ ] No new stubs / `@SuppressWarnings` / empty catches introduced while wiring.

**Verify these specifically — each is a behaviour change a player would notice, flagged when it landed:**
- [ ] **Harvesting crops from horseback now pays nothing.** `Skills.Herbalism.Prevent_AFK_Leveling`
      ships `true` and was previously read-but-ignored; it is now consulted. User-visible.
- [ ] **Repairing enchanted gear below Repair 100 (RetroMode) destroys every enchantment.** Faithful to
      legacy (`canKeepEnchants()` is `rank != 0`), and harsh enough to read as a bug in play.
- [ ] **Combat XP is per-hit, so the XP *rate* will have shifted materially** — you now get paid for
      damage to things you never kill. Verify at 1.0× single-mode, **not** RetroMode 10×.
- [ ] **A chorus tree now pays for every block** (upstream bug #13 meant it paid for one). A big tree
      pays every `Chorus_Flower: 25` tip — see the cap question in §F.
- [ ] Interaction-driven bodies that no headless test can reach: Green Thumb block / Shroom Thumb /
      berry-bush harvest, Hylian Luck's sapling + flower-pot **tag** branches (unit tests cover only the
      hardcoded flower/bush members), Call of the Wild summons, a real brew, a real fished-up book.
- [ ] **Overfishing now confiscates the catch.** Past `ExploitFix.Fishing.OverFishLimit` (10) casts at
      one spot the fish *and* the vanilla XP orbs are destroyed, not merely unpaid — plus two warnings
      the port previously dropped ("scaring the fish", "low resources"). Verify the two warnings read
      sensibly and the confiscation isn't reachable by accident during normal play.
- [ ] **Placed blocks stay ineligible across a world reload.** Place ore, quit to title, reopen the
      world, mine it: it must pay no XP and no bonus drops. Then break a *natural* block next to it to
      confirm the flags didn't over-apply. The store is unit-proven and boot-proven, but the
      place→save→reload→mine loop has never been walked in a client.
- [ ] **Anything that reads the player through `mmoPlayer.getPlayer()` after a death — and after an
      End exit.** The stale-handle bug is now fixed and unit-tested (see *Newly found*), but the fix
      itself is unobserved. Die once, then check that sounds, action-bar notifications and a super
      ability all still work; repeat for an End-portal return, which recreates the entity without any
      death. Also confirm Dodge XP is withheld for the first 5s after respawning.

---

## Remaining code work (the short tail)

Nothing here blocks §G.

### §A — keystone residuals
- [ ] **K4 `SkillUtils`** — one leftover: the legacy Haste-*potion* fallback branch of
      `handleAbilitySpeedIncrease`, unreachable with the bundled `hidden.yml`
      (`Options.EnchantmentBuffs=true`), so this is optional. *(The `RepairableManager` max-durability
      override is now wired — see `CONVERSION_DONE.md`.)*
- [x] **K9 placed-block tracker — cross-restart persistence DONE.** New `PlacedBlockStore` writes the
      flags to `<worldRoot>/mcmmo/placed_blocks.dat` (binary: magic + version + per-world
      `worldKey`/count/packed `long`s), loaded at server start, saved on the autosave tick and at
      server stop *before* the tracker is cleared. Collapses legacy's per-chunk
      `McMMOSimpleRegionFile` shard set to one document — sound because the tracked set is bounded by
      *still-standing hand-placed* blocks (every player break clears its position before any skill
      branch runs), not by world size. Every load failure is fail-open + logged (missing / foreign /
      truncated / future-version / corrupt count all leave the tracker empty rather than throwing
      into world load); writes go via a `.tmp` + `ATOMIC_MOVE`. **Still open, both rare and
      pre-existing:** multi-place upper halves (double plants) are unmarked, and piston-moved placed
      blocks are not followed. Newly noted: a block removed *without* a player break (creeper blast,
      fire, lava) leaves a stale flag, which now survives restarts too — harmless unless a natural
      block later occupies that exact position, and self-healing the first time one is broken there.

### §B — sub-skill residuals
- [ ] **Alchemy — Concoctions ingredient-tier gating.** *Genuinely blocked, not deferred by choice:*
      `BrewingStandBlockEntity#canCraft` is a static with no `BlockPos`, so the owner's tier cannot be
      resolved there without risking a never-completing brew loop. Recipe recognition is currently
      tier-permissive (any player can brew any mcMMO potion). Needs a different seam.

### §E — cosmetic
- [ ] `ExperienceBarHideTask` / XP-bar `processPostXpEvent`. Cosmetic; fine to slip to Pass 2.

### Newly found, unscheduled

- [x] **⚠️ `PlatformPlayer` stale handle — FIXED.** Was inferred; now **confirmed by bytecode**:
      `PlayerManager#respawnPlayer` calls `ServerWorld.removePlayer(old, reason)` and then
      `new ServerPlayerEntity(...)`. Worse than "after death" — the End-exit path routes through the
      same method (`alive == true`), so no death is needed to break it. Fixed by making the handle
      `volatile` + `PlatformPlayer#rebind` (UUID-guarded, logs a refusal), driven from a new
      `ServerPlayerEvents.AFTER_RESPAWN` handler in `PlayerSessionListener`. **Rebinding in place
      rather than rebuilding the `McMMOPlayer`** is load-bearing: `AbilityCooldownTask` /
      `AbilityDisableTask` capture the wrapper directly and must survive a mid-ability death.
      *Still needs §G confirmation in a live world — the seam is proven, the gameplay effect is not.*
- [x] **Legacy's respawn handler was never ported — now wired.** `McMMOPlayer#getRespawnATS` /
      `actualizeRespawnATS` (legacy `PlayerListener#onPlayerRespawn` +
      `PlayerProfileLoadingTask`) plus the Acrobatics Dodge XP grace period that reads them, which
      un-deadens `Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS` (a constant nothing consulted). Seconds, not
      millis — `cooldownExpired` multiplies by `TIME_CONVERSION_FACTOR`, so millis would push every
      deadline ~31,000 years out and silently kill Dodge XP for the session. Legacy's other consumer
      of the timestamp is the PvP combat-XP branch, unreachable in singleplayer.
- [ ] **Decided NOT to port: legacy `PlayerListener#onPlayerDeathNormal`** (strip super-ability dig
      boosts on death). Recorded so nobody "restores" it. With the rebind above, the already-scheduled
      `AbilityDisableTask` now sweeps the *correct* inventory, which covers the `keepInventory` case.
      The items-dropped case is not covered — but it is not covered upstream either: Bukkit populates
      `PlayerDeathEvent#getDrops()` from the inventory *before* that handler runs, so mutating the
      inventory there cannot change what drops. Porting it would add a handler that fixes nothing.
      Residual (narrow, self-healing): dying with `keepInventory=false` mid-Super-Breaker drops a tool
      whose `+EnchantBuff` Efficiency is never stripped — re-activating the ability on that tool strips
      it, since activation calls `removeSuperAbilityBoostFromMainHand` first. **Confirm in §G.**
- [x] **Upstream defect #16 — `potions.yml`'s pre-1.13 `WATER_LILY` — FIXED (aliased).** Boot logged
      `No vanilla item for material name 'WATER_LILY'` (one WARN, one ingredient), so that Alchemy
      ingredient silently did nothing upstream too; the modern registry path is `lily_pad`. Same shape
      as #1/#2/#10 (a stale Bukkit key in shipped YAML), so it took the same remedy: a
      `LEGACY_NAME_ALIASES` table on `Materials#idOf` — the one funnel every config material name
      passes through — rather than editing the vendored YAML. Deliberately minimal (one entry, only
      names the bundled configs actually contain) so it never becomes a speculative port of Bukkit's
      whole legacy-material table. **Aliases unqualified names only:** an explicitly namespaced
      `foo:water_lily` is another mod being specific and is not ours to rewrite.

---

## §F. Open tuning & config questions

Decisions, not code. Most are "upstream ships a knob that lies to the operator" — the port reproduced
the behaviour faithfully rather than inventing a fix, and the call on whether to wire or strip each one
is deferred to the tuning pass. **Seven of these are the same family**, which is itself the finding.

- [ ] **Cross-check each body's constants/formula against upstream as it is exercised.** The bodies not
      yet verified against *real observed behaviour* are exactly the §G list.
- [ ] **Upstream bug #8 — Gore never rolls.** `advanced.yml` ships `Gore.ChanceMax: 100.0` plus a
      `MaxBonusLevel` ladder and the validator checks both, but `TamingManager#gore` contains **no
      `ProbabilityUtil` call at all** — so Gore fires on *every* wolf hit once unlocked instead of
      scaling. Verified byte-identical against upstream master, so it is genuine, and ported faithfully
      (no roll). **The most player-visible of the family:** it hands a low-level tamer a permanent
      unconditional 2× wolf-damage multiplier the config says they should rarely get. Decide: wire the
      roll (a balance change) or strip the keys + comments.
- [ ] **Known deviation — `SuperAbilityListener` has no creative-mode gate.** Legacy gates its *entire*
      interact handler on `getGameMode() != CREATIVE`; ours does not, so super-ability readying,
      activation and remote detonation all work in creative. **Sweep the listener once and decide
      deliberately** rather than patching branch by branch.
- [ ] **Blast Mining yield semantics — verify by observation.** Bukkit's `yield` has no modern
      equivalent; the port derives `1 / explosion power`, which is what vanilla's own
      `ExplosionDecayLootFunction` uses (bytecode-verified). Sound, but it means Bigger Bombs *lowers*
      per-block yield as it widens the blast — check the net payout still feels like an upgrade at rank.
- [ ] **Is the `chorus_plant: 22` tall-plant cap still dead?** It was unreachable upstream (bug #13 meant
      chorus traversal never ran). Now that a chorus break rewards the whole tree, whether chorus XP
      needs a cap is a **real balance question** rather than a hypothetical. The port kept legacy's
      behaviour (no cap on the delayed path) rather than inventing a tuning decision.

**Dead configs / strings — ported faithfully, decide whether to wire or strip:**
- [ ] `DebrisReduction` — `MiningManager.getDebrisReduction()` reads a per-rank value
      `blastMiningDropProcessing` never consults; the debris chance is a hardcoded 10%.
- [ ] Rupture `Explosion_Damage` — the getter and `METADATA_KEY_EXPLOSION_FROM_RUPTURE` have zero
      callers upstream, yet `advanced.yml` promises "if Rupture runs for 5 seconds it explodes".
- [ ] Serrated Strikes `BleedTicks` — the shipped key is `BleedTicks`, the getter reads `RuptureTicks`,
      so the knob is read by nothing and the AoE just uses the normal Rupture duration.
- [ ] Shake `Drop_Level` and per-drop `XP` — both parsed onto every `ShakeTreasure`, neither consulted
      (`chooseDrop` walks drop chance alone; the flat `Experience_Values.Fishing.Shake` is paid).
      Benign as shipped (every value is `0`).
- [ ] `Fishing.FishermansDiet.RankChange` — `advanced.yml` claims it sets when the diet bonus applies;
      nothing upstream reads it (ranks come from `skillranks.yml`). Note the tell: Herbalism's
      identically-behaving Farmer's Diet ships no such knob at all.
- [ ] `Salvage.Skills.ArcaneSuccess` — a shipped locale string nothing ever sends, so a *perfect*
      extraction is silent. Compare Arcane Forging, whose equivalent three-way report does fire all three.
- [ ] `METADATA_KEY_MULTI_SHOT_ARROW` — **not an upstream bug; recorded so nobody "restores" it.** The
      vendored snapshot stamps a key nothing reads, but upstream master has *deleted* it outright
      (Paper/Spigot handles multishot pickup natively). The vendored tree is simply behind master. No
      action; do not port.
- [ ] **Config-interaction gotcha to keep in mind:** RetroMode (default `true`) + the live 10× XP rate
      make drop-level gates clear fast. That is tuning *feel* — verify the math at 1.0× single-mode.

---

## Pass 2 — explicitly deferred (do NOT block Pass 1 on these)

- In-game config menu (`Screen`) — client-side GUI over the config files.
- **Vampirism** retarget to villagers/mobs (KEEP-but-rework from scope reduction).
- **Hardcore** stat-loss on death (optional toggle).
- Per-skill info commands; XP-bar cosmetics; sound/particle polish.
- Phase 9 third-party-removal verification sweep.

**Standing adapter gaps that only Pass 2 cosmetics need:** a particle adapter (Dodge, Greater Impact,
Rupture bleed, wolf shake/hearts/smoke, COTW despawn all drop their particle effects) and custom potion
name/lore/colour.

---

## Testing checklist

The Pass-1 testing checklist this file once held is recoverable from git history. Per the original plan
it is worth running only **after** §G confirms the port behaves in a live world — before that you would
just be re-discovering known gaps.
