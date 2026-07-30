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

## Session 8 — Stealth, Snow Walker and the XP bars (~30 min)

All new in Pass 2 and **none of it has ever been played**. Everything below is unit-proven and
boot-clean only. Grant levels with `/addlevels stealth 1000` etc.

### 8a. Stealth XP — the anti-AFK gate is the whole skill

| # | Action | Expect | What would make this a false pass |
|---|---|---|---|
| ST1 | Crouch-walk 200 blocks on dry land | Stealth XP climbs steadily | — |
| ST2 | **⚠️ Check the log first.** Search `latest.log` for `Stealth: server-side movement input observed` | The line appears once, on your first crouched step | **If it never appears, ST1's XP is coming from somewhere it shouldn't, or the skill is earning zero.** The gate reads `getPlayerInput()`; if this client never sends input packets the whole skill silently pays nothing. This is the single most important check in the session |
| ST3 | Crouch and stand still | **Zero** XP | — |
| ST4 | Crouch in a boat / minecart being pulled along | **Zero** XP | — |
| ST5 | Crouch-swim, and crouch in a water current | **Zero** XP | Crouch-swimming is ~2.3× the sneak reference speed — if it pays, it is the best XP source in the mod and the ruling that closed it has regressed |
| ST6 | Tape shift down in a flowing-water channel and walk away | **Zero** XP | The exploit the skill is designed against ("sticky keys op") |
| ST7 | Crouch-walk while sprinting is impossible — confirm Parkour earns **nothing** during ST1 | Parkour XP unchanged | One movement state must feed exactly one skill |

### 8b. Stealth sub-skills

| # | Action | Expect |
|---|---|---|
| ST8 | Crouch at Stealth 1 vs Stealth 1000 | Noticeably faster crouching at rank. **Should approach but never exceed walking speed** — vanilla clamps the attribute at 1.0 |
| ST9 | Crouch through a 1-block gap (crawling) | Also faster. Known and intended side effect of using `sneaking_speed` |
| ST10 | Stop crouching | Speed bonus gone **immediately**, not after a delay |
| ST11 | Die while crouched, respawn, crouch again | Bonus still applies (the respawn rebuilds the player entity) |
| ST12 | At Stealth 150+, hit a mob while crouched, having taken no damage for 5 s | `**BACKSTAB**` and visibly more damage |
| ST13 | Take a hit, then immediately crouch-attack | **No** backstab for ~5 s |
| ST14 | **⚠️ Tuning:** backstab an armoured mob, and separately land a crouched *critical* hit | Record the numbers. The multiplier applies to the whole melee total and compounds with crits — **the most likely thing in Pass 2 to be over-tuned** |
| ST15 | Shoot a mob with a bow while crouched | **No** backstab — projectiles are excluded |
| ST16 | At Stealth 250+, right-click **gunpowder** | Invisible, **no particles**, no firework, ability message + cooldown |
| ST17 | While invisible, check your own armour and held item | Still visible — vanilla behaviour, documented, not a bug |
| ST18 | Right-click gunpowder again during cooldown | "Too tired" message, no second activation |
| ST19 | Right-click a **feather** (Second Wind's item) | Fires Second Wind, **not** Smoke Bomb, and prints no Stealth message |

### 8c. Parkour → Snow Walker

| # | Action | Expect |
|---|---|---|
| SW1 | At Parkour < 100, walk into powder snow | You sink in, as vanilla |
| SW2 | `/addlevels parkour 100`, walk onto powder snow | **You walk on top.** No sinking, no rubber-banding, no freezing |
| SW3 | Sprint and jump across a powder-snow field | Holds up under movement — this is the client/server agreement check |
| SW4 | Max Swimming and Flying but leave Parkour at 0 | Still sink. The gate is Parkour, **not** Agility's average |
| SW5 | `/mcstats parkour` | Snow Walker is listed with its rank |

### 8d. XP bars

| # | Action | Expect |
|---|---|---|
| XB1 | Sprint around | **Both** the Parkour bar and the **Agility** bar appear |
| XB2 | Watch the Agility bar over a few levels | It **fills gradually** — if it sits permanently full, the child-skill progress averaging has regressed |
| XB3 | Swim, then glide | Swimming/Flying bars appear, Agility bar stays and keeps updating |
| XB4 | Train 4+ skills in quick succession (mine while sprinting, then hit a mob) | **At most 3 bars on screen.** The one you trained longest ago disappears |
| XB5 | Keep training the oldest of the three while adding a fourth | The skill you are actively training is **not** the one evicted |
| XB6 | Stop everything for ~10 s | All bars fade |
| XB7 | Repair or smelt something | **No** Salvage/Smelting bar — still suppressed by design |
| XB8 | Set `Experience_Bars.Max_Visible: 0` in `experience.yml`, reload | No limit; all bars stack |

## Session 9 — Unarmored (~30 min)

New in Pass 2, **never played**. Unit-proven and boot-clean only. `/addlevels unarmored 1000` grants
it; the four Iron Skin tiers unlock at **100 / 200 / 500 / 1000** (RetroMode), Thorny Skin at **350**.

**Fight everything in this session with all four armour slots empty**, including the head — a carved
pumpkin or a mob head counts as armour and turns the whole skill off. That is deliberate.

### 9a. XP — and the exploit gates, which are the interesting part

| # | Action | Expect | What would make this a false pass |
|---|---|---|---|
| UA1 | Punch-fight a few zombies bare-handed and take hits | Unarmored XP climbs per hit taken | — |
| UA2 | Equip **only a helmet**, take hits | **Zero** XP. Repeat for chest / legs / boots | A gate written as an `\|\|` chain can lose one arm and still pass a test that only ever equips a helmet |
| UA3 | Wear a **carved pumpkin** and nothing else, take hits | **Zero** XP | Stricter than mcMMO's own "is it armour?" test, on purpose — otherwise the rule is "free armour as long as mcMMO doesn't recognise your hat" |
| UA4 | Stand in a **cactus / fire / berry bush** and take damage | **Zero** XP | The most obvious cheese: no mobs, no risk, no attention |
| UA5 | Take **fall** damage | **Zero** XP (Agility Roll still fires normally) | — |
| UA6 | Blow yourself up with your own **TNT**, and separately with a **Blast Mining** charge | **Zero** XP | A player is a living entity — without the "not yourself" clause, Blast Mining is a repeatable, automatable XP button |
| UA7 | **⚠️ THE BALANCE ROW.** Let *one* zombie hit you ~30 times (slab trap + golden carrots) and watch the XP | It pays for the first **20** hits, then **that mob stops paying entirely** | **This is the whole reason the skill's 92 h budget means anything.** Without the cap this exact setup is a passive ~250 XP/s ⇒ level 1000 in ~12 h. If XP keeps climbing past hit 20, the per-attacker cap has regressed |
| UA8 | Kill that spent zombie, let a fresh one hit you | Pays again — the counter is per mob, not per player | If a fresh mob pays nothing, the cap is keyed on the wrong entity and real fights are being throttled |
| UA9 | **⚠️ Tuning:** record XP/hour from a normal (non-farm) session of fighting bare-handed | Compare against the 92 h-to-1000 budget | The budget is a paper figure. **Measure it** |

### 9b. Iron Skin — the four tiers

| # | Action | Expect |
|---|---|---|
| UA10 | At Unarmored < 100, look at the **armour bar** on the HUD | Empty, as vanilla |
| UA11 | `/addlevels unarmored 100`, look again | **The armour bar fills to leather-set level (~7 points).** The attribute is client-synced, so this is free feedback — if the bar stays empty, the modifier is not reaching the client |
| UA12 | Cross 200 / 500 / 1000 | Bar steps up to ~11 / ~15 / ~20 points. **Steps, not a smooth climb** |
| UA13 | At 1000, take a hit from a mob and compare with the same mob at Unarmored 0 | Visibly less damage |
| UA14 | **Equip a chestplate** | Skin armour **vanishes the same tick**; the bar shows only the chestplate's own points |
| UA15 | Take the chestplate back off | Skin returns immediately |
| UA16 | Toggle armour on and off ~20 times, then check the bar unarmored | Still exactly the tier value — **not** a larger number. Stacked modifiers are the failure mode this whole design exists to prevent |
| UA17 | **Die and respawn**, then check the bar | Skin still applied. A respawn builds a *new* player entity and silently drops every modifier; the per-tick re-derivation should heal it within a tick |
| UA18 | Go to the End, jump in the portal, come back | Same — the End exit uses the same entity-rebuilding path as death |
| UA19 | Quit to title, reload the world, check the bar | Skin applied, and **exactly** the tier value |
| UA20 | Compare a diamond-tier skin against a real diamond set against a heavy hit (creeper / charged creeper) | **The real set takes noticeably less.** Skin grants armour but no *toughness*, which is what blunts big hits — this is the deliberate reason real armour stays worth wearing |
| UA21 | Take damage that ignores armour (void, starvation) | Skin does nothing. Vanilla behaviour, not a bug |

### 9c. Thorny Skin

| # | Action | Expect |
|---|---|---|
| UA22 | At Unarmored < 350, let a zombie punch you | Attacker takes **nothing** back |
| UA23 | `/addlevels unarmored 350`+, let a zombie punch you | Attacker takes **chip damage** (~half a heart at max). No message, no particles — by design |
| UA24 | Let a **skeleton shoot you** from range | **No** reflect. Melee only — the gate is the direct damager, so an arrow is out of reach |
| UA25 | Take **fall / fire / cactus** damage | No reflect, and nothing in the log |
| UA26 | Equip one armour piece, get punched | No reflect |
| UA27 | **⚠️ Tuning:** stand in a mob farm at max Unarmored and do nothing | Mobs should **not** die to reflect alone in any reasonable time. If they do, the cap is too high — a reflect costs nothing, needs no aim and fires on every hit |
| UA28 | Watch the log during all of 9c | **No exceptions.** Thorny deals damage from inside the damage pipeline; a re-entrancy bug would surface as a stack overflow here |

### 9d. Cross-checks

| # | Action | Expect |
|---|---|---|
| UA29 | `/mcstats unarmored` | Both sub-skills listed with ranks and the current tier |
| UA30 | Fight bare-handed | **Unarmed** XP and **Unarmored** XP both move — they are different skills with one letter between them, and a prefix bug would send ranks and config to the wrong one |
| UA31 | Take hits bare-handed while *sneaking* | Unarmored still pays (it is not a movement skill) |
| UA32 | Watch the XP bar while taking hits | An Unarmored bar appears, coloured **red** |

---

## Session 10 — Husbandry: the whole skill (~90 min)

Covers stages 0–2. The harvest verbs (shear, hive, milk, brush) are stages 3–4 and get their own
session. Start with `/addlevels husbandry 0` in a fresh pen of cows, wheat in hand.

### 10a. Breed XP — the per-species table

| # | Action | Expect |
|---|---|---|
| HU1 | Breed two cows | Husbandry XP moves by **350**. Paid **once for the pair**, not once per parent — if you see 700, the once-per-breeding rule broke |
| HU2 | Breed two chickens, then two horses | **300** and **1200**. The spread is the point: the table is priced by what the breeding item costs |
| HU3 | Breed two **foxes**, and two **turtles** | **800** and **700**. ⚠️ **The single most important row in 10a.** Fox and turtle re-implement breeding inline and never call the method the plan originally named — if either pays zero, the seam regressed to `AnimalEntity#breed` |
| HU4 | Breed two **goats**, and two **hoglins** | **400** and **900** |
| HU5 | Breed frogs or sniffers (egg-layers) | Breeding still pays. No twin — vanilla hands out an egg, not a baby |
| HU6 | Let two animals breed **on their own** (villager-style AI, or a command) | **Nothing.** The seam only fires when vanilla resolved a real player as the breeder |

### 10b. Multi-Breed and Twins — the retuned numbers

| # | Action | Expect |
|---|---|---|
| HU7 | At Husbandry 1, feed one cow in a pen of ten | One or two go into love mode, not all ten |
| HU8 | `/addlevels husbandry 1000`, feed one cow in a pen of **twenty** | **At most five breed — the one you fed plus four.** ⚠️ **This is the anti-exploit cap (retuned 8 → 4 on 2026-07-29) and the whole XP budget rests on it.** If the whole pen pairs off, the count cap is not being applied |
| HU9 | Do HU8 with **horses** | Same spread. Horses override `interactMob`, so a regression to that seam leaves them at one |
| HU10 | Watch the log through HU8 | **No StackOverflowError.** The spread calls the very method it hooks; the re-entrancy guard is all that stands between one wheat and an unbounded cascade |
| HU11 | At 1000, breed ~40 pairs and count double births | Roughly **one in four** (ChanceMax 25, retuned from 50). A twin is a surprise, not the norm |
| HU12 | **⚠️ Tuning:** time how long ~20 breeding cycles take, and extrapolate | The design budget is ~51 h of active breeding to 1000. If HU8 + HU11 together make it feel like hours, the caps are still too generous |

### 10c. Raise XP — the 20-minute verb

| # | Action | Expect |
|---|---|---|
| HU13 | Breed two cows, **stay nearby**, wait for the calf to grow up | **A second 350** lands the moment it matures. This is the payout the whole bred-by marker exists for |
| HU14 | Confirm HU13 fired **exactly once** | Not twice, not per tick |
| HU15 | Breed a calf, then fly far enough to **unload the chunk**, come back, wait | Pays when it grows up. The marker rides the calf's own NBT into the region file and back |
| HU16 | Breed a calf, **quit to title and reload the world**, then wait for it to mature | **Pays the full 350.** ⚠️ **The row this whole mechanism was rebuilt for** (D-H6 reversed 2026-07-29 — it used to read "pays nothing, expected"). The marker is a persistent data attachment now; if this pays zero, it regressed to the session-lifetime `MetadataStore` and every calf bred before a save is silently disinherited |
| HU16b | Breed a calf, reload, and check the log during world load | **No `Skipping invalid attachments` warning.** That line means the `mcmmo:bred_by` identifier was not registered before the world was read, and every marker in the save was discarded on the spot |
| HU17 | Find a **wild** baby animal and wait for it to grow up | **Nothing.** You did not breed it |
| HU18 | Spawn-egg a baby, then `/data`-modify or otherwise turn an adult back into a baby | **Nothing**, in both directions |
| HU19 | Breed a **twin** pair and let **both** babies mature | **Both** pay. A twin carries the same marker as its sibling |
| HU20 | Breed a goat and a hoglin, let both mature | **Both pay.** ⚠️ **The row this seam was rebuilt for** — `HoglinEntity#onGrowUp` and `GoatEntity#onGrowUp` never call `super`, so the originally-planned hook would have paid these two exactly zero |

### 10d. Feed and Accelerated Growth

| # | Action | Expect |
|---|---|---|
| HU21 | Feed a baby cow wheat | **50 XP**, and the calf visibly jumps toward adulthood |
| HU22 | Feed a **baby horse**, **camel** and **llama** | 50 each. ⚠️ These three feed through `receiveFood`, not `interactMob` — a seam regression leaves them paying nothing |
| HU23 | Let a **sheep eat grass** next to you and watch the XP bar | **Nothing.** ⚠️ **The AFK-farm row.** Sheep eating grass calls the same growth method a feed does; only the interaction stash separates them |
| HU24 | Feed a **dolphin** a fish | **Nothing** — not in the breeding table, so not in this skill |
| HU25 | At Husbandry < 150, breed a cow and time the calf | Full ~20 minutes |
| HU26 | At 1000, breed a cow and time the calf | Noticeably shorter (~30 % off) — but **still minutes, not instant** |
| HU27 | At 1000, breed 10 calves and check none is born an adult | ⚠️ **A newborn must never arrive already grown.** If one does, the raise verb is paying in the same tick as the breed verb and the whole skill is broken |
| HU28 | At 1000, feed babies repeatedly and watch for the double-feed message | Fires roughly one feed in four |

### 10f. Shear and Bountiful Harvest (stage 3)

| # | Action | Expect |
|---|---|---|
| HU34 | Shear a woolly sheep | **300 XP**, and wool drops as normal |
| HU35 | Shear a **mooshroom**, a **snow golem** and a **bogged** | 300 each. The mooshroom becomes a cow, the golem loses its pumpkin, the bogged drops mushrooms — all three go through the same loot funnel as the sheep |
| HU36 | **⚠️⚠️ THE ROW THIS SEAM EXISTS FOR.** Put shears in a **dispenser** aimed at a sheep and let it fire repeatedly | **ZERO XP, and no bonus drops.** Wool still drops normally. A dispenser reaches the exact same code a player does; the only thing separating them is that it opens no player interaction. If this pays, the AFK wool farm is live |
| HU37 | Shear a **copper golem** (hand it a poppy, shear, repeat) | **Nothing, ever.** ⚠️ It rolls no loot table, so it never reaches the seam. It is also infinitely repeatable — if this ever starts paying, it is a click-for-300 loop, not a balance question |
| HU38 | Shear **leaves**, **vines** and a **pumpkin stem** | **Nothing.** Block shearing is Woodcutting/Herbalism's, and it is spammable (D-H3) |
| HU39 | At Husbandry 1000, shear ~20 sheep and count double yields | Roughly **half** drop twice (ChanceMax 50). The bonus is the animal's own loot rolled again, so a black sheep's bonus is black wool |
| HU40 | At 1000, watch a single shear that drops several wool | **All of them double, or none do.** The roll is once per shear, not per item |
| HU41 | At 1000, shear repeatedly and watch the shears' durability | Roughly one shear in four costs nothing (DurabilitySaveChanceMax 25) |
| HU42 | Let the **dispenser** from HU36 run at Husbandry 1000 | Its shears wear **normally** — no durability saving. An automated farm that never wears out is the same exploit by another route |
| HU43 | **⚠️ Tuning:** build a snow golem, shear it, and count the pumpkin back | You get the carved pumpkin back, so a snow farm makes this a ~4-action 300 XP loop. **This is the shear to judge the rate on, not the sheep.** Note the timing; do not file it as a bug |

### 10g. Hive, milk and brush + Beekeeper (stage 4)

| # | Action | Expect |
|---|---|---|
| HU44 | Harvest a full hive with **shears** | **500 XP** and 3 honeycomb |
| HU45 | Harvest a full hive with a **glass bottle** | **500 XP** and a honey bottle. Both halves of the verb pay the same |
| HU46 | **⚠️⚠️ THE ROW THE HIVE SEAM WAS REBUILT FOR.** Put a **lit campfire under a hive**, then harvest it | **500 XP, and the bees stay calm.** The plan's gate would have paid **ZERO** here — the campfire branch calls the "automated" `takeHoney` overload, so gating on the player overload would have paid nothing for every bee farm anyone actually builds, and full XP only for the careless harvests that get you stung |
| HU47 | Put **shears in a dispenser** aimed at a hive, and a **glass bottle in another**, and let both fire | **ZERO XP from both.** ⚠️ Vanilla really does ship both behaviours (`ShearsDispenserBehavior` and `DispenserBehavior$3`) — this is the hive's AFK farm |
| HU48 | Milk a cow | **200 XP** |
| HU49 | Bowl a **mooshroom** for stew, then bucket the same mooshroom for milk | **One award total** — same verb, same per-animal cooldown. Not two |
| HU50 | **⚠️ D-H5.** Milk the **same cow 20× in a row** | **One award**, then nothing for 5 minutes. Vanilla puts no cooldown on this at all; without mcMMO's, it is the fastest XP in the mod |
| HU51 | Milk **10 different cows** in a row | **10 awards.** The cooldown is per animal, not per player — keeping a herd is the intended way to earn this |
| HU52 | Milk a cow, quit to title, reopen, milk it again | Pays again. ⚠️ **Expected, not a bug**: the cooldown is transient by design — 5 minutes is short enough that losing it costs one early payout, unlike the bred-by marker's 20 |
| HU53 | Brush an armadillo | **300 XP** and a scute |
| HU54 | **⚠️ The row the plan got wrong.** Brush the **same armadillo** repeatedly | Scute drops **every time** (vanilla has no brush cooldown — `brush/armadillo.json` has no conditions and `brushScute` never touches the shed timer), but XP pays **once per 5 min**. The drop must not be withheld; only the reward |
| HU55 | Put a **brush in a dispenser** aimed at an armadillo | **ZERO XP.** ⚠️ Vanilla ships this too (`DispenserBehavior$5`) and the plan did not mention it |
| HU56 | Brush an armadillo at Husbandry 1000, ~20× across different animals | Roughly half double (ChanceMax 50), and roughly one brush in four costs no durability — **worth a quarter of the brush each time**, since vanilla charges 16 of its 64 |
| HU57 | Reach Husbandry 100 and harvest a hive with **no campfire** | **Bees stay calm.** ⚠️ Check **both** ways they anger: no bee targets you, *and* the hive's own occupants do not come out. The plan's version would have left the second firing |
| HU58 | At 1000, harvest ~20 full hives and count the yield | Visibly more than 3 comb on many of them — Beekeeper's bonus **stacks** with Bountiful Harvest, so up to 3× |
| HU59 | Shear a sheep, then a hive, then milk, then brush, all at Husbandry 1000 | All four pay, all four can double. One reward path, four verbs |

### 10h. Selective Breeding, Brood and Hidden Bounty (stage 5)

| # | Action | Expect |
|---|---|---|
| HU60 | At Husbandry 250+, breed two **mediocre horses** ~10× and record each foal's speed/jump/health | Foals trend **above** the parents' midpoint. ⚠️ Compare against a **pre-250 baseline** — this is a distribution shift, not a visible proc, so it cannot be judged from one foal |
| HU61 | Breed horses across **several generations** at 1000 | Stock walks toward the species maximum but **does not arrive** in one or two generations. ⚠️ **The tuning row**: the effect compounds, so if generation 3 is already perfect, 0.25 is too high |
| HU62 | Breed a horse whose stat is already at the **species maximum** | It stays there — never overshoots |
| HU63 | Throw a **stack of eggs** at Husbandry 200+ | Visibly more than 1-in-8 hatch. Occasionally **four chicks** at once |
| HU64 | **⚠️ The AFK row.** Let a chicken coop with a hopper run, collect eggs, throw them | **NO XP for laying, collecting, throwing or hatching.** Brood is yield-only |
| HU65 | Let the chicks from HU63 **grow up** | **They pay nobody.** ⚠️ A hatched chick carries no bred-by marker — if it did, an AFK egg farm would become a raise-XP farm 20 minutes later |
| HU66 | Put **eggs in a dispenser** and fire it | No Brood at all — a dispensed egg has no player owner |
| HU67 | At Husbandry 300+, work a herd through all four harvest verbs for a while | Occasional `**HIDDEN BOUNTY**` message plus an extra item. String/leather/scute are common; a **name tag** is rare and needs level 500 |
| HU68 | Check which treasure comes off which verb | Verb-specific: honey block only from hives, string only from shears, leather only from milk, scute only from brushing. **Keyed on the verb, never the species** |

### 10i. Herdsman's Call (stage 6)

| # | Action | Expect |
|---|---|---|
| HU69 | At Husbandry 100+, right-click a **goat horn** | `**HERDSMAN'S CALL**`, the activation sound, **and the horn still sounds normally** — mcMMO only observes the click |
| HU70 | Sound the call near a penned herd and walk away | Animals **path toward you** without you holding food. ⚠️ **They must not clip through fences or water** — this is vanilla navigation, not a teleport or a velocity shove |
| HU71 | Sound the call with animals behind a **closed gate** | They walk up to the gate and stop. If they come through, the ability is doing something it should not |
| HU72 | Sound the call, then milk the **same cow** 5× in a row | **All five pay.** The cooldown bypass is one of the three effects |
| HU73 | ⚠️ After HU72 ends, milk that cow again immediately | **Nothing** — the bypass must not have stamped the animal's clock, or the ability would be worth twice what it looks like |
| HU74 | Sound the call, then shear/brush/harvest a hive | **Every harvest doubles**, with no roll — even at Bountiful Harvest rank 1 |
| HU75 | Sound the call twice in a row | Second press prints `Skills.TooTired` with the remaining cooldown. A refusal must **not** burn the cooldown |
| HU76 | `/mcability` off, then press the horn | Nothing, silently |
| HU77 | ⚠️ **Perf:** stand in a 100-animal pen with the ability **idle** for a minute, watching tick time | No measurable cost. The radius reads 0 while idle, so no entity sweep runs at all |
| HU78 | Sound the call in that same pen | Tick time stays sane. The sweep is capped at radius 40 in code |

### 10e. Cross-checks

| # | Action | Expect |
|---|---|---|
| HU29 | `/mcstats husbandry` | **All nine** sub-skills listed with ranks and per-effect stat lines, and no `!Husbandry.…!` literals. (Fixed since stage 3: `HusbandryStatsRenderer` now exists — the earlier fall-through to `GenericSkillStatsRenderer` is gone) |
| HU79 | Check the three super-ability trigger items are distinct | Feather = Second Wind, gunpowder = Smoke Bomb, **goat horn = Herdsman's Call**. All three listen on one event; a collision activates one and prints another's refusal |
| HU30 | Breed a **tamed wolf** | Pays **Husbandry**, not Taming. The boundary is the verb, never the species |
| HU31 | Feed a wolf raw meat to **heal** it | Pays **Taming** (Fast Food Service), not Husbandry |
| HU32 | Watch the XP bar while breeding | A Husbandry bar appears, coloured **yellow** |
| HU33 | Watch the log through the whole session | No exceptions, no mixin warnings |

### 10f. The wiring-audit fixes (2026-07-30)

Three gaps the 2026-07-30 wiring audit found *after* the skill was already called code-complete. All
three were **silent** — nothing crashed, nothing logged, and all 1148 unit tests passed over the top of
them. Each is now pinned by a mutation-checked test, but these rows are the live proof.

| # | Action | Expect |
|---|---|---|
| HU80 | **⚠️⚠️ THE ROW THE MILK VERB WAS REBUILT FOR.** Milk a **goat** with a bucket | **200 XP**, exactly like HU48's cow. Until this fix `CowMilkMixin` targeted `AbstractCowEntity` only, and a goat re-implements the bucket branch inline in its own `interactMob` — so **every goat ever milked paid ZERO**, while that same goat went on paying for breeding, raising and feeding |
| HU81 | Milk the **same goat** 20× in a row | **One award**, then nothing for 5 minutes — the D-H5 cooldown reaches goats for free, because it lives inside `onMilked` rather than at the call site |
| HU82 | At Husbandry 1000, milk ~20 different goats | Bonus milk buckets on roughly half, plus the occasional Hidden Bounty leather. Goats must reach **every** part of the verb, not just the XP |
| HU83 | Breed two **nautiluses** (tame both, then feed them at full health) | **1200 XP**, the mount rate. ⚠️ Nautilus is new in 1.21.11 and breeds through `NautilusBrain`'s `BreedTask`, which does fire the criterion mcMMO hooks — so the seam *was* wired and the species price was the missing half |
| HU84 | Raise that nautilus calf to adulthood | **1200 XP.** The raise verb is a multiple of the breed value, so a missing breed price silently killed **both** halves of the lifecycle |
| HU85 | Breed two **happy ghasts** with snow blocks, then raise the ghastling | **1200 XP each.** Shipped back in 1.21.6 and simply missed when the roster was written — note that `experience.yml`'s combat table already knew this mob |
| HU86 | Open the ModMenu config editor → Abilities | A **Herdsman's Call Cooldown (sec)** slider sits alongside the other ten supers, and moving it really does change the ability's cooldown in game |

---

## Reporting

For each item, one line: `ID | PASS/FAIL/PARTIAL | what you actually saw`. Anything FAIL or PARTIAL —
grab the surrounding `run/logs/latest.log` lines too. Session 1 and 6 failures are blockers; session 4
and 7 are judgement calls, not bugs.
