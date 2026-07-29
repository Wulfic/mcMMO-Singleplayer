# Husbandry — Plan

**Read [00-OVERVIEW.md](00-OVERVIEW.md) first.** Husbandry is a **standalone primary skill** (D1).
It is **event-driven** — it does **not** need F1 (the tick sampler). It needs new **breed, grow-up,
shear, brush, honey and milk hooks**, because Fabric exposes a callback for none of them. It can be
built in parallel with the movement skills.

Wiki source: `raw_site_text.md` §"Shearing" and §"Husbandry" (Suggested Child Skills). The user
explicitly wants **Shearing folded into Husbandry** rather than shipped as its own skill.

> **⚠️ REVISED 2026-07-28 — the v1 draft was too narrow and two of its seams were wrong.**
> The original plan had **two** XP verbs (breed, shear), both hard rate-capped by vanilla, one of them
> (shear) trivially dispenser-automatable, and it left the skill dead in a world with no sheep. This
> revision widens it to **six verbs covering the whole livestock lifecycle**, states the
> Husbandry↔Taming boundary as a rule rather than a vibe, and replaces two seams that bytecode says
> do not exist. User rulings taken this pass are marked ✅.

---

## The Husbandry ↔ Taming boundary — read this before adding anything

**Species is not an available boundary.** `experience.yml:759-781` (`Taming.Animal_Taming`) already
claims **Bee, Goat, Fox, Panda, Frog, Axolotl, Sniffer, Camel, Llama, Horse, Donkey, Mule, Parrot,
Wolf, Cat** — i.e. every animal in the game. (Most of that table is dead — you cannot tame a bee or a
goat, so `EntityTameEvent` never fires for them — but the claim is on paper and someone will read it.)
Any "Husbandry gets these mobs, Taming gets those" split is a lookup table that will rot.

Draw the line on the **verb**, and on **when in the animal's life it fires**:

| | **Taming** | **Husbandry** |
|---|---|---|
| Moment | the **one-time conversion** of a wild animal into yours | the **repeating lifecycle** of an animal you already keep |
| Cardinality | pays **once per animal, ever** | pays **repeatedly**, per act of care or harvest |
| Payload | combat-pet buffs — Gore, Sharpened Claws, Thick Fur, Shock Proof, Holy Hound, Pummel, Fast Food Service, Beast Lore, Call of the Wild | yield, fertility, growth rate, product quality, herd handling |
| Must never touch | breeding, raising, harvesting | pet combat, pet damage/health **in a fight**, summoning |

**The one-liner: Taming pays once, for making the animal yours. Husbandry pays repeatedly, for what
you do with it afterwards.** A tamed wolf pays Taming once at taming and Husbandry every litter. No
species table, no exceptions, and any new sub-skill that fails this test does not ship.

Corollaries worth writing down because they will come up:
- **Breeding a wolf/cat/horse pays Husbandry, at the full rate.** The verb owns it, not the species.
- **Feeding a wolf to heal it is Taming** (it is already `TAMING_FAST_FOOD_SERVICE`), even though
  "feeding an animal" is otherwise a Husbandry verb. Healing in combat is the discriminator.
- **Wolf armour, horse armour in a fight, llama caravans as a defence** — Taming. Saddling and naming
  livestock — Husbandry (D-H8, low priority).

---

## Concept

The **livestock lifecycle** skill: breed → raise → maintain → harvest. Six XP verbs, deliberately
spread across different animals, biomes and tools so no single missing resource kills the skill.

Category: **`GATHERING_SKILLS`** — ✅ D-H1 settled below.

## XP sources — the six verbs

RetroMode values. **These are starting points pending §G measurement**, exactly like every other Pass-2
number. The curve is `10N² + 1010N` ⇒ **11,010,000 XP to level 1000** (`experience.yml:145-147`).

| # | Verb | XP (start) | What limits the rate | Farm risk |
|---|---|---|---|---|
| 1 | **Breed** a pair | per-species table, 300–1500 | vanilla 5-min love cooldown per animal; food supply | **Low** — dispensers cannot feed animals, breeding is inherently manual |
| 2 | **Raise** — a baby you bred reaches adulthood | = that species' breed value | **20 real minutes of vanilla time, per animal** | **Lowest in the design.** Cannot be rushed except by feeding, which costs food |
| 3 | **Feed a baby** to accelerate growth | 50 flat | number of live babies × food | Low |
| 4 | **Shear** (sheep / mooshroom / snow golem / bogged) | 300 | grass regrowth — a just-sheared sheep is worthless until it eats | **⚠️ HIGH if hooked wrong** — see the dispenser trap below |
| 5 | **Harvest a hive** (honey bottle / honeycomb) | 500 | 5 honey levels of bee-pollination time; needs flowers | **⚠️ Medium** — dispensers can harvest hives |
| 6 | **Milk / bucket** (cow, mooshroom stew, bucket-mobs) | 200 | **nothing in vanilla** — see D-H5 | **⚠️ HIGH without a cooldown** |
| 6b | **Brush an armadillo** | 300 | vanilla's own scute cooldown | Low |

> Verbs 4–6b are the "harvest" family and share one reward path (`Bountiful Harvest`,
> `Hidden Bounty`) — do not write four copies of the drop logic.

**Budget sanity check, so nobody eyeballs this later.** Pure cow farming = 300 (breed) + 300 (raise) =
600 per pair-cycle, and the raise half accrues in the background. `11,010,000 / 600 ≈ 18,350` cycles;
at ~10 s per breed with a stocked pen that is **~51 h of active clicking**. Against Agility's 89–170 h
per medium that is fast, but Husbandry is active input the whole way, not passive travel. Adopt as the
starting point; **measure in §G before tuning.**

### Explicitly NOT XP sources

| Rejected | Why |
|---|---|
| **Eggs being laid / picked up** | ⚠️ **Bytecode-verified: `ChickenEntity.eggLayTime` is a public field ticked in `tickMovement`.** Egg production is a pure passive timer — a chicken farm with a hopper is 100 % AFK income. Pay for **hatching a thrown egg** (verb via `Brood`) instead: that needs a player to throw it. |
| **Any product collected by a dispenser or hopper** | Same reason. Every harvest verb gates on a real player — see the seam notes. |
| **Butchering / slaughtering a bred adult** | ✅ **CUT (user ruling, 2026-07-28).** It is thematically the payoff of the whole skill, but it collides three ways — Hunter's per-mob kill counters ([hunter.md](hunter.md)), Swords/Axes combat XP, and every mob farm in existence. Left entirely to Hunter. **Do not re-open without re-opening `hunter.md` D-HU2 at the same time.** |
| **Villager breeding / curing zombie villagers** | Different domain (trading), and village mechanics make it an XP firehose. |

## Sub-skills

RetroMode unlock levels. Nine sub-skills is large but they fall into four clean families — breeding,
raising, harvesting, and the super.

| Sub-skill | Family | Type | Mechanic | Scaling (proposed) | Unlock |
|---|---|---|---|---|---|
| **Multi-Breed** | breed | interaction | Right-click an animal with its breeding item → also sets nearby same-type animals in love from the **one** item | Radius grows with level, cap **40 blocks** (wiki). Consumes one item total | 1 |
| **Twins** | breed | passive | Chance a successful breed yields **two** babies | +1 %/10 levels, **capped per D-H2** | 1 |
| **Selective Breeding** | breed | passive | Offspring inherit **biased-upward** parent stats, and a raised chance at rare variants | Horses/donkeys already roll speed/jump/health from the parents — bias the roll toward the better parent. Variant chance (brown mooshroom, rare cat/wolf/rabbit/frog/cow variants) scales with level | 250 |
| **Accelerated Growth** | raise | passive | Babies in your care grow faster; a chance a feed counts double | Growth-rate multiplier scaling with level | 150 |
| **Brood** | raise | passive | Thrown eggs hatch above vanilla's 1/8; chance of multiple chicks | 12.5 % → cap per D-H2's philosophy | 200 |
| **Bountiful Harvest** | harvest | passive | **One** bonus-drop + tool-durability-save path shared by shear, brush and hive harvest | Bonus-drop chance + durability-save chance, both scaling | 1 |
| **Hidden Bounty** *(was "Mob Whisperer")* | harvest | passive | Rare treasure roll on any harvest verb | Low-chance treasure table. Reuse `TreasureConfig`/`ItemSpecBuilder` ([[phase-3-woodcutting-excavation-drops]]) | 300 |
| **Beekeeper** | harvest | passive | Bees do not anger when you harvest without a campfire; +honey yield | Binary unlock + a yield roll | 100 |
| **Herdsman's Call** | **SUPER** | active | For N seconds: nearby animals follow you without held food, **and** every harvest verb ignores its cooldown and double-yields | Duration scales like every other super | 100 |

> **"Mob Whisperer" was renamed to "Hidden Bounty" on purpose** — the old name reads as a Taming
> ability and would have invited exactly the boundary creep this revision exists to prevent.

## Design decisions

- ✅ **D-H1 — category: `GATHERING_SKILLS`.** Four of the six verbs are gathering (shear, brush, honey,
  milk). Affects only the `/mcstats` grouping and the `SkillTools` category list
  (`SkillTools.java:96-110`).
- ✅ **D-H2 — Twins cap: 25 %** (user ruling, 2026-07-29). Wiki says 100 % at level 1000. Doubling
  *every* breed at max is a food and mob-farm firehose, and it compounds with Multi-Breed rather than
  adding to it. Stage 1 shipped 50 %; the ruling took it to **25 %**, so a twin birth stays a pleasant
  surprise at max rank instead of the expected outcome. Same reasoning applies to `Brood`'s
  multi-chick roll (stage 5). Config-driven in `advanced.yml`. Still a §G tuning row.
- ✅ **D-H2b — Multi-Breed spread cap: 4 extra animals** (user ruling, 2026-07-29). The plan
  originally bounded Multi-Breed only by *radius*, which is not a bound at all: Husbandry pays per
  breeding, so the cap on the *count* is what bounds the XP one click can be worth. Stage 1 shipped
  8; the ruling took it to **4**. At eight, one click was worth nine breedings and Multi-Breed became
  the only sensible way to level the skill rather than a convenience on top of it.
  ⚠️ **A changed shipped default never reaches an existing on-disk `advanced.yml`** —
  `copyMissingDefaults` back-fills only *absent* keys, so a config generated before this ruling keeps
  8/50 forever. The dev `run/config/mcmmo/advanced.yml` was patched by hand.
- **D-H3 — what counts as "shearing".** Enumerate the set explicitly; do not hand-wave "shears = XP"
  or you pay for shearing a pumpkin stem 400×. **Entity shear:** sheep (wool), mooshroom (mushrooms,
  destructive), snow golem (pumpkin), bogged (mushrooms). **Block shear:** leaves/vines/beehive-adjacent
  blocks pay **nothing** — that is Woodcutting/Herbalism's territory and it is spammable. This is a
  change from the v1 draft, which routed shears-on-leaves into Husbandry.
- **D-H4 — anti-exploit, generally.** Respect the existing `experience.yml → ExploitFix` philosophy.
  Every harvest verb requires a **real player** (see the seam notes — this is not automatic).
  Additionally: a baby bred by Twins must not itself pay Twins XP in a self-sustaining loop, and the
  raise verb (2) must pay only once per animal.
- **D-H5 — milking has no vanilla cooldown, and that is the whole problem.** ⚠️ Right-clicking a cow
  with a bucket is free and infinitely repeatable on the *same* cow. Unmitigated it would be the fastest
  XP in the mod. **Recommend an mcMMO-side per-entity award cooldown** (one award per animal per
  5 min), which is the same `MetadataStore` mechanism Unarmored's per-attacker cap and Agility's Dodge
  cap already use — see [[unarmored-skill-build]]. Config key
  `ExploitFix.Husbandry.Milk_Cooldown_Seconds`.
- **D-H6 — how does the "raise" verb know who bred the baby?** Verb 2 pays the player who bred the
  animal, 20 minutes later. That needs a **`bred by <uuid>` marker on the child**, written in the breed
  mixin. `MetadataStore` is in-memory, so a server restart loses it. **Recommend accepting the loss**
  (worst case: animals bred before a restart pay nothing when they mature — invisible, never wrong in
  the other direction) rather than adding a persistence shape. If it turns out to matter, the
  `PlacedBlockStore` pattern ([[placed-block-persistence]]) is the template — **but note its lesson:
  never size an allocation from a number read off disk.**
- ✅ **D-H7 — Herdsman's Call is a real `SuperAbilityType`** (user ruling, 2026-07-28). The v1 draft
  skipped a super and left Husbandry the odd skill out. It flows through `buildSuperAbilityMaps()`
  automatically via `subSkillTypeDefinition` — see the overview checklist item 3/8.
- **D-H8 — naming/equipping livestock** (name tag, saddle, horse armour, llama carpet, lead): one-shot
  per animal, tiny XP, pure flavour. **Deferred to v1.1**, listed so it is not re-litigated.

---

## MC-typed trigger layer — the seams

**Every signature below was verified with `scripts/javap-mc.sh` against the 1.21.11 merged jar on
2026-07-28** ([[javap-mc-script]]). Two of the v1 draft's seams did not survive that check.

### 1. Breed — XP, Twins, Selective Breeding

✅ `AnimalEntity#breed(ServerWorld, AnimalEntity)` **and** the 3-arg overload
`breed(ServerWorld, AnimalEntity, PassiveEntity)`. **Target the 3-arg one — it already holds the
child**, so Twins and Selective Breeding do not have to re-derive it.
✅ `AnimalEntity#getLovingPlayer()` returns `ServerPlayerEntity` directly (it is a
`LazyEntityReference<ServerPlayerEntity>` internally) — resolving the breeder is free.

- Award breeding XP **once per breeding, not once per parent** — `breed` runs for the pair.
- On `rollTwins()`, `createChild` again and place it at the parent.
- On `rollSelectiveBreeding()`, bias the child's attribute roll / variant.
- Write the **D-H6 bred-by marker** onto the child here.

### 2. Raise — "the baby grew up"

✅ `PassiveEntity#onGrowUp()` — `protected void`, an ideal mixin target.

> ⚠️ **BYTECODE TRAP, and it is a nasty one.** `onGrowUp()` is **not** a "became an adult" callback.
> `PassiveEntity#setBreedingAge(int)` calls it on **both** transitions — the branch logic disassembles
> to *"run the body when `(prev < 0 && age >= 0)` **or** `(prev >= 0 && age < 0)`"*. So it also fires
> when an **adult becomes a baby** (spawn egg, `setBaby(true)`).
>
> ⚠️ **Worse: `readCustomData` routes through `setBreedingAge`.** A baby animal loading from disk goes
> `prev = 0` (field default) → `age = -1200`, which satisfies the second branch — **`onGrowUp()` fires
> on every chunk load of every baby animal in the world.**
>
> **Two independent gates, both required:** (a) `!isBaby()` / `age >= 0`, and (b) the D-H6 bred-by
> marker must be present. Either one alone closes the chunk-load case; both together also close the
> spawn-egg case. **Mutation-test this** — deleting either gate must fail a test, per
> [[mixin-slice-allow-guard]]'s lesson about guards that silently do nothing.

### 3. Shear — ⚠️ the v1 seam does not exist

> ⚠️ **The v1 draft said "`ShearsItem#useOnEntity` → `Shearable#sheared(...)`". Both halves are wrong.**
> **`ShearsItem` declares no `useOnEntity` at all** — its only methods are `createToolComponent`,
> `postMine` and `useOnBlock`. And `ItemStack#useOnEntity` dispatches to
> `EquippableComponent.equipOnInteract` then `Item#useOnEntity`, neither of which shears anything.

✅ **The player shear lives entirely inside `SheepEntity#interactMob(PlayerEntity, Hand)`.** Verified
call sequence:

```
getStackInHand → ItemStack.isOf(SHEARS) → server-world check → isShearable()
  → sheared(ServerWorld, SoundCategory, ItemStack)
  → emitGameEvent(...)
  → ItemStack.damage(1, player, hand.getEquipmentSlot())
```

- Hook `interactMob` on each implementor: `SheepEntity`, `MooshroomEntity`, `SnowGolemEntity`,
  `BoggedEntity`. The player is a parameter — no resolution needed.
- **Bountiful Harvest's durability save is an `@ModifyArg` on that `ItemStack.damage` call** inside
  `interactMob`. Clean, local, no cancel needed.

> ⚠️⚠️ **DO NOT HOOK `Shearable#sheared`.** Dispensers call `sheared` too — that is the classic AFK
> wool farm, and hooking the interface would ship it as an XP source on day one. This is the single
> most important line in this file. The same rule holds for the hive and brush verbs below: **hook the
> player interaction path, never the shared effect method.**

### 4. Hive harvest — honey bottle / honeycomb

✅ `BeehiveBlock#onUseWithItem(ItemStack, BlockState, World, BlockPos, PlayerEntity, Hand, BlockHitResult)`
— the player path, with the player in hand.
✅ `BeehiveBlock.dropHoneycomb(ServerWorld, ItemStack, BlockState, BlockEntity, Entity, BlockPos)` —
**static, and it takes the harvesting `Entity`.** Good for the yield roll.
✅ `BeehiveBlock#takeHoney` has **two overloads** — one taking `(World, BlockState, BlockPos, PlayerEntity,
BeeState)` and one taking `(World, BlockState, BlockPos)`. **Use the player overload as the gate**; the
3-arg one is the automated path.
- `Beekeeper` suppresses the `angerNearbyBees(World, BlockPos)` call.

### 5. Milk / bucket

✅ `AbstractCowEntity#interactMob(PlayerEntity, Hand)` — cows and mooshrooms both inherit it;
`MooshroomEntity` overrides it for the stew path. Bucket-mobs (cod/salmon/axolotl/tadpole) go through
their own `interactMob`. Gate on the D-H5 per-entity cooldown.

### 6. Brush an armadillo

✅ `ArmadilloEntity#brushScute(Entity, ItemStack)` — **public, returns `boolean`, and hands you the
brushing entity.** Shearing's exact sibling; near-free once the harvest reward path exists. Check the
`Entity` is a player (the same dispenser rule).

### 7. Egg hatch (`Brood`)

✅ `EggEntity#onCollision(HitResult)` — the vanilla 1/8 hatch roll lives here. Modify the chance and
the chick count. Costs the player an egg, so it is self-limiting.

### 8. Multi-Breed and Herdsman's Call

`UseEntityCallback` (right-click entity) for Multi-Breed: if the held item is the target's breeding
item, set every same-type breedable `AnimalEntity` within `getMultiBreedRadius()` into love mode,
consuming **one** item, and return a result that stops vanilla consuming per-animal.
Herdsman's Call rides the existing super-ability infra — activation goes through the same
`checkAbilityActivation` path the combat supers use ([[combat-superability-activation-gap]]).

---

## MC-free core (`HusbandryManager extends SkillManager`)

Keep it MC-free: the manager decides *whether* and *how much*; the mixin/listener does the entity work.

- `float getBreedXp(String entityConfigString)` / `getRaiseXp(...)` / `getShearXp()` /
  `getHoneyXp()` / `getMilkXp()` / `getBrushXp()` / `getFeedBabyXp()`
- `boolean rollTwins()` — pinned RNG
- `boolean rollSelectiveBreeding()` and `double getStatBias()`
- `int getMultiBreedRadius()` — `min(40, base + level * perLevel)`
- `boolean rollBonusHarvestDrop()` / `rollToolDurabilitySave()` / `Optional<ItemSpec> rollHiddenBounty()`
- `double getGrowthAccelerationMultiplier()` / `double getHatchChance()` / `int rollChickCount()`
- `boolean canHarvestHiveSafely()` (Beekeeper)
- Standard `canX()` gates (`RankUtils.hasUnlockedSubskill` + `Permissions.isSubSkillEnabled`)

## Registration specifics

Follow the overview's "add a `PrimarySkillType`" checklist in full. Skill-specific:

- `PrimarySkillType.HUSBANDRY`, in `GATHERING_SKILLS`.
- `SubSkillType`: `HUSBANDRY_MULTI_BREED`, `HUSBANDRY_TWINS`, `HUSBANDRY_SELECTIVE_BREEDING`,
  `HUSBANDRY_ACCELERATED_GROWTH`, `HUSBANDRY_BROOD`, `HUSBANDRY_BOUNTIFUL_HARVEST`,
  `HUSBANDRY_HIDDEN_BOUNTY`, `HUSBANDRY_BEEKEEPER`, `HUSBANDRY_HERDSMANS_CALL`.
- `SuperAbilityType.HERDSMANS_CALL` (D-H7) — the 6-arg constant + the `subSkillTypeDefinition` wiring
  in the static block (`SuperAbilityType.java:116-118`).
- `experience.yml`: a `Husbandry.Animal_Breeding` per-species table (mirror the shape of
  `Taming.Animal_Taming` at line 759 — same `ConfigStringUtils.getConfigEntityTypeString` keying),
  flat values for the other verbs, the XP modifier, the Experience_Bars colour block, and the
  `ExploitFix.Husbandry.*` toggles (`Milk_Cooldown_Seconds`, plus a general `Require_Player_Harvest`).
- `advanced.yml`: Twins chance/cap, Multi-Breed radius/increment, harvest bonus/durability/treasure
  chances, growth multiplier, hatch chance.
- `skillranks.yml`: the unlock levels in the sub-skill table (**RetroMode ×10**, `PLAYTEST_G.md` §0).
- Locale block `Husbandry.*` — ⚠️ **and the three ungreppable families**: `XPBar.Husbandry`,
  `Overhaul.Name.Husbandry`, `Commands.XPGain.Husbandry` are built by string concatenation, so a miss
  renders as a literal `!XPBar.Husbandry!` and **grep will not find it**. `SkillLocaleCompletenessTest`
  now pins these — see [[dynamic-locale-key-families]]. Stealth and Unarmored each shipped missing keys
  through exactly this hole.
- Hidden Bounty's treasure table: reuse `TreasureConfig`/`ItemSpec` if it grows past a handful of
  entries, otherwise inline in `advanced.yml`.

## Suggested build order

One stage lands **fully** — code + config + locale + unit tests + green boot + played §G rows — before
the next starts.

| Stage | Content | Why this order |
|---|---|---|
| **0** | Skill registered, MC-free `HusbandryManager`, all config/locale/`/mcstats`, old-profile regression test. **No mechanics.** | The registration surface is boring and wide; land it alone so a failure is unambiguous |
| **1** | **Breed XP** + `Twins` + `Multi-Breed` (the breed mixin) | The one verb with no farm risk. Proves `getLovingPlayer()` resolution and the once-per-pair rule |
| **2** | **Raise XP** + the D-H6 bred-by marker + `Accelerated Growth` | Depends on stage 1's marker. **The `onGrowUp` double-fire trap lives here** — budget for it |
| **3** | **Shear** + `Bountiful Harvest` (drops + durability save) | The first harvest verb; establishes the shared reward path and the real-player gate |
| **4** | **Hive / milk / brush** on that same reward path, + `Beekeeper` + D-H5's cooldown | Cheap once stage 3 exists — three verbs, one path |
| **5** | `Selective Breeding`, `Brood`, `Hidden Bounty` | The flavour tier; none of it blocks anything |
| **6** | **`Herdsman's Call`** (super ability) | Last, because it multiplies every verb above it and is meaningless until they all work |

## Testing

- **Unit (MC-free):** `rollTwins` pinned-RNG (0 → always double, 100 → never); `getMultiBreedRadius`
  clamps at 40; every harvest roll and every XP value; the D-H5 cooldown arithmetic.
  ⚠️ **Stub the shipped config default in a fixture, not Mockito's zero** — a `0` cooldown silently
  disables the D-H5 gate for every other test in the file ([[unarmored-skill-build]]).
- **Mixin tests:** structural assertion that each injector applied — assert a real marker (`@Unique`
  field / handler in `getDeclaredMethods()`), **never `Class.forName`**, and cap slice-anchored
  injectors with `allow = N` ([[mixin-slice-allow-guard]]).
- **Mutation checks, specifically:** delete the `!isBaby()` gate → a test must go red. Delete the
  bred-by marker gate → a test must go red. Point the shear hook at `sheared` instead of `interactMob`
  → the dispenser test must go red.
- **§G rows** (new `PLAYTEST_G` session): breed cows 10× → XP delta; watch a Twins double-birth at high
  rank; **leave a bred baby for 20 minutes and confirm the raise payout fires exactly once**;
  **put shears in a dispenser aimed at a sheep and confirm it pays ZERO**; harvest a hive without a
  campfire at Beekeeper rank; milk one cow 20× in a row and confirm the cooldown holds; brush an
  armadillo; throw a stack of eggs at `Brood` rank; Multi-Breed one wheat across a full pen; trigger
  Herdsman's Call.
- **Regression:** old profile loads with Husbandry defaulted (overview checklist item 19).

## Cuts / deferrals

- **Butchering** — ✅ cut, see the "Explicitly NOT XP sources" table. Tied to `hunter.md`.
- **Shears-on-leaves as a Husbandry XP source** — cut this pass (D-H3); it belongs to
  Woodcutting/Herbalism and it is spammable.
- **Naming/equipping livestock** (D-H8) — v1.1.
- **Persisting the bred-by marker across restarts** (D-H6) — deferred; accept the loss.
- If the breed mixin proves flaky (loving-player resolution, twin placement inside blocks), **stage 3
  onward is fully independent of stages 1–2** — ship the harvest half first. That is a real fallback
  now, which it was not when the skill had only two verbs.
