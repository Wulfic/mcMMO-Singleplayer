# §G — Pass-1 Play-Test Plan

Everything in §A–§F is **boot-verified, never played**. This file is the whole in-game verification
debt, broken into runnable sessions. Work top to bottom; the sessions are ordered so a failure early
invalidates as little of the later work as possible.

**Rule: log what you observe, not what you expected.** A skill that "seems fine" is not a pass — a pass
is a `/mcstats` delta, a message on screen, or a block/item that changed. Anything ambiguous goes in
the FAIL column with a note.

---

## 0. Setup (do this once)

```
./gradlew runClient
```

1. **Create a new world: Survival, `Allow Cheats: ON`.** Cheats are required — `/addlevels` and
   `/addxp` are gated at vanilla permission level 2.
2. Configs are written to `run/config/mcmmo/` on first boot. The world's mcMMO data (player profiles,
   `placed_blocks.dat`) lands under `run/saves/<world>/mcmmo/`.
3. Keep `run/logs/latest.log` open in a second window. Every error path in this port logs; a silent
   failure in-game is usually a loud one in the log.

**Commands this port actually has** (the legacy command set was *not* fully ported — do not go looking
for `/mctop`, `/inspect`, `/skillreset`, or the per-skill commands):

| Command | Use |
|---|---|
| `/mcstats` | **Your primary instrument.** Level + XP for all 19 skills. Run it before and after every action. |
| `/mcability` | Toggle super abilities on/off |
| `/mcrefresh` | Clear all super-ability cooldowns — use this constantly in session 2 |
| `/addlevels <skill> <n>` | Grant levels |
| `/addxp <skill> <n>` | Grant raw XP |
| `/mcmmo` | Version / info |

**⚠️ RetroMode is ON by default** (`config.yml` → `General.RetroMode.Enabled: true`). Every unlock
level in `skillranks.yml` is ×10. **Rank 1 of every super ability = skill level 50**, not 5. So the
standing setup for sessions 2–3 is:

```
/addlevels mining 100
/addlevels excavation 100
/addlevels woodcutting 100
/addlevels herbalism 100
/addlevels swords 100
/addlevels axes 100
/addlevels unarmed 100
/addlevels taming 100
/addlevels fishing 100
/addlevels repair 100
/addlevels salvage 100
/addlevels archery 100
/addlevels alchemy 100
```

---

## 1. ⚠️ READ BEFORE YOU START — the combat activation path is brand new

An earlier revision of this plan told you Serrated Strikes and Skull Splitter were dead on arrival.
**They are now wired** — legacy's combat-path activation guard
(`if (manager.canActivateAbility()) mmoPlayer.checkAbilityActivation(...)`, which opens
`CombatUtils#processSwordCombat` / `processAxeCombat` / `processUnarmedCombat`) had never been
ported, and now is, in
[EntityDamageListener#maybeActivateSuperAbility](src/main/java/com/gmail/nossr50/fabric/listeners/EntityDamageListener.java).
The block path in
[SuperAbilityListener.java:365-393](src/main/java/com/gmail/nossr50/fabric/listeners/SuperAbilityListener.java#L365-L393)
still covers the five block-struck abilities.

What that means for this plan:
- Session 2 items **SS** and **SK** are now expected to **PASS**, and they are the *least*-exercised
  code in the build — nothing about them has ever run in a live world. Treat them as the highest-risk
  rows in session 2, not a formality.
- The AoE effect bodies behind them
  ([EntityDamageListener.java](src/main/java/com/gmail/nossr50/fabric/listeners/EntityDamageListener.java))
  were already wired but **unreachable until now**, so their effect is being observed for the first
  time. Watch that the AoE hits *nearby* mobs and never double-hits the primary target.
- Legacy's ordering is reproduced deliberately: activation runs **before** the damage bonus, so the
  activating swing is itself buffed and itself does the AoE. If the first hit looks unusually strong,
  that is correct, not a bug.
- **Berserk now activates by punching a mob as well as a block.** Test **BK** both ways.

---

## Session 1 — every skill earns XP (~45 min)

The §G bar is "all 19 skills earn XP from their real action." Method for every row: `/mcstats`, do the
action 5–10×, `/mcstats` again, record the delta. **A zero delta is a failure even if nothing errored.**

| # | Skill | Action | Notes / what would make this a false pass |
|---|---|---|---|
| 1 | Mining | Break stone, then coal/iron ore | Use **natural** blocks only — placed blocks pay nothing by design (§A/K9) |
| 2 | Excavation | Shovel dirt / sand / gravel | |
| 3 | Woodcutting | Axe logs | |
| 4 | Herbalism | Break mature wheat | Must be **mature**; immature is maturity-gated to zero |
| 5 | Swords | Hit a mob with a sword | XP is **per hit** now, not per kill — expect a delta on the first swing |
| 6 | Axes | Hit a mob with an axe | |
| 7 | Unarmed | Punch a mob bare-handed | |
| 8 | Maces | Hit a mob with a mace | |
| 9 | Tridents | Melee a mob with a trident | Melee, not thrown — the thrown path is Tridents too, test both |
| 10 | Archery | Shoot a mob with a bow | Also confirm the **distance** bonus: a far shot should pay more than a point-blank one |
| 11 | Crossbows | Shoot a mob with a crossbow | Same distance check |
| 12 | Acrobatics | Take fall damage and survive; separately, get hit and dodge | Two sources — roll and dodge |
| 13 | Fishing | Cast and catch | Move spots between casts, see session 4/OF. **Also newly wired: the Treasure Hunter vanilla-XP boost.** At Fishing 0 a catch must drop its normal vanilla XP orb (the multiplier is 0 at Treasure Hunter rank 0 and is deliberately guarded to a no-op — if orbs vanish, the guard broke). At high rank the orb should be visibly larger |
| 14 | Taming | Have a tamed wolf damage a mob; also tame a wolf | Wolf-assist XP is ×3 |
| 15 | Repair | Right-click a damaged tool on an **iron block** | |
| 16 | Salvage | Right-click a damaged tool on a **gold block** | |
| 17 | Smelting | Smelt ore in a furnace and take the result | Take it both by click **and** by shift-click — different code paths |
| 18 | Alchemy | Brew a potion at a brewing stand | |
| 19 | ~~Spears~~ | **N/A — unreachable.** No spear item and no `spear` damage type exists in 1.21.11. Documented collapse; skip. |

---

## Session 2 — all eight super abilities (~45 min)

**Mechanics you need:** *ready* the tool by **right-clicking a block** (or air) with it held, then
**left-click/strike** an eligible block within the readiness window. `/mcrefresh` between every attempt
so you're never fighting a cooldown. Also: **an item in your off-hand suppresses activation** unless
you're sneaking or mounted ([SuperAbilityListener.java:580](src/main/java/com/gmail/nossr50/fabric/listeners/SuperAbilityListener.java#L580))
— empty your off-hand before you conclude anything failed.

For each: does it **activate** (message + sound), does the **effect** apply, does it **expire** on
schedule, and does the **cooldown** hold afterwards? All four, not just the first.

| # | Ability | Skill | How to trigger | Effect to observe |
|---|---|---|---|---|
| SB | Super Breaker | Mining | Ready a pickaxe, strike stone/ore | Near-instant mining + triple drops |
| GD | Giga Drill Breaker | Excavation | Ready a shovel, strike dirt/sand/gravel | Instant dig + extra treasure rolls |
| TF | Tree Feller | Woodcutting | Ready an axe, strike a log | **Whole tree falls.** Also try a huge jungle tree — check the threshold/leaf handling and that it doesn't lag or truncate |
| GT | Green Terra | Herbalism | Ready a hoe, strike a crop | Triple drops; **and** right-clicking cobble with seeds converts to mossy |
| BK | Berserk | Unarmed | Ready fists (empty hand), strike dirt/gravel/**snow**/**glass** — **and separately, punch a mob** | Instant break of soft blocks; the activating strike itself insta-breaks. The punch-a-mob activation is newly wired (§1) |
| BM | Blast Mining | Mining | Place TNT, hold **flint & steel**, right-click **thin air** while looking at it | Remote detonation + ore drops from the blast. Also confirm right-clicking the TNT *directly* with flint & steel is **refused** (the don't-blow-yourself-up guard) |
| SS | Serrated Strikes | Swords | Ready a sword, hit a mob | **Newly wired — highest risk in this table (§1).** AoE bleed onto nearby mobs; the activating swing should itself AoE |
| SK | Skull Splitter | Axes | Ready an axe, hit a mob | **Newly wired — see §1.** AoE damage to nearby mobs; primary target must not be hit twice |

Also, once per ability: let it run to expiry without `/mcrefresh` and confirm (a) the "ability wore off"
message fires, (b) re-activating immediately is refused with a cooldown message, (c) `/mcrefresh`
clears it.

---

## Session 3 — combat on-hit sub-skills (~30 min)

One core sub-skill per weapon skill must visibly fire. Levels from setup are enough to unlock all of
these; if one never fires in ~20 hits, that's a fail worth logging.

- [ ] **Swords → Rupture** — a bleed DoT: the mob keeps taking damage after you stop hitting it
- [ ] **Swords → Counter Attack** — a mob attacking you takes reflected damage
- [ ] **Axes → Critical Strikes** — occasional doubled damage
- [ ] **Axes → Greater Impact** — an *unarmored* mob gets knocked back hard
- [ ] **Axes → Armor Impact** — an *armored* mob (zombie in iron) loses armor durability, no bonus damage
- [ ] **Unarmed → Disarm** — a mob holding an item drops it
- [ ] **Unarmed → Iron Grip / Arrow Deflect** — arrows deflected while bare-handed
- [ ] **Archery → Skill Shot** — increased bow damage at level
- [ ] **Archery → Arrow Retrieval** — arrows recovered from mob corpses
- [ ] **Crossbows → Trick Shot** — bolts ricochet off surfaces
- [ ] **Tridents → Impale** — bonus trident damage
- [ ] **Maces → Crush** (flat bonus) and **Cripple** (slow applied to target)
- [ ] **Taming → Gore** — ⚠️ see §F below; expect this to fire on **every** wolf hit, which is upstream
      behaviour but reads as broken. Confirm it, don't fix it here
- [ ] **Acrobatics → Dodge** — reduced damage on an incoming hit
- [ ] **Acrobatics → Roll / Graceful Roll** — reduced or negated fall damage

---

## Session 4 — the five flagged behaviour changes (~30 min)

These are deliberate, user-visible changes made during the port. Each was flagged when it landed
because a player would notice it and call it a bug. **The test is "does this read as sane," not "does
it work."** Your judgement is the deliverable here.

- [ ] **HB — Harvesting crops from horseback now pays nothing.**
      `Skills.Herbalism.Prevent_AFK_Leveling` ships `true` and was previously read-but-ignored; it is
      now consulted. Mount a horse, harvest a wheat field, confirm zero Herbalism XP. **Then decide
      whether that's the behaviour you want**, because it will surprise people.

- [ ] **RE — Repairing enchanted gear below Repair 100 (RetroMode) destroys every enchantment.**
      Faithful to legacy (`canKeepEnchants()` is `rank != 0`), and harsh enough to read as a bug.
      Take an enchanted damaged sword, repair it at an iron block at low Repair, watch the enchants
      vanish. Then `/addlevels repair 1000` and confirm they survive.

- [ ] **CX — Combat XP is per-hit, so the whole XP *rate* has shifted.** You now get paid for damage to
      things you never kill. **Verify this at 1.0× Standard mode, not RetroMode 10×** — set
      `config.yml` → `General.RetroMode.Enabled: false`, delete the world, start fresh. Grind a mob
      farm for 5 minutes and judge whether the curve feels like mcMMO or like a cheat.

- [ ] **CH — A chorus tree now pays for every block.** Upstream bug #13 meant it paid for one. Go to
      the End, break the base of a large chorus tree, watch the Herbalism XP. Every
      `Chorus_Flower: 25` tip pays. **This is the §F cap question** — record the total XP from one
      big tree so the cap decision has a real number behind it.

- [ ] **OF — Overfishing now confiscates the catch.** Past `ExploitFix.Fishing.OverFishLimit` (10)
      casts at one spot, the fish **and** the vanilla XP orbs are destroyed, not merely unpaid, plus
      two warnings the port previously dropped. Cast 12× at one spot. Check: (a) the "scaring the
      fish" and "low resources" warnings read sensibly and fire at a sane point, (b) you can't hit
      the confiscation by accident during normal play.

---

## Session 5 — interaction-only bodies (~30 min)

No headless test can reach any of these; they are pure §G debt.

- [ ] **Green Thumb (block)** — right-click cobblestone with wheat seeds → mossy cobblestone. Note the
      seed is consumed **before** the roll, so a failure still costs it (faithful, but confirm it
      doesn't feel broken)
- [ ] **Green Thumb (replant)** — harvest mature wheat with a hoe while **not sneaking** and holding
      seeds → the crop replants itself ~1s later
- [ ] **Shroom Thumb** — hold a mushroom (with one brown *and* one red somewhere in the pack),
      right-click dirt/grass → mycelium-ish conversion, and the held mushroom must **not** also be
      placed
- [ ] **Berry bush harvest** — right-click a ripe sweet berry bush → Herbalism XP paid one tick later
      (the anti-exploit delayed confirm). Right-click an **unripe** bush → no XP
- [ ] **Hylian Luck — sapling branch** — sword-break a sapling, look for treasure replacing the drop.
      ⚠️ Only the hardcoded flower/bush members are unit-tested; **the sapling and flower-pot *tag*
      branches are verified here for the first time**
- [ ] **Hylian Luck — flower-pot branch** — sword-break a potted plant. Note the legacy quirk: a
      *failed* roll still consumes the pot
- [ ] **Call of the Wild** — **sneak + left-click a block** holding **10 bones** (wolf), **10 cod**
      (cat), or **10 apples** (horse). ⚠️ Left-click-*air* is not wired (Fabric exposes no callback),
      so you must be looking at a block or the ground. Confirm: pet spawns, despawns after 240s, the
      per-player limit holds, and the summoned pet **pays no combat XP** (it's flagged transient)
- [ ] **A real brew** — brew a potion end to end. Confirm Catalysis speeds it up at rank (MaxSpeed 4.0
      ⇒ a 400-tick brew in ~100 ticks) and that the stand doesn't start a *second* brew or eat an
      extra blaze powder
- [ ] **A real fished-up book** — fish until an enchanted book drops; confirm the enchant is sane and
      that you can fish a sword with conflicting enchants (Sharpness + Smite) — that's upstream bug
      #11, **fixed** here, so you should **not** see conflicts

---

## Session 6 — lifecycle & persistence (~20 min)

The riskiest section: these are the bugs that only appear across a save/load or an entity recreation.

- [ ] **PB — Placed blocks stay ineligible across a world reload.** Place an ore block, **quit to
      title**, reopen the world, mine it. It must pay **no XP and no bonus drops**. Then break a
      *natural* block right next to it to confirm the flags didn't over-apply. The store is
      unit-proven and boot-proven; this loop has never been walked in a client.

- [ ] **DE — Everything still works after a death.** The stale-`PlatformPlayer` bug is fixed and
      unit-tested, but the fix itself is unobserved. Die once, then confirm: sounds still play,
      action-bar notifications still appear, `/mcstats` still resolves you, and a super ability still
      activates.

- [ ] **EN — Same, after an End exit.** ⚠️ This is the nastier case: the End-portal return routes
      through `PlayerManager#respawnPlayer` and recreates the entity **without any death**. Go to the
      End, kill/avoid the dragon, exit through the portal, repeat the DE checks.

- [ ] **DX — Dodge XP is withheld for 5s after respawning.** Die, respawn, immediately take a hit —
      no Acrobatics Dodge XP. Wait 5s, take another — XP resumes.

- [ ] **MA — Die mid-ability.** Activate Super Breaker, die while it's running, respawn. The ability
      should end cleanly and the tool's `+EnchantBuff` Efficiency should be stripped. **Known narrow
      residual:** dying with `keepInventory=false` mid-Super-Breaker drops a tool whose buff is never
      stripped — re-activating the ability on that tool strips it. Confirm the residual is as narrow
      as claimed and self-heals.

- [ ] **SV — Profile survives a clean shutdown.** Earn XP, quit to title properly, reopen: levels intact.

---

## Session 7 — tuning observations (§F input, ~ongoing)

Don't fix anything here. Just record numbers so the §F decisions stop being hypothetical.

- [ ] **Gore (upstream bug #8)** — fires on *every* wolf hit once unlocked instead of rolling, giving a
      low-level tamer a permanent unconditional 2× wolf damage. Record how strong that feels.
      Decision pending: wire the roll (a balance change) or strip the keys.
- [ ] **Creative-mode gate** — legacy gates its whole interact handler on non-creative; this port does
      not. In creative, confirm that super-ability readying, activation and remote detonation all
      still work. Then decide whether that's acceptable.
- [ ] **Blast Mining yield** — the port derives Bukkit's `yield` as `1 / explosion power`. That means
      **Bigger Bombs lowers per-block yield as it widens the blast.** Mine a fixed ore vein at rank 1
      and again at max rank; record total ore. Does it still feel like an upgrade?
- [ ] **Chorus cap** — the number from session 4/CH goes here.
- [ ] **XP curve at 1.0× Standard** — the number from session 4/CX goes here.

---

## Reporting

For each item, one line: `ID | PASS/FAIL/PARTIAL | what you actually saw`. Anything FAIL or PARTIAL —
grab the surrounding `run/logs/latest.log` lines too. Session 1 and 6 failures are blockers; session 4
and 7 are judgement calls, not bugs.
