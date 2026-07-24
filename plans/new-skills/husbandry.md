# Husbandry (Breeding + Shearing) — Plan

**Read [00-OVERVIEW.md](00-OVERVIEW.md) first.** Husbandry is a **standalone primary skill** (D1 —
the wiki calls it a child of Herbalism+Taming, but it has its own XP sources, so it must be standalone).
It is **event-driven** — it does **not** need F1 (the tick sampler); it needs new **breed and shear
mixins**, because Fabric exposes no callback for either. It can be built in parallel with the movement
skills.

Wiki source: `raw_site_text.md` §"Shearing" and §"Husbandry" (Suggested Child Skills). The user
explicitly wants **Shearing folded into Husbandry** rather than shipped as its own skill.

## Concept

The animal-tending skill. Two XP sources, three sub-skill areas:
1. **Breeding** — leveled by breeding animals; rewards multi-breeding and twin offspring.
2. **Shearing** — leveled by shearing (sheep wool, mooshroom, snow golem, and shears-on-leaves);
   rewards bonus drops, shear-durability savings, and hidden treasures.

Category: `GATHERING_SKILLS` (it's fundamentally a resource-gathering skill via shears + breeding),
or `MISC_SKILLS` if you'd rather not have it counted among the ore/log/crop gatherers — **decide during
design (D-H1 below).**

## Sub-skills

| Sub-skill | Type | Mechanic | Scaling (proposed) | Unlock | Risk |
|---|---|---|---|---|---|
| **Multi-Breed** | active/interaction | Right-click an animal with its breeding item → also sets nearby same-type animals in love from the **one** item | Radius grows with level, cap ~**40 blocks** (wiki). Consumes only the one item. | e.g. 1 | Med |
| **Twins** | passive | Chance a successful breed yields **two** babies | +1%/10 levels, cap **100%** at 1000 (wiki). Consider capping lower — 100% double-breed is a food/mob-farm firehose. | 1 | Med (needs breed hook) |
| **Shear Mastery** | passive/gathering | Bonus drops when shearing (extra wool/leaves); reduced shear durability cost | Bonus-drop chance + a durability-save chance, both scaling with level. | 1 | Med (shear hook) |
| **Mob Whisperer** *(Shearing "hidden treasures")* | passive | Rare treasure roll on shear/shear-break (wiki: egg/spider-spawn from a spiderweb, etc.) | Low-chance treasure table scaling with level. Reuse the `TreasureConfig`/`ItemSpecBuilder` pattern (`[[phase-3-woodcutting-excavation-drops]]`). | some | Med |

## Design decisions specific to Husbandry

- **D-H1 — category:** GATHERING vs MISC. Recommend **GATHERING** (shearing is gathering). Affects only
  the `/mcstats` grouping and the `SkillTools` category list.
- **D-H2 — Twins cap:** wiki says 100% at 1000. Doubling *every* breed at max is a balance problem
  (infinite meat/wool/mob-farm fuel). Recommend capping the chance (e.g. 50%) and making it config. Flag.
- **D-H3 — what counts as "shearing" for XP:** sheep (wool), mooshroom→mushrooms, snow golem (pumpkin),
  and **shears-on-leaves/vines/beehive/pumpkin** block breaks. Enumerate the shearable set explicitly;
  don't hand-wave "shears = XP" or you'll pay XP for shearing a pumpkin stem 400×.
- **D-H4 — anti-exploit:** breeding is already exploitable (auto-farms). Respect the existing
  `experience.yml → ExploitFix.COTWBreeding` philosophy; consider an XP throttle per animal type, and do
  **not** pay Twins XP for the *baby* being bred later in a self-sustaining loop. Look at how the port
  handles Taming COTW breeding exploit before finalizing.

## MC-free core (`HusbandryManager extends SkillManager`)

- `boolean rollTwins()` — RNG gate for a double birth (pinned RNG for tests).
- `int getMultiBreedRadius()` — `min(40, base + level * perLevel)`.
- `float getBreedXp(...)` / `float getShearXp(...)` — per-action XP.
- `boolean rollBonusShearDrop()` / `boolean rollShearDurabilitySave()` / `Optional<ItemSpec>
  rollShearTreasure()` — the shear rewards, all deterministic given injected RNG.
- Standard `canX()` gates (`RankUtils.hasUnlockedSubskill` + `Permissions.isSubSkillEnabled`).

Keep it MC-free: the manager decides *whether* and *how much*; the listener/mixin does the entity/item
work.

## MC-typed trigger layer — the hard part

**There is no Fabric breed event and no Fabric shear event.** Both need mixins. Verify every signature
with `scripts/javap-mc.sh` (`[[javap-mc-script]]`) — do not trust remembered method names.

1. **Breed hook (XP + Twins):** mixin on `AnimalEntity#breed(ServerWorld, AnimalEntity)` (the method
   that spawns the child and resets love). Inject after the child is created to:
   - award breeding XP to the breeding player (resolve via `getLovingPlayer()` — an `AnimalEntity` stores
     the player who bred it),
   - on `rollTwins()`, spawn a **second** child (`createChild` again, place it at the parent),
   - respect the anti-exploit gate (D-H4).
   ⚠️ `breed` runs for the *pair*; make sure XP/Twins fires **once** per breeding, not once per parent.
2. **Multi-Breed:** a `UseEntityCallback` (right-click entity). If the held item is the animal's breeding
   item and Multi-Breed is unlocked: set the target and every same-type breedable `AnimalEntity` within
   `getMultiBreedRadius()` into love mode, consuming only one item. Return a result that prevents vanilla
   from *also* consuming per-animal.
3. **Shear (sheep / mooshroom / snow golem):** mixin on the shear path. Sheep implements `Shearable`;
   the shears use routes through `ShearsItem#useOnEntity` → `Shearable#sheared(...)` (verify). Inject to:
   - award shear XP,
   - on `rollBonusShearDrop()` drop extra wool/items,
   - on `rollShearTreasure()` drop treasure,
   - on `rollShearDurabilitySave()` refund the durability point (don't apply the damage vanilla was about
     to apply — an `@ModifyArg`/cancel on the `setDamage`/`damage` call).
4. **Shears-on-leaves (block path):** the existing `BlockBreakListener` already fires on block break.
   When the tool is `ShearsItem` and the block is in the shearable-block set (D-H3), route to the same
   Husbandry shear rewards. **Don't** double-count: this is block-break shearing, distinct from the
   entity shear in (3).

## Registration specifics

- `PrimarySkillType.HUSBANDRY`; category per D-H1.
- `SubSkillType`: `HUSBANDRY_MULTI_BREED`, `HUSBANDRY_TWINS`, `HUSBANDRY_SHEAR_MASTERY`,
  `HUSBANDRY_MOB_WHISPERER` (treasure). Ranks per table.
- No super ability (Multi-Breed is an interaction, not a cooldowned super) → **skip `SuperAbilityType`.**
- `experience.yml`: `Husbandry` XP modifier + bar color; consider a breeding-XP exploit toggle.
- `advanced.yml`: Twins chance/cap, Multi-Breed radius/increment, shear bonus/durability/treasure chances.
- A **per-skill treasure config** (like `fishing_treasures.yml`) for shear treasures, loaded through the
  existing `TreasureConfig`/`ItemSpec` machinery, **or** inline a small table in `advanced.yml` if it's
  just a handful of entries. Prefer reusing `TreasureConfig` if the table grows.
- Locale block `Husbandry.*`.

## Testing

- **Unit (MC-free):** `rollTwins` pinned-RNG (0→always double, 100→never); `getMultiBreedRadius` clamp at
  40; `rollBonusShearDrop`/`rollShearDurabilitySave`/`rollShearTreasure` gates; XP values.
- **Mixin tests:** structural assertion that the breed/shear injectors applied (mirror the
  `MixinApplicationTest` approach, and heed `[[mixin-slice-allow-guard]]` — assert a real marker, not
  `Class.forName`).
- **§G rows:** breed cows 10× → Husbandry XP; observe a Twins double-birth at high rank; shear sheep →
  XP + occasional bonus wool + shears lasting longer; shear a spiderweb/leaves → occasional treasure;
  Multi-Breed one wheat sets a whole pen in love.
- **Regression:** old profile loads with Husbandry defaulted.

## Cuts / deferrals

- If the breed mixin proves flaky (loving-player resolution, twin placement inside blocks), ship
  **Shearing first** (self-contained via the shear + block paths) and defer Breeding to a follow-up —
  Shearing is what the user actually asked to fold in.
- "Hidden treasure from wool" (an egg/spawn-egg from shearing a sheep) is the wiki's flavor; keep the
  treasure table small and sane, not a spawn-egg fountain.
