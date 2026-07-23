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
| Unit suite | ✅ **712 green** |
| Headless boot | ✅ `Done (1.097s)`, 0 exceptions, 0 mixin failures |
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

---

## Remaining code work (the short tail)

Nothing here blocks §G.

### §A — keystone residuals
- [ ] **K4 `SkillUtils`** — two leftovers: the legacy Haste-*potion* fallback branch (unreachable with
      the bundled `hidden.yml`, so this is optional) and the `RepairableManager` max-durability override.
- [ ] **K9 placed-block tracker — cross-restart persistence.** In-memory only today, dropped at world
      close, so a placed block re-mined **after a restart** pays out again. The in-session
      place→mine→repeat farm is fully closed; this is the residual hole. Legacy used region files
      (`HashChunkManager`). Also still open, both rare: multi-place upper halves (double plants) are
      unmarked, and piston-moved placed blocks are not followed.

### §B — sub-skill residuals
- [ ] **Alchemy — Concoctions ingredient-tier gating.** *Genuinely blocked, not deferred by choice:*
      `BrewingStandBlockEntity#canCraft` is a static with no `BlockPos`, so the owner's tier cannot be
      resolved there without risking a never-completing brew loop. Recipe recognition is currently
      tier-permissive (any player can brew any mcMMO potion). Needs a different seam.
- [ ] **Repair** — the enchanted-repair-material avoidance branch (`getAllowEnchantedRepairMaterials`),
      and calling `SkillUtils.removeAbilityBuff` before repairing a haste-boosted tool.
- [ ] **Fishing** — the exploit item-removal punishment (the spam/scarcity *detection* is already wired;
      only the confiscation half is missing).

### §E — cosmetic
- [ ] `ExperienceBarHideTask` / XP-bar `processPostXpEvent`. Cosmetic; fine to slip to Pass 2.

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
