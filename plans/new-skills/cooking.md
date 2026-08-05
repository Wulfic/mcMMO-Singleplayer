# Cooking — Plan

**Read [00-OVERVIEW.md](../00-OVERVIEW.md) first.** Cooking is a **standalone primary skill** (D1 shape)
and belongs in **`MISC_SKILLS`** — it is a *processing* skill, and every other processing skill in the
mod (`SMELTING`, `REPAIR`, `SALVAGE`, `ALCHEMY`) is already there. It needs **no F1** (tick sampler)
and **no F2** (attribute service): every one of its verbs is a discrete event.

> 🔴 **STATUS: PLAN ONLY. NO CODE.** Nothing named Cooking exists in `src/` (verified — the only hits
> are three comments in `SmeltingListener` / `SmeltingManager` explaining why food is *excluded* from
> Smelting).
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

> ✅ **RECOMMENDED — needs a ruling: quality is rolled at EAT time, from the eater's Cooking level.
> The item carries nothing.** No component write, no persistence, no stacking bug, no jammed furnace,
> and it satisfies the draft's own `core_philosophy` ("no reinterpretation of existing items") better
> than the NBT design did.
>
> **The cost, stated honestly:** you cannot gift or trade a Gourmet steak, and a cooked item is not a
> better item — the *cook* is not what pays, the *cook's level* is. **In singleplayer there is exactly
> one player, so this cost is zero.** It would matter on a server; this mod does not have one.
>
> ❌ **Rejected: stamp on the way out of the output slot** (`FurnaceOutputSlot#onCrafted`, so the
> furnace never sees the marked stack). It dodges the jam but keeps the inventory split — three
> unmergeable piles of steak is a worse bug than no feature, and it would be reported as a dupe.

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

> ✅ **RECOMMENDED: CUT.** Cooking's payoff is the **effect**, not the hunger. Ship no `.Diet`
> sub-skill and no `+nutrition` at all.
>
> **If you want it anyway**, the only defensible version covers the foods **neither** existing diet
> claims — `cooked_beef`, `cooked_porkchop`, `cooked_chicken`, `cooked_mutton`, `cooked_rabbit`,
> `rabbit_stew`, `beetroot_soup`, `suspicious_stew`, `dried_kelp`, `honey_bottle`, `sweet_berries`,
> `apple`, `chorus_fruit`, `tropical_fish`, `pufferfish`, `rotten_flesh`, `spider_eye`,
> `poisonous_potato` (18 of the 41). That set is real and non-overlapping — but it makes Cooking a
> hunger skill, which is the least interesting thing it could be, and it collides head-on with
> **Gourmet Meal** below. Pick one, never both.

### D-CK5 — The effect budget, or Cooking deletes Alchemy

The draft's **Flavor Burst** grants 30–60 s of a **randomly chosen** effect from a pool of ten —
Strength, Haste, Speed, Regeneration, Resistance, Fire Resistance, Water Breathing, Night Vision,
Luck, Jump — on a 5-minute cooldown, forever, for free. **That is a splash potion of anything, and
Alchemy is the skill whose entire job is making those.** It also fails the interesting half of the
wiki idea, which was that *the food you chose* is the effect you get.

The wiki itself was far more conservative than the draft: *"potion effects only last for **2 seconds**"*
at level 100, scaling to 1000.

> ✅ **RECOMMENDED budget for Power Cook, all four clauses:**
>
> 1. **The effect is determined by the food. Never random.** Steak is Strength. Baked potato is Haste.
>    Golden carrot is Night Vision. That is the mechanic; the randomness was noise on top of it.
> 2. **Amplifier is always 0.** No Strength II from a sandwich.
> 3. **Duration is measured in seconds and scales with rank: 3 s at rank 1 → 15 s at rank 5.** The
>    wiki's shape, one notch more generous. A brewed Strength potion is 3:00 at amplifier 1 — a
>    factor of **twelve**, before the amplifier. Alchemy stays worth levelling.
> 4. **No effect that trivialises a hazard for longer than it takes to cross it.** Fire Resistance and
>    Water Breathing are **excluded from the table entirely**; 15 s of either is a lava-lake shortcut
>    and a monument shortcut, and both are Alchemy's to sell.
>
> ⚠️ Vanilla already grants effects from `golden_apple`, `enchanted_golden_apple`, `golden_carrot`,
> `chorus_fruit`, `suspicious_stew`, `poisonous_potato`, `pufferfish`, `rotten_flesh` and
> `spider_eye`. Cooking **must not touch those nine** — stacking our effect on top of vanilla's is
> the "does not stack, extend duration instead" clause the draft hand-waved, and it is not worth the
> code. They are simply absent from the table.

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

> ✅ **RECOMMENDED, and it ships in Stage 2 with the XP — not reserved as a backstop: a rolling
> credited-cook cap.** `experience.yml → ExploitFix.Cooking.Max_Cooks_Per_Hour`, default **1,200**
> (two continuously-running smokers). Cooks beyond it still cook; they simply pay nothing.
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

---

## The sub-skill roster

Four. Cooking is a small skill and padding it is how the port ended up with 13 dead enums on
`/mcstats`.

| `SubSkillType` | Ranks | What it does | Seam |
|---|---|---|---|
| `COOKING_POWER_COOK` | 5 | Eating a cooked food grants its mapped effect, amplifier 0, 3 s → 15 s by rank | eat |
| `COOKING_MASTER_CHEF` | 5 | Chance at one bonus copy of a finished cook | `setLastRecipe` |
| `COOKING_KITCHEN_EFFICIENCY` | 3 | Fuel burns longer when the input is a food | `getFuelTime` |
| `COOKING_GOURMET_MEAL` | 5 | Chance on eating that the meal is *Gourmet*: extra **saturation** (never hunger — D-CK4) and **double** Power Cook duration | eat |

**Cut, with the reason recorded so it is not re-litigated:** Flavor Burst (D-CK5/D-CK6), Cook's Diet
(D-CK4), Precision Cooking + Meal Memory (both were plumbing for the dead item-quality tier, D-CK1),
Butchery and Holy Cook (D-CK7).

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
| `GourmetMeal` | 350 / 550 / 700 / 850 / 1000 | 35 / 55 / 70 / 85 / 100 |

Power Cook's rank 1 at **100** is deliberate and it is the wiki's own number.

---

## Power Cook — the food → effect table

⚠️ **This table is a STARTING POINT and Stage 0 must verify it against the live registry.** Use the
Hunter calibration method (`[[hunter-skill-build]]`): a throwaway JUnit test that walks
`Registries.ITEM`, filters on `DataComponentTypes.FOOD`, and dumps the set to TSV. **Do not author a
food list from memory** — the draft did, and 11 of its 44 entries were items with no food component
at all.

`javap net.minecraft.component.type.FoodComponents` reports **41** foods in 1.21.11. The nine that
already carry a vanilla effect are excluded (D-CK5), as are pure ingredients with no sensible mapping.

| Food | Effect | Note |
|---|---|---|
| `cooked_beef` | Strength | the wiki's own example: *"whip out a steak, get 2 strong hits in"* |
| `cooked_porkchop` | Resistance | |
| `cooked_mutton` | Resistance | |
| `cooked_chicken` | Speed | |
| `cooked_rabbit` | Jump Boost | |
| `cooked_cod` | Dolphin's Grace | ⚠️ **not** Water Breathing — D-CK5 clause 4 |
| `cooked_salmon` | Dolphin's Grace | |
| `baked_potato` | Haste | the wiki's own example |
| `bread` | Saturation | |
| `cookie` | Speed | |
| `pumpkin_pie` | Regeneration | |
| `mushroom_stew` | Regeneration | |
| `beetroot_soup` | Regeneration | |
| `rabbit_stew` | Jump Boost | |
| `dried_kelp` | Haste | |
| `honey_bottle` | Regeneration | vanilla only *clears* poison; no duration to stack with |
| `sweet_berries` | Speed | |
| `glow_berries` | Glowing | thematic, harmless, and not Alchemy's |
| `melon_slice` | Saturation | |
| `apple` | Saturation | |
| `tropical_fish` | Dolphin's Grace | ⚠️ absent from the draft entirely |

**Deliberately absent:** `golden_apple`, `enchanted_golden_apple`, `golden_carrot`, `chorus_fruit`,
`suspicious_stew`, `poisonous_potato`, `pufferfish`, `rotten_flesh`, `spider_eye` (vanilla already
grants effects — D-CK5); `beef`, `chicken`, `cod`, `salmon`, `mutton`, `porkchop`, `rabbit`, `potato`,
`carrot`, `beetroot` (**raw** — cooking them is the point, and paying for eating them raw inverts the
skill).

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
| `skillranks.yml` | the four sub-skill ladders above |
| `advanced.yml` | `Skills.Cooking.MasterChef` (`ChanceMax` / `MaxBonusLevel`), `.GourmetMeal`, `.PowerCook` (`Seconds_Per_Rank`), `.KitchenEfficiency` (`Multiplier_Per_Rank`) |
| `config.yml` | `Skills.Cooking.Level_Cap: 0`; `Skills.Cooking.Power_Cook_Effects`; `Bonus_Drops.Cooking` (the food result list Master Chef reads — **results, not inputs**, D-CK3) |
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
before D-CK2's restructure, not after. **No production code.** Delete the test when done.

**Stage 1 — Registration.**
`PrimarySkillType.COOKING`, the four `SubSkillType`s, `CookingManager extends SkillManager`
(**MC-free**), `McMMOPlayer.initManager` + typed getter, `SkillTools.MISC_SKILLS`, all five config
files, the locale block, `SkillLocaleCompletenessTest`, the ModMenu key-validation registration, and
the **old-profile regression test** (checklist item 19). Nothing fires yet. `/mcstats` renders the
skill generically.

**Stage 2 — Cook XP + the exploit cap.**
The food branch of `SmeltingListener.onFurnaceSmelt`; `CraftingResultSlotMixin` for the crafted foods
(⚠️ **× the batch count**); `Max_Cooks_Per_Hour`. Campfires are **not** in this stage — furnace,
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

**Stage 4 — Power Cook + Gourmet Meal.**
**D-CK2's restructure lands first, alone, with the eat-bread test**, then the effect application
(`player.addStatusEffect` — precedent in `SecondWindListener` / `SmokeBombListener`). The
level→duration math and the Gourmet roll are **MC-free on `CookingManager`** with the RNG draw passed
in, so both are unit-testable with no world — the Trophy Hunter `Runnable` lesson.

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
6. **Mutation-test every gate.** Break each one, prove the specific test reddens.
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
