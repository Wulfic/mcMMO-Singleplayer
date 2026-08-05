# Cooking — Plan

**Read [00-OVERVIEW.md](../00-OVERVIEW.md) first.** Cooking is a **standalone primary skill** (D1 shape)
and belongs in **`MISC_SKILLS`** — it is a *processing* skill, and every other processing skill in the
mod (`SMELTING`, `REPAIR`, `SALVAGE`, `ALCHEMY`) is already there. It needs **no F1** (tick sampler)
and **no F2** (attribute service): every one of its verbs is a discrete event.

> 🔴 **STATUS: PLAN ONLY. NO CODE.** Nothing named Cooking exists in `src/` (verified — the only hits
> are three comments in `SmeltingListener` / `SmeltingManager` explaining why food is *excluded* from
> Smelting).
>
> ✅ **REFINEMENT PASS 2026-08-05 — four user rulings landed, and the plan below reflects them:**
> 1. **D-CK1: quality is DROPPED ENTIRELY.** Not stored on the item (it jams the furnace), and not
>    rolled at eat time either. ⇒ **`COOKING_GOURMET_MEAL` is CUT** and the roster is **three**
>    sub-skills, not four.
> 2. **D-CK4: no Cook's Diet.** Confirmed cut.
> 3. **D-CK8: flat `Max_Cooks_Per_Hour: 1200`**, one rolling hour, as originally written.
> 4. **D-CK5 scope: Power Cook fires on COOKED/CRAFTED foods only.** An apple you picked off a tree
>    grants nothing. Table re-authored: 21 rows → **15**.
>
> ⚠️⚠️ **And one correctness defect found in that pass, bytecode-verified — see D-CK5.** Three rows
> mapped to **Saturation**, which is an `InstantStatusEffect` that fires *every tick*. Remapped.
>
> ⚠️ **The plan was written before GitHub #9 and #10 shipped and its registration surface was stale.**
> Three completeness tests now go red the instant the enum constant lands, and two Cooking mechanics
> are **not** covered by the per-skill disable switch for free. See the new **D-CK9**.
>
> ⚠️ **This file was rewritten 2026-08-03 from the draft committed in `162d6f593`.** The draft was a
> raw YAML config blob written against a **Bukkit plugin** that does not exist here — it referenced
> "economy plugins", "the server plugin", NBT, and "hotkeys exposed by the server". Its mechanics
> were audited against 1.21.11 bytecode and against this port's shipped code. **Three of its five
> abilities are mechanics a shipped skill already owns; the other two were plumbing for its central
> mechanic — the item-borne quality tier — which jams the furnace.** The full defect ledger is in
> [§Appendix — what the draft got wrong](#appendix--what-the-draft-got-wrong); it is kept because the
> same mistakes are easy to make twice.

**Stage gate in use (the Husbandry / Hunter precedent):** code + config + locale + unit tests +
`./gradlew build` exit 0 + a clean headless boot, per stage. Each stage pauses for review and is
committed to memory before the next starts.

---

## Concept

Cooking is the **food-processing** skill: it owns the transformation from ingredient to meal, and what
that meal does to you when you eat it. It is the only skill in the mod whose payoff is a **status
effect the player chooses in advance** — you decide what you are going to need before the fight, and
you eat it.

The whole design rests on one boundary, so state it before anything else:

| Neighbour | Owns | Cooking owns |
|---|---|---|
| **Smelting** | the furnace, for **ores** | the furnace/smoker/campfire, for **food** |
| **Herbalism / Fishing / Husbandry** | growing, catching, raising the **ingredient** | the **transformation** of it |
| **Farmer's Diet / Fisherman's Diet** | **+hunger** on their own food sets | **+effect** and **+saturation**, on everything |
| **Alchemy** | brewed potions — long, strong, amplifiable | food effects — **short, weak, amplifier 0** |
| **Taming / Hunter / Axes** | killing the animal | never; see [D-CK7](#d-ck7--butchery-is-cut-and-it-is-not-cookings-territory) |

**The Smelting boundary is not a convention we are agreeing to — it is already enforced in shipped
code, in both directions.** `Experience_Values.Smelting` in `experience.yml` lists **ores only** (25
entries, `Coal_Ore` … `Cobbled_Deepslate`), so cooking a raw beef pays **zero** Smelting XP today; and
[`SmeltingListener.boostFuelTime`](../../src/main/java/com/gmail/nossr50/fabric/listeners/SmeltingListener.java#L213-L225)
gates on `SmeltingManager.isSmeltable(input)` and returns vanilla burn time otherwise, with the
comment *"so cooking food burns at vanilla speed."* The food half of the furnace is empty **by
construction**. This is the cleanest skill boundary anything in Pass 2 has had — Husbandry needed a
verb rule to fend off Taming, Unarmored needed one letter of enum distance from Unarmed. Cooking
needs neither, because Smelting already cut the hole and left it open.

---

## The seams — all six verified

| Verb | Seam | State |
|---|---|---|
| Cook in **furnace / smoker / blast furnace** | `AbstractFurnaceBlockEntity#tick` @ the `craftRecipe` invoke → [`AbstractFurnaceSmeltMixin#mcmmo$onSmeltComplete`](../../src/main/java/com/gmail/nossr50/fabric/mixin/AbstractFurnaceSmeltMixin.java#L58-L72) | ✅ **already injected** — add a food branch to `SmeltingListener.onFurnaceSmelt` |
| **Fuel efficiency** on food | the `getFuelTime` `@ModifyExpressionValue` in the same mixin | ✅ **already injected** — take the `else` of a gate that already exists |
| **Bonus cooked item** | the `setLastRecipe` invoke (vanilla's "the smelt succeeded" marker) | ✅ **already injected** — Second Smelt's own seam |
| Cook on **campfire** | `CampfireBlockEntity#litServerTick` | 🔨 **new mixin.** `CampfireBlockEntity extends BlockEntity`, **not** `AbstractFurnaceBlockEntity` (javap-verified) — the furnace mixin does not reach it |
| **Craft** food (bread, cookie, pie, stew, cake) | `CraftingResultSlot#onCrafted(ItemStack)` | 🔨 **new mixin.** Mirror [`FurnaceOutputSlotMixin`](../../src/main/java/com/gmail/nossr50/fabric/mixin/FurnaceOutputSlotMixin.java) exactly — same shape, same funnel argument |
| **Eat** | `FoodComponent#onConsume` → [`FoodListener`](../../src/main/java/com/gmail/nossr50/fabric/listeners/FoodListener.java) | ✅ already injected, ⚠️ **needs surgery** — see **D-CK2** below |

**Four of the six already exist.** That is why Cooking is a cheap skill to build — and a cheap one to
get wrong, because "the seam is already there" is not the same as "the seam does what you want" (see
D-CK2, where the existing seam actively refuses most of the food in the game).

> 🔑 **`CraftingResultSlot#onCrafted` is player-only by construction, and that is a feature.**
> `CrafterBlock` (the 1.21 auto-crafter) has its own `craft(BlockState, ServerWorld, BlockPos)` and
> **never references `CraftingResultSlot`** (javap-verified: zero occurrences in its disassembly). A
> recipe-level or item-level hook would pay a redstone auto-crafter fed by a wheat farm. This one
> cannot. Do **not** "improve" it to a more general seam later without re-deriving that property.

---

## ⚠️ Design decisions

### ⚠️⚠️ D-CK1 — The item-borne quality tier is DEAD. It jams the furnace.

**This is the draft's central mechanic and it cannot ship.** The draft stored Normal/Great/Gourmet on
the cooked item (`meal_quality.storage: "NBT"`). Three verified steps kill it:

1. **There is no NBT to write to.** Since 1.20.5 an item's data is its **component map**. The port's
   only item-marker mechanism is `DataComponentTypes.CUSTOM_DATA`
   ([`PlatformPlayer.java:406`](../../src/main/java/com/gmail/nossr50/platform/PlatformPlayer.java#L406)),
   used for the Super Breaker dig boost.
2. **`ItemStack.areItemsAndComponentsEqual` compares the whole component map.** Bytecode: same item,
   then `Objects.equals(this.components, that.components)` over the `MergedComponentMap`. **No
   exclusion list.** A stamped stack is not equal to an unstamped one.
3. **`AbstractFurnaceBlockEntity.canAcceptRecipeOutput` returns `false` on that inequality** (bytecode,
   offsets 64–75: `areItemsAndComponentsEqual(outputSlot, recipeResult)` → `ifne` → `iconst_0;
   ireturn`), and `tick` never reaches `craftRecipe` when it does.

**Consequence: the first quality-stamped steak lands in the output slot and the smoker stops. It stays
stopped until the player empties it by hand.** `craftRecipe` produces the *vanilla* result, which will
never be component-equal to the stamped stack sitting on top of it. And in the inventory, a stack of
64 cooked beef splits into three unmergeable piles.

> ⚠️ Note *why* the existing `CUSTOM_DATA` use is safe and this one is not: it is only ever applied to
> **pickaxes and shovels** (`canBeDigBoosted`), which have max stack size 1. Non-stacking costs nothing
> there. Reusing the mechanism on a food stack is the trap.

> ✅ **RULED 2026-08-05 (user): quality is DROPPED ENTIRELY.** Not on the item, and not rolled at eat
> time either. There is no Normal/Great/Gourmet tier anywhere in this skill.
>
> **⇒ `COOKING_GOURMET_MEAL` is CUT** along with it — it was the eat-time half of the same mechanic.
> The roster below is **three** sub-skills.
>
> ❌ **Rejected: stamp on the way out of the output slot** (`FurnaceOutputSlot#onCrafted`, so the
> furnace never sees the marked stack). It dodges the jam but keeps the inventory split — three
> unmergeable piles of steak is a worse bug than no feature, and it would be reported as a dupe.
>
> ❌ **Rejected: roll quality at eat time from the eater's level** (the 2026-08-03 recommendation).
> Technically sound — no component write, no jam, no stacking bug — but it is **invisible**: the item
> is identical, so a Gourmet roll is a silent double-duration the player can only detect by watching
> the effect timer. It would have needed its own notification to mean anything, which is plumbing for
> a mechanic Power Cook's rank scaling already delivers legibly.
>
> **What this costs, stated honestly: Cooking's only genuinely new mechanic is now Power Cook.**
> Master Chef and Kitchen Efficiency are Smelting's two passives pointed at food (D-CK3). That is a
> defensible shape for a *processing* skill — Salvage is the same story against Repair — but it does
> mean the skill lives or dies on the effect table being good. Budget the review time there.

### ⚠️⚠️ D-CK2 — The `FoodListener` chain returns early, and eats most of the food in the game

[`FoodListener.onFoodConsumed`](../../src/main/java/com/gmail/nossr50/fabric/listeners/FoodListener.java#L69-L83)
is an `if / else if / else` chain and **every arm returns**:

```java
if (HerbalismManager.isFarmersDietFood(itemPath))      { … return; }   // 12 foods
else if (FishingManager.isFishermansDietFood(itemPath)) { … return; }   //  5 foods
else                                                    { return; }     // "not a diet food"
```

Appending Cooking as a third `else if` — the obvious edit, and the one a code reviewer would wave
through — means **Cooking never fires on 17 of the 41 foods in the game**, including `bread`,
`cookie`, `pumpkin_pie`, `mushroom_stew`, `baked_potato`, `golden_carrot`, `cooked_cod` and
`cooked_salmon`. Those are exactly the foods a cook cooks.

**This is the same ordering trap that has now bitten this port four times** (see
`[[unarmored-skill-build]]`, `[[husbandry-wiring-audit]]`). It is silent: no error, no log, no failing
test on either side alone, because each arm is self-consistent.

> ✅ **Required restructure: the diets stay mutually exclusive with each other; Cooking runs
> unconditionally, after them.**
>
> ```
> bonusHunger = farmersDiet ?: fishermansDiet ?: 0     // unchanged, still exclusive
> cooking.onFoodConsumed(stack, nutrition, saturation) // ALWAYS, for every food
> ```
>
> Pin it with a test that eats **bread** (a Farmer's Diet food) and asserts the Cooking effect fired.
> An "assert off the reference point" test — the Stealth lesson — not a test against a food only
> Cooking claims.

Two more properties of that seam, both load-bearing:

- **`if (nutrition <= 0) return;` sits ABOVE the chain.** Anything Cooking wants to do for a
  zero-nutrition consumable is unreachable without moving the guard.
- **`milk_bucket` and `cake` have no `FoodComponent` at all** (javap: `FoodComponents` has 41 fields
  and neither is one — cake is a *block*, eaten through `CakeBlock`). The seam **can never fire for
  them.** The draft mapped both. Delete them or hook `CakeBlock` deliberately; do not leave them in a
  table implying they work.

### D-CK3 — Kitchen Efficiency and Master Chef are legal, and they are Smelting's mechanics on a different item class

Say this out loud rather than discovering it in review (the D3 convention):

- **Kitchen Efficiency** = `SMELTING_FUEL_EFFICIENCY(3)`, applied to the branch Smelting refuses. Same
  seam, same `@ModifyExpressionValue`, same rank count. Legal because `boostFuelTime` already returns
  vanilla for food.
- **Master Chef's bonus item** = `SMELTING_SECOND_SMELT`, applied to food results. Legal because
  `Bonus_Drops.Smelting` in `config.yml` lists 12 **ore products** and no food.

Both are duplications *of implementation*, not of reward: no player can hold both bonuses on the same
smelt, because the input is either an ore or a food and never both. **But it means Cooking's two
passive sub-skills are, mechanically, Smelting's two passive sub-skills.** That is fine for a
processing skill — it is what processing skills do — and it is the reason Cooking must not *also* ship
a diet (D-CK4) or a super ability (D-CK6). Strip those and what is left that is genuinely new is
**Power Cook**, which is the whole reason to build the skill.

⚠️ **The Second Smelt deviation is inherited.** `onSmeltComplete` gates only on the **result**, because
`craftRecipe` has already decremented the input by the time it runs (documented at
`SmeltingListener.java:176-184`). Cooking's bonus-item table is keyed on the **cooked result**, not the
raw input, for the same reason. Do not write it the other way and expect it to work.

### D-CK4 — No third diet. "Cook's Diet" is CUT.

The wiki asked for *"Cooks diet: making cooked foods restore more hunger."* That is Farmer's Diet and
Fisherman's Diet **a third time**, and its food set overlaps both of them: `bread` is Farmer's,
`cooked_cod` is Fisherman's. Two shipped skills already pay for +hunger on those exact items, and
D-CK2's restructure would have all three firing on one bite.

> ✅ **RULED 2026-08-05 (user): CUT.** Cooking's payoff is the **effect**, not the hunger. Ship no
> `.Diet` sub-skill and no `+nutrition` at all. Do not re-open.
>
> **The alternative that was rejected**, recorded so it is not re-proposed: cover the foods **neither** existing diet
> claims — `cooked_beef`, `cooked_porkchop`, `cooked_chicken`, `cooked_mutton`, `cooked_rabbit`,
> `rabbit_stew`, `beetroot_soup`, `suspicious_stew`, `dried_kelp`, `honey_bottle`, `sweet_berries`,
> `apple`, `chorus_fruit`, `tropical_fish`, `pufferfish`, `rotten_flesh`, `spider_eye`,
> `poisonous_potato` (18 of the 41). That set is real and non-overlapping — but it makes Cooking a
> hunger skill, which is the least interesting thing it could be, and it would leave the skill with
> **two** payoffs competing for the same bite.
>
> ⚠️ **This ruling is now load-bearing in a way it was not on 2026-08-03.** D-CK5a proves Saturation
> fills the hunger bar every tick, and D-CK1 cut Gourmet Meal — so "Cooking grants no hunger, ever"
> is the single rule that keeps both the sub-skill and the effect table honest. **Any future proposal
> that gives Cooking a hunger payoff has to re-open D-CK4, D-CK5a and D-CK1 together.**

### D-CK5 — The effect budget, or Cooking deletes Alchemy

The draft's **Flavor Burst** grants 30–60 s of a **randomly chosen** effect from a pool of ten —
Strength, Haste, Speed, Regeneration, Resistance, Fire Resistance, Water Breathing, Night Vision,
Luck, Jump — on a 5-minute cooldown, forever, for free. **That is a splash potion of anything, and
Alchemy is the skill whose entire job is making those.** It also fails the interesting half of the
wiki idea, which was that *the food you chose* is the effect you get.

The wiki itself was far more conservative than the draft: *"potion effects only last for **2 seconds**"*
at level 100, scaling to 1000.

> ✅ **RULED budget for Power Cook, all five clauses:**
>
> 1. **The effect is determined by the food. Never random.** Steak is Strength. Baked potato is Haste.
>    That is the mechanic; the randomness was noise on top of it.
> 2. **Amplifier is always 0.** No Strength II from a sandwich.
> 3. **Duration is measured in seconds and scales with rank: 3 s at rank 1 → 15 s at rank 5.** The
>    wiki's shape, one notch more generous. A brewed Strength potion is 3:00 at amplifier 1 — a
>    factor of **twelve**, before the amplifier. Alchemy stays worth levelling.
> 4. **No effect that trivialises a hazard for longer than it takes to cross it.** Fire Resistance and
>    Water Breathing are **excluded from the table entirely**; 15 s of either is a lava-lake shortcut
>    and a monument shortcut, and both are Alchemy's to sell.
> 5. ✅ **RULED 2026-08-05 (user): the table covers COOKED and CRAFTED foods only.** A food that
>    reaches your hand without passing through a furnace, smoker, campfire or crafting grid grants
>    **nothing** — `apple`, `melon_slice`, `sweet_berries`, `glow_berries`, `honey_bottle` and every
>    raw meat and fish are out. The rule is legible in one sentence ("Cooking rewards you for eating
>    what you cooked") and it drops the table from 21 rows to 15.
>
> ⚠️ Vanilla already grants effects from `golden_apple`, `enchanted_golden_apple`, `golden_carrot`,
> `chorus_fruit`, `suspicious_stew`, `poisonous_potato`, `pufferfish`, `rotten_flesh` and
> `spider_eye`. Cooking **must not touch those nine** — stacking our effect on top of vanilla's is
> the "does not stack, extend duration instead" clause the draft hand-waved, and it is not worth the
> code. They are simply absent from the table.

#### ⚠️⚠️ D-CK5a — the Saturation rows were a hunger cannon (found 2026-08-05, bytecode-verified)

The 2026-08-03 table mapped **`bread`, `melon_slice` and `apple` → Saturation**. That is not a mild
effect, it is the strongest entry in the table by an order of magnitude, and it walks straight through
D-CK4's "Cooking grants no hunger" ruling by the back door:

```
SaturationStatusEffect extends InstantStatusEffect
  applyUpdateEffect(world, entity, amplifier):
      ((PlayerEntity) entity).getHungerManager().add(amplifier + 1, 1.0f)   // +1 food, +1 saturation
InstantStatusEffect.canApplyUpdateEffect(duration, amplifier):
      return duration >= 1                                                  // ⇒ EVERY TICK
```

⇒ At amplifier 0, **3 seconds = 60 ticks = +60 food points onto a 20-point bar.** Rank 1 Power Cook
would fill the hunger bar from empty, instantly, from one slice of bread, forever. Hunger stops being
a resource in this game.

> ✅ **Saturation is struck from the table entirely.** `melon_slice` and `apple` are gone anyway under
> clause 5 (neither is cooked); **`bread` is remapped to Speed** — the traveller's loaf, and the one
> row that had to be re-authored rather than deleted.
>
> 🔑 **The reusable rule: an effect's NAME does not tell you its tick cadence.** Check
> `canApplyUpdateEffect` before putting any status effect in a config table. `InstantStatusEffect`
> subclasses (Saturation, Instant Health, Instant Damage) apply *per tick* for the whole duration and
> are never safe on a duration-scaled mechanic. **Stage 0 must dump the cadence for every effect the
> final table names**, not just the food list.

### D-CK6 — No super ability

`SMELTING`, `ALCHEMY`, `REPAIR` and `SALVAGE` — the other four `MISC_SKILLS` processing skills — have
**no super ability between them**. Cooking having one would make it the odd member of its own
category, and D2's standing stance on wiki mega-abilities is *cut from v1*.

There is also a concrete cost: `config.yml` already carries a warning that Stealth's
`Smoke_Bomb_Item` **must differ from Agility's `Second_Wind_Item`** because both actives listen on the
same use-item event. A third would be a third item to keep distinct and a third refusal message to
route.

> ✅ **RECOMMENDED: no `SuperAbilityType.COOKING_*`, and no dead enum for one.** `[[readme-and-github-wiki]]`
> records that this port already renders **8 dead Limit Breaks and 5 dead super abilities** on
> `/mcstats`. Do not add a ninth.

### D-CK7 — "Butchery" is cut, and it is not Cooking's territory

The wiki's *"Butchery: Level 350. All damage you do to animals results in an instant kill while
wielding an axe. Plus improves the drops"* is three other skills' property and one exploit:

- "instant kill with an axe" → **Axes**, and it is Skull Splitter's territory.
- "improves the drops" → **Hunter**'s Trophy Hunter, which already ships a loot re-roll on
  `MobEntity#dropLoot`, tiered by mob.
- Instant-killing every animal is an **XP cannon** pointed at Taming, Husbandry, Hunter *and* Cooking
  simultaneously — one swing per cow, at a cow farm.

> ✅ **CUT. No sub-skill, no config flag, no enum.** The wiki's "Holy Cook" (splash potion of Healing
> also restores hunger, *"as well as your allies'"*) is likewise **cut**: in singleplayer there are no
> allies, which leaves "throw a potion at yourself for 2 hunger" as the entire feature.

### ⚠️⚠️ D-CK8 — The XP gate. This is the skill's only real risk, and Cooking has no origin flag to hide behind.

Hunter's D-HU1 lesson, restated for a skill with worse odds: **the gate between hand-play and a farm
*is* the feature.**

Cook times, read out of the vanilla recipe data in the 1.21.11 jar (`cookingtime`, not recalled —
`data/minecraft/recipe/cooked_beef*.json`): **smoking 100 ticks (5 s), smelting 200 (10 s), campfire
600 (30 s)**. So one smoker is **720 items/h, unattended**; a furnace 360; a campfire 120. A
hopper-fed array of eight smokers behind a chicken farm is **5,760/h with the player asleep**, and the
furnace-owner map — `FURNACE_OWNERS`, populated by a single right-click and held for the whole
session — keeps paying that player the entire time.

⚠️ The campfire being **6× slower than a smoker** is why Stage 5 can safely ship it last and why it
needs no separate cap: it is the one cooking block nobody automates.

Cooking has **none** of the four gates Hunter got:

| Hunter's gate | Cooking equivalent |
|---|---|
| transient-entity check | — nothing to check |
| player-created golem check | — |
| killing blow is player-attributed | — a furnace has no attacker |
| **spawn origin** (`EntityTypeSpawnOriginMixin`) | — **an item has no spawn origin** |

> ⚠️ **Smelting has this exact hole today and shipped with it. That is precedent, not permission.**
> Smelting's inputs are **ores**, which are themselves gated by having to be mined; Cooking's inputs
> are **chickens**, which are gated by nothing. The same hole is worth an order of magnitude more here.

> ✅ **RULED 2026-08-05 (user), and it ships in Stage 2 with the XP — not reserved as a backstop: a
> rolling credited-cook cap.** `experience.yml → ExploitFix.Cooking.Max_Cooks_Per_Hour`, default
> **1,200** (two continuously-running smokers), **one flat rolling hour**. Cooks beyond it still cook;
> they simply pay nothing.
>
> ⚠️ **The known cost of the flat shape, accepted deliberately:** it is bursty. A stack of 64 raw beef
> through eight smokers spends a large slice of the hour's budget in minutes, and the player then
> earns nothing for the rest of the hour with no on-screen explanation. The windowed alternative
> (`Awards_Per_Window` + `_Window_Seconds`, the shipped `ExploitFix.Husbandry` shape) smooths that at
> the same 1,200/h rate and was **considered and not taken**. If §G row CK6 makes the dead hour feel
> like a bug rather than a limit, that is the retune — the key rename would need a
> `ConfigRetunes`-style migration, so decide it before the first release that ships the key.
>
> ⚠️ **The cap must tell the player it fired**, or it is indistinguishable from the skill being
> broken. One throttled `NotificationManager` line the first time a cook is refused in a window,
> not one per cook.
>
> **Hunter's lesson, verbatim:** *the leak is worth nothing until XP makes it valuable — close it in
> the stage that creates the value.* Hunter deferred its rolling cap and the open §G question it left
> (HN33–HN35, the farms no origin flag can close) is **still open**. Cooking should not repeat that;
> here the cap is not a backstop, it is the only gate that exists.
>
> ❌ **Rejected: an owner-proximity check** ("owner must be within N blocks"). It is a per-tick
> distance test on a hot block-entity path, it breaks the legitimate case of loading a furnace and
> going mining, and a farm builder simply AFKs next to the array.

**The arithmetic**, against the standard `10N² + 1010N` curve — **11,010,000 XP** to RetroMode 1000 —
and the ~80–100 h floor every skill in this port is held to:

```
1,200 credited cooks/h × 100 h = 120,000 cooks
11,010,000 ÷ 120,000           = ~92 XP per average cook
```

| Verb | XP | Why |
|---|---|---|
| Smelt/smoke/campfire a raw food → cooked | **100** | the core verb; the number the budget is derived from |
| Bake a potato, dry kelp | **60** | one free ingredient, no crafting step |
| Craft bread | **80** | 3 wheat |
| Craft a stew or soup | **150** | 3–4 ingredients + a bowl |
| Craft pumpkin pie | **200** | pumpkin + sugar + egg |
| Craft a cake | **300** | 3 milk + 2 sugar + 1 egg + 3 wheat, yields **1** |
| Craft cookies | **10** | ⚠️ **the recipe yields 8.** Price **per item produced**, or one craft pays 8× — see below |
| Golden apple, golden carrot, enchanted golden apple | **0** | 8 gold ingots. A gold farm is not a cooking skill |
| Anything eaten | **0** | XP is for cooking, not for eating |

> ⚠️ **`CraftingResultSlot#onCrafted(ItemStack)` is called once per *take*, and the stack it hands you
> holds the whole batch.** Bytecode: it reads its own `amount` field and passes it to
> `ItemStack#onCraftByPlayer(player, amount)`. **XP must be priced per item and multiplied by the
> count**, or a shift-click that crafts 64 cookies pays for one. Getting this backwards in either
> direction is an 8×–64× error and it will not look like a bug in a unit test that crafts one loaf.

### ⚠️⚠️ D-CK9 — The registration surface moved under this plan. GitHub #9 and #10 shipped after it was written.

This plan was authored 2026-08-03. `8a5470941` (#10, the per-skill enable/disable switch) and
`ed5eb2f15` (#9, the anti-cheat tab) landed 2026-08-05 and **both added machinery that a new
`PrimarySkillType` is now obliged to satisfy.** The `00-OVERVIEW.md` checklist does not mention any
of it. All three items below are **Stage 1**, not clean-up.

**1. Four completeness tests go red the moment the enum constant lands.** Stage 1 is not "nothing
fires yet" — it is *satisfy all four before you write a mechanic*. Verified by grep, not recalled:

| Test | What breaks |
|---|---|
| `DatatypeEnumTest.primarySkillTypeHasAllTwentySixSkills` | asserts `19 + 3 + 1 + 1 + 1 + 1`. **A hard-coded count** — re-derive it as `+ 1`, and rename the method; it already lies about "twenty-six" if you only bump the sum |
| `SkillGatingTest.everyPrimarySkillHasAnEntryInTheShippedConfig` | `coreskills.yml` must gain a `Cooking:` block. **This is #10's; the old checklist predates the file having all 26 entries** |
| `SkillLocaleCompletenessTest` | **five** separate loops over `values()` — XP-bar title, the title's `{0}` placeholder, `Overhaul.Name`, `SkillName`, `Commands.XPGain` |
| `SkillStatsRendererTest.everySkillResolvesToANonNullRenderer` + `noRenderedLineLeaksARawColourCode` | the generic fallback covers the first; the second renders Cooking at levels 0 / 500 / 1000 |

`FlatFileProfileStoreTest` also loops `values()` and should pass free — that is checklist item 19
already working. **Confirm it does; do not assume it.**

**2. ⚠️⚠️ Two of Cooking's three sub-skills are NOT covered by the disable switch for free.**
[`SkillGating`](../../src/main/java/com/gmail/nossr50/util/skills/SkillGating.java) derives a
sub-skill's parent from its **enum-name prefix**, so `COOKING_*` maps to `COOKING` automatically —
but the gating only bites at three chokepoints: `Permissions` predicates, `RankUtils` **boolean**
rank gates, and `ProbabilityUtil#isSkillRNGSuccessful` ([[issue-10-per-skill-toggle]]).

- `COOKING_MASTER_CHEF` is an RNG proc ⇒ **covered free.**
- `COOKING_KITCHEN_EFFICIENCY` is a **multiplier**, not a roll. Nothing gates it.
- `COOKING_POWER_COOK` applies an effect on a deterministic condition. Nothing gates it.

⇒ Both need an **explicit `SkillGating.isSkillEnabled` call**, or a player who switches Cooking off
in `coreskills.yml` still gets boosted fuel and still gets Strength off a steak. Pin each with its
own test — #10's lesson was that reasoning your way to "one gate covers everything" is how the
Agility XP redirect shipped.
⚠️ **And do not force `getRank` to 0 to shortcut this** — §F #9 landmine, 5th sighting.

**3. `ExploitFix.Cooking.Max_Cooks_Per_Hour` needs its Anti-Cheat tab entry in the SAME stage.**
#9 added `CAT_EXPLOITS` to
[`McMMOSettings`](../../src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L35)
plus a **converse completeness test** — a shipped `ExploitFix` key with no tab entry now fails the
build. Use `ConfigSetting.integer(CAT_EXPLOITS, EXPERIENCE_YML, …)`, mirroring the Husbandry entries
at [McMMOSettings.java:284-294](../../src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L284-L294).

> 🔑 **#9's rule applies here in full: never put a switch on a gate without first proving the gate has
> a live caller.** For Cooking that ordering is forced — the key, the gate and the toggle all land
> together in Stage 2, so the tab never describes a mechanic that does not exist. **A settings screen
> is the one place a dead mechanic becomes an active lie.**

---

## The sub-skill roster

**Three.** Cooking is a small skill and padding it is how the port ended up with 13 dead enums on
`/mcstats`.

| `SubSkillType` | Ranks | What it does | Seam | Disable-switch coverage |
|---|---|---|---|---|
| `COOKING_POWER_COOK` | 5 | Eating a cooked food grants its mapped effect, amplifier 0, 3 s → 15 s by rank | eat | ⚠️ **needs an explicit gate** (D-CK9) |
| `COOKING_MASTER_CHEF` | 5 | Chance at one bonus copy of a finished cook | `setLastRecipe` | ✅ free (RNG proc) |
| `COOKING_KITCHEN_EFFICIENCY` | 3 | Fuel burns longer when the input is a food | `getFuelTime` | ⚠️ **needs an explicit gate** (D-CK9) |

**Cut, with the reason recorded so it is not re-litigated:** Flavor Burst (D-CK5/D-CK6), Cook's Diet
(D-CK4), **Gourmet Meal** (D-CK1 — the eat-time half of the dropped quality tier), Precision Cooking
+ Meal Memory (both were plumbing for the same dead tier), Butchery and Holy Cook (D-CK7).

⚠️ **`SubSkillType` warns in-file that a sub-skill must not collide with any `PrimarySkillType`
name.** All four above are clear. `COOKING_SMELTING` and `COOKING_ALCHEMY` are not — do not reach for
them later.

### skillranks.yml — authored in RetroMode, ×10

Per the checklist item 12: author RetroMode, let the scaler produce Standard.

| Sub-skill | RetroMode ranks | Standard |
|---|---|---|
| `PowerCook` | 100 / 250 / 450 / 700 / 1000 | 10 / 25 / 45 / 70 / 100 |
| `MasterChef` | 50 / 200 / 400 / 650 / 900 | 5 / 20 / 40 / 65 / 90 |
| `KitchenEfficiency` | 250 / 500 / 850 | 25 / 50 / 85 |

Power Cook's rank 1 at **100** is deliberate and it is the wiki's own number.

⚠️ **With Gourmet Meal cut, nothing unlocks between level 1,000 and the cap except Power Cook rank 5**
— which also sits at 1,000. The last 300 levels of the skill are flat. That is honest for a
three-sub-skill processing skill (Salvage is no denser) and **no filler is being added to hide it**;
noted here so it is a known property at §G rather than a surprise.

---

## Power Cook — the food → effect table

⚠️ **This table is a STARTING POINT and Stage 0 must verify it against the live registry.** Use the
Hunter calibration method (`[[hunter-skill-build]]`): a throwaway JUnit test that walks
`Registries.ITEM`, filters on `DataComponentTypes.FOOD`, and dumps the set to TSV. **Do not author a
food list from memory** — the draft did, and 11 of its 44 entries were items with no food component
at all.

`javap net.minecraft.component.type.FoodComponents` reports **41** foods in 1.21.11. The nine that
already carry a vanilla effect are excluded (D-CK5), as are pure ingredients with no sensible mapping.

**15 rows.** Every one is a food that came out of a furnace, smoker, campfire or crafting grid —
D-CK5 clause 5. No `InstantStatusEffect` appears anywhere in it (D-CK5a).

| Food | Made by | Effect | Note |
|---|---|---|---|
| `cooked_beef` | cook | Strength | the wiki's own example: *"whip out a steak, get 2 strong hits in"* |
| `cooked_porkchop` | cook | Resistance | |
| `cooked_mutton` | cook | Resistance | |
| `cooked_chicken` | cook | Speed | |
| `cooked_rabbit` | cook | Jump Boost | |
| `cooked_cod` | cook | Dolphin's Grace | ⚠️ **not** Water Breathing — D-CK5 clause 4 |
| `cooked_salmon` | cook | Dolphin's Grace | |
| `baked_potato` | cook | Haste | the wiki's own example |
| `dried_kelp` | cook | Haste | |
| `bread` | craft | Speed | ⚠️ **remapped from Saturation** — D-CK5a. The traveller's loaf |
| `cookie` | craft | Speed | sugar rush |
| `pumpkin_pie` | craft | Regeneration | |
| `mushroom_stew` | craft | Regeneration | |
| `beetroot_soup` | craft | Regeneration | |
| `rabbit_stew` | craft | Jump Boost | |

**Deliberately absent, in three groups:**

1. **Vanilla already grants an effect** (D-CK5) — `golden_apple`, `enchanted_golden_apple`,
   `golden_carrot`, `chorus_fruit`, `suspicious_stew`, `poisonous_potato`, `pufferfish`,
   `rotten_flesh`, `spider_eye`.
2. **Raw** — `beef`, `chicken`, `cod`, `salmon`, `mutton`, `porkchop`, `rabbit`, `potato`, `carrot`,
   `beetroot`, `tropical_fish`. Cooking them is the point; paying for eating them raw inverts the skill.
3. **Not cooked or crafted** (D-CK5 clause 5, the 2026-08-05 ruling) — `apple`, `melon_slice`,
   `sweet_berries`, `glow_berries`, `honey_bottle`. Picked, not made. **Saturation and Glowing
   therefore no longer appear in the table at all**; Saturation is banned outright (D-CK5a), Glowing
   simply lost its only carrier.

⚠️ **Cross-skill interaction the earlier draft missed — a §G row, not a blocker.** `Speed` (3 foods)
and `Dolphin's Grace` (2) are **movement** buffs, and Agility's Parkour/Swimming domains pay
**speed-normalised** XP whose per-tick distance is clamped at the medium's reference speed (D5,
`[[agility-skill-build]]`). The clamp *should* make both neutral — that is exactly what it exists
for, and Fleet Footed is named in D5 as a buff it already absorbs. **Nobody has tested a
Cooking-granted movement buff against it.** Measure it (§G row CK7) before assuming the clamp holds;
if it does not, Cooking is an Agility XP multiplier.

The table lives in **`resources/config.yml` under `Skills.Cooking.Power_Cook_Effects`**, keyed by
registry path, so an operator can retune or disable a row — checklist item 14, and D-CK5's
"admins can disable specific mapped effects" is satisfied by the config engine, not by new code.

---

## Config surface

Follow the checklist in [00-OVERVIEW.md](../00-OVERVIEW.md) §"add a PrimarySkillType" in full. The
Cooking-specific entries:

| File | Adds |
|---|---|
| `experience.yml` | `Experience_Values.Cooking` (the XP table above); `Experience_Bars.Cooking` (**Color: `YELLOW`** — free; `PURPLE` is Repair/Salvage/Smelting, `GREEN` Woodcutting, `PINK` Stealth); `ExploitFix.Cooking.Max_Cooks_Per_Hour: 1200` |
| `skillranks.yml` | the three sub-skill ladders above |
| `advanced.yml` | `Skills.Cooking.MasterChef` (`ChanceMax` / `MaxBonusLevel`), `.PowerCook` (`Seconds_Per_Rank`), `.KitchenEfficiency` (`Multiplier_Per_Rank`) |
| `config.yml` | `Skills.Cooking.Level_Cap: 0`; `Skills.Cooking.Power_Cook_Effects`; `Bonus_Drops.Cooking` (the food result list Master Chef reads — **results, not inputs**, D-CK3) |
| **`coreskills.yml`** | **`Cooking.Enabled: true`** — ⚠️ **new since this plan was written** (GitHub #10). `SkillGatingTest.everyPrimarySkillHasAnEntryInTheShippedConfig` fails the build without it. D-CK9 |
| **`McMMOSettings.java`** | **the `CAT_EXPLOITS` entry for `Max_Cooks_Per_Hour`** — ⚠️ **new since this plan was written** (GitHub #9). The converse completeness test fails the build without it. D-CK9 |
| `locale_en_US.properties` | `Cooking.SkillName`; per sub-skill `.Name` / `.Description` / `.Stat` |

> ⚠️⚠️ **Three locale key families are built by string concatenation and are invisible to grep** —
> `XPBar.Cooking`, `Overhaul.Name.Cooking`, `Commands.XPGain.Cooking`. A miss renders as literal
> `!XPBar.Cooking!` in-game and nothing catches it at build time except
> `SkillLocaleCompletenessTest`, which **must** be extended with the new skill. Stealth and Unarmored
> shipped six of these missing between them. See `[[dynamic-locale-key-families]]`.
>
> ⚠️ **`.Stat` keys are exempt from that test.** Four skills shipped with no `/mcstats` Stats section
> at all because of it (`[[pass2-stats-renderers]]`). `CookingStatsRenderer` and its `.Stat` keys are
> **Stage 5**, not an afterthought.
>
> ⚠️ **`copyMissingDefaults` back-fills only ABSENT keys** — an existing on-disk `config.yml` never
> sees a *changed* default (`[[xp-boss-bar]]`). Every number above must be right the first time, or
> the live instance (`[[live-playtest-instance-forensics]]`) keeps the old one silently.

---

## Stages

One stage lands **fully** — code + config + locale + unit tests + `./gradlew build` exit 0 + clean
headless boot — before the next starts. Pause and commit to memory at each gate.

**Stage 0 — Verify, don't author.**
Throwaway JUnit test dumps every `Registries.ITEM` entry carrying `DataComponentTypes.FOOD` to TSV,
with nutrition / saturation / `eat_seconds` / existing effects. Reconcile against the Power Cook table
and against `FARMERS_DIET_FOODS` (12) and `FISHERMANS_DIET_FOODS` (5) so the overlap is a known set
before D-CK2's restructure, not after.
⚠️ **Also dump, for every effect the final table names, whether `canApplyUpdateEffect` is true at
duration 1** — the D-CK5a check. An `InstantStatusEffect` in a duration-scaled table is a hunger
cannon and the name does not warn you.
⚠️ **And derive the cooked/crafted set from the recipe manager, not by eye** (D-CK5 clause 5): walk
`RecipeManager` for `AbstractCookingRecipe` and `CraftingRecipe` results that carry a `FOOD`
component. That set *is* the Power Cook whitelist's domain; authoring it by hand is how the draft got
11 phantom foods. **No production code.** Delete the test when done.

**Stage 1 — Registration.**
`PrimarySkillType.COOKING`, the three `SubSkillType`s, `CookingManager extends SkillManager`
(**MC-free**), `McMMOPlayer.initManager` + typed getter, `SkillTools.MISC_SKILLS`, all five config
files, the locale block, and the **old-profile regression test** (checklist item 19). Nothing fires
yet. `/mcstats` renders the skill generically.

⚠️ **Stage 1 is gated on four completeness tests going green, not on "it compiles" — see D-CK9.**
`DatatypeEnumTest` (a **hard-coded** enum count), `SkillGatingTest.everyPrimarySkillHasAnEntryInTheShippedConfig`
(the new `coreskills.yml` entry), `SkillLocaleCompletenessTest` (five loops), and
`SkillStatsRendererTest` (two). Run the suite *before* writing the config, so you see all four fail
and know each one was actually satisfied rather than never reached.

**Stage 2 — Cook XP + the exploit cap.**
The food branch of `SmeltingListener.onFurnaceSmelt`; `CraftingResultSlotMixin` for the crafted foods
(⚠️ **× the batch count**); `Max_Cooks_Per_Hour` **and its `CAT_EXPLOITS` tab entry, same stage**
(D-CK9 item 3 — #9's rule forces the ordering). Campfires are **not** in this stage — furnace,
smoker and blast furnace all come free off the existing mixin, and shipping the free 90% first proves
the XP model before a new mixin is written.

⚠️ **Three hooks, and they are keyed on deliberately different things. Write that down before anyone
"unifies" them:**
> - **Furnace XP** (`craftRecipe`, shift = before) reads the **input** slot — `beef`. That is what the
>   existing hook hands over and it is Smelting's own convention.
> - **Master Chef** (`setLastRecipe`, after) reads the **output** slot — `cooked_beef` — because
>   `craftRecipe` has already decremented the input, which is *empty* when the last of it was consumed
>   (`SmeltingListener.java:176-184`).
> - **Crafting XP** (`CraftingResultSlot`) reads the **result** — `bread`.
>
> The furnace-XP and Master-Chef keys therefore live in **different key spaces on the same block**, and
> `Experience_Values.Cooking` needs entries for both raw inputs *and* crafted results. Hunter's lesson
> applies to the half that must agree: where two paths derive the same key, give them **one shared
> function**, not two tests (`[[hunter-skill-build]]`).

**Stage 3 — Kitchen Efficiency + Master Chef.**
Both ride injectors that already exist. `boostFuelTime` gets an `else` branch;
`SmeltingListener.onSmeltComplete` gets a food arm reading `Bonus_Drops.Cooking`.
⚠️ **`hasRoomForSecondSmelt` must be re-used, not re-derived** — it encodes the pre-merge/post-merge
count subtlety documented at `SmeltingListener.java:176-184`.
⚠️ **`ownerSkill(pos)` returns a `SmeltingManager`** ([SmeltingListener.java:350](../../src/main/java/com/gmail/nossr50/fabric/listeners/SmeltingListener.java#L350)).
Cooking needs its own resolver over the **same** `FURNACE_OWNERS` map — one map, two managers. Do not
duplicate the map, and do not make the existing method generic in a way that loses the null-owner
fast path.
⚠️ **Kitchen Efficiency needs an explicit `SkillGating` call** — it is a multiplier, not an RNG proc
(D-CK9 item 2). Master Chef gets gating free.
⚠️ **Decide the ordering when an item is in BOTH `Bonus_Drops.Smelting` and `Bonus_Drops.Cooking`.**
Nothing prevents an operator listing one item in both, and `onSmeltComplete` gates on the **result**
only, so the two arms are ambiguous by construction. Pick Smelting-first, write it down in the
javadoc, and pin it with a test.

**Stage 4 — Power Cook.**
**D-CK2's restructure lands first, alone, with the eat-bread test**, then the effect application
(`player.addStatusEffect` — precedent in `SecondWindListener` / `SmokeBombListener`). The
level→duration math is **MC-free on `CookingManager`**, so it is unit-testable with no world — the
Trophy Hunter `Runnable` lesson.
⚠️ **Power Cook needs an explicit `SkillGating` call too** (D-CK9 item 2) — it fires on a
deterministic condition and no chokepoint covers it.
⚠️ **Do not overwrite a longer effect the player already has.** Eating a steak mid-Strength-potion
must not cut 3:00 down to 15 s. Compare remaining duration and amplifier before applying; a Cooking
effect never replaces something stronger or longer. This is the "does not stack" clause the draft
hand-waved, and here it is one comparison rather than a subsystem.

**Stage 5 — Campfire + the window onto the skill.**
`CampfireBlockEntity#litServerTick` mixin. ⚠️ **The owner is not available where the cook finishes** —
`addItem(ServerWorld, LivingEntity, ItemStack)` has it, `litServerTick` does not. A pos+slot owner map
is needed, mirroring `FURNACE_OWNERS`. Then `CookingStatsRenderer` + the `.Stat` keys, and the
milestone plaques: **re-run `scripts/gen-milestone-advancements.sh`** and add a Cooking icon to its
`ICON` map. ⚠️ The generator is the source of truth for plaque presentation and
`MilestoneAdvancementResourcesTest` asserts both directions — `[[agility-child-skill-restructure]]`
records that the generator once **deleted a skill's json**.

---

## Tests (non-negotiable)

Beyond the checklist's items 20–22:

1. **The eat-bread test.** Eat a Farmer's Diet food, assert the Cooking effect fired *and* the Farmer's
   Diet hunger bonus still applied. Assert off the reference point, not off a food only Cooking claims.
2. **The batch-count test.** Craft 8 cookies in one take; assert XP == 8 × per-item, not 1 ×.
3. **The furnace-does-not-jam test.** Registry-backed: run a smoker through **three consecutive**
   cooks with Master Chef forced on, assert three results. Guards D-CK1 from being reintroduced by
   anyone who "just adds a small tag."
4. **The exploit-cap test.** 2,000 cooks in a simulated hour credits 1,200. ⚠️ **Cooldowns in this
   codebase count *world ticks*, not wall-clock** (`[[husbandry-skill-build]]`).
5. **`SkillLocaleCompletenessTest`** extended, and a `/mcstats cooking` dump diffed against a sibling
   skill's screen.
6. **⚠️ The two disable-switch tests** (D-CK9 item 2). Switch Cooking off in `coreskills.yml`, then
   assert **fuel is not boosted** and **eating a steak grants no effect**. Both fail today's
   `SkillGating` coverage and neither is caught by any existing test — the RNG-proc chokepoint that
   covers Master Chef does not exist on either path.
7. **⚠️ The no-InstantStatusEffect test** (D-CK5a). Walk every effect named in
   `Skills.Cooking.Power_Cook_Effects` and assert none is instant. It is three lines and it stops the
   hunger cannon coming back through a config edit rather than a code edit — the one direction no
   other test in this list covers.
8. **⚠️ The stronger-effect test.** Apply a 3:00 Strength II, eat a steak, assert the potion is
   intact. The Stage-4 clause above, pinned.
9. **Mutation-test every gate.** Break each one, prove the specific test reddens.
   ⚠️ **A mutation that does not redden is not evidence until you have proved it landed where you
   aimed** — nine sections of `advanced.yml` carry an identical `ChanceMax` block and a non-global
   `perl -0pi` silently edits the first (`[[hunter-skill-build]]`). ⚠️ **CRLF kills `perl -0pi` `$`
   anchors** — use line-targeted `if $. == N`.

---

## §G — the play-test rows

Cooking adds a session to `PLAYTEST_G.md`. It cannot be signed off without, at minimum:

- **CK1** Smelt an iron ore and a raw beef in the same furnace. Iron pays **Smelting only**; beef pays
  **Cooking only**. Neither pays both. *(The boundary, measured.)*
- **CK2** Eat bread. Farmer's Diet hunger bonus **and** the Cooking effect both land. *(D-CK2, live.)*
- **CK3** Smoke a full stack of 64 raw beef unattended. **The output is one stack of 64.** *(D-CK1's
  regression, the one that would be reported as a dupe.)*
- **CK4** Fuel a furnace with one coal cooking food vs cooking ore. Burn times differ by exactly the
  configured multiplier and Smelting's is unchanged. *(D-CK3.)*
- **CK5** Steak → Strength for the configured seconds, amplifier **0**. Compare side-by-side with a
  brewed Strength potion and judge whether Alchemy still feels worth levelling. *(D-CK5 — a feel call,
  not a fact call, and it is the user's to make.)*
- **CK6** Build an eight-smoker hopper array behind a chicken farm and AFK one hour. **Measure the XP.**
  If `Max_Cooks_Per_Hour` does not hold the line, the number is wrong — not the design. *(D-CK8, and
  this is the row that decides whether Cooking ships.)*
  ⚠️ **Also record how the dead remainder of the hour FEELS** once the cap bites — the flat-window
  ruling accepts burstiness, and this row is where "a limit" or "looks broken" gets decided.
- **CK7** ⚠️ **Eat a cooked chicken (Speed) and sprint a measured distance; eat a cooked cod
  (Dolphin's Grace) and swim one.** Compare Parkour/Swimming XP against the same run unbuffed. **They
  must be equal** — D5's per-tick speed clamp is what makes them so. If Cooking's buffs beat the
  clamp, Cooking is an Agility XP multiplier and the table loses five rows. *(The cross-skill
  interaction, measured.)*
- **CK8** Eat something with a **full hunger bar and full saturation**. Confirm the effect still
  fires and nothing tops up hunger. *(D-CK4 + D-CK5a, the two hunger rulings, in one bite.)*

---

## Appendix — what the draft got wrong

Kept because these are cheap mistakes to repeat, not to shame the draft.

| # | Defect | Evidence |
|---|---|---|
| 1 | **Item-borne quality jams the furnace** | `canAcceptRecipeOutput` bytecode, offsets 64–75 → D-CK1 |
| 2 | **Wrong platform throughout** — "economy plugins", "the server plugin", "hotkeys exposed by the server", "compatibility with economy plugins", "telemetry" | This is a **Fabric singleplayer mod**. There is no economy, no other player, and no telemetry |
| 3 | **"NBT"** as a storage mechanism | Removed in 1.20.5. It is a component map now |
| 4 | **32 duplicate rows** in `gourmet_mappings` — 76 entries, 44 unique; 22 ids repeated, several three times | `grep -c '^  - item:'` |
| 5 | **11 entries name items with no `FoodComponent`** — `bowl`, `sugar`, `sugar_cane`, `pumpkin_seeds`, `cocoa_beans`, `kelp`, `mushroom`, `pumpkin`, `milk_bucket`, `phantom_membrane`, `cake` | `javap FoodComponents` = 41 fields. The eat seam can never fire for any of them |
| 6 | **7 real foods missing** — `beef`, `chicken`, `cod`, `salmon`, `tropical_fish`, `potato`, `poisonous_potato` | same |
| 7 | **`Teleportation_placeholder`** and **`Remove_Potion_Effects`** listed as potion effects | Neither is a `StatusEffect`. `milk_bucket` has no food component anyway (#5) |
| 8 | **`phantom_membrane` "grants slow falling when consumed via plugin logic (not vanilla)"** | It is not consumable. The note admits the design is inventing an item behaviour, which the draft's own `core_philosophy` forbids |
| 9 | **Flavor Burst duplicates Alchemy** — 30–60 s of any of ten effects, free, forever | D-CK5 |
| 10 | **Kitchen Efficiency / Master Chef duplicate Smelting** without saying so | D-CK3 — legal, but it had to be *stated* |
| 11 | **"Cook's Diet" is the third diet** on an overlapping food set | D-CK4 |
| 12 | **Level rewards to 400** with no mode stated | This port is RetroMode-authored, cap 1000, ×10 (`PLAYTEST_G.md` §0) |
| 13 | **`perfect_timing.window_ticks: 40`** — a QTE on a block entity | There is no UI to show a window and no input to hit it with. Cut silently; noted here so it is not re-proposed |
| 14 | **No anti-farm gate of any kind**, and `bulk_cooking` actively *rewards* volume (`cap_multiplier: 2.0`) | D-CK8. The single largest omission after #1 |
| 15 | `spider_eye` annotated **"Not a food"** | It is one (`FoodComponents.SPIDER_EYE`). Vanilla gives it Poison, which is why it is excluded here — for the opposite reason to the one stated |

### Defects found in the 2026-08-05 refinement pass — in *this* file, not the draft

Kept for the same reason as the table above: a rewrite that fixes fifteen defects can still introduce
its own, and two of these were mine.

| # | Defect | Evidence |
|---|---|---|
| 16 | ⚠️⚠️ **Three rows mapped to Saturation, which fires EVERY TICK** — 3 s of it fills a 20-point hunger bar six times over, from one slice of bread, at rank 1 | `SaturationStatusEffect extends InstantStatusEffect`; `canApplyUpdateEffect` returns `duration >= 1`. Both disassembled. → D-CK5a |
| 17 | ⚠️ **The registration surface was stale.** Written 2026-08-03; `8a5470941` (#10) and `ed5eb2f15` (#9) landed 2026-08-05 and added a `coreskills.yml` completeness test, a `CAT_EXPLOITS` converse test, and a per-skill disable switch that does **not** cover a multiplier or a deterministic effect | grep over `src/test`, and `SkillGating`'s own javadoc → D-CK9 |
| 18 | **`tropical_fish` → Dolphin's Grace** was added by the rewrite as a "missing food" — but it is **raw**, and the same file's own exclusion rule bars raw foods | Cut by D-CK5 clause 5. 🔑 *Adding a row because an item was missing from a list is not the same as it belonging in the list.* |
| 19 | **No cross-skill check against Agility.** Five of 21 rows granted movement buffs to a mod whose newest skill pays XP per unit of movement | → §G row CK7 |
| 20 | **No "don't overwrite a stronger effect" clause.** Eating a steak mid-potion would have cut a 3:00 Strength II to 15 s of Strength I — a Cooking *downgrade* | → Stage 4 |
