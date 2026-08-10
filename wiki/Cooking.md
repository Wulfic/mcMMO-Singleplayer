# Cooking

**New in this port** — Cooking does not exist in upstream mcMMO.

**How you train it:** cook food in a furnace, smoker, blast furnace or campfire, and craft food at a bench.

> ⚠️ **Code-complete, barely play-tested.** Every number on this page is a starting estimate. The hourly cap in particular is the one knob most likely to move — see [the cap](#the-hourly-cook-cap).

---

## The idea

Cooking is **Smelting's other half**. The two skills share the furnace and split it by **input**:

| Input | Pays |
|---|---|
| Ore | [Smelting](Skills#smelting-child) |
| Food | **Cooking** |

Never both. The listener checks Smelting first and Cooking is the `else`, which is the same order the fuel-bonus gate has enforced since the Smelting port — its original comment reads *"so cooking food burns at vanilla speed"*, and [Kitchen Efficiency](#kitchen-efficiency--3-ranks) is literally that comment's `else`.

The two are not going to be merged, and neither side should be widened to "unify" them.

---

## Earning XP

`experience.yml` → `Experience_Values.Cooking`. There are **two subsections and they are not interchangeable.**

### `Cook` — keyed by what went *into* the heat

| Input | XP |
|---|---|
| Beef, Porkchop, Chicken, Mutton, Rabbit, Cod, Salmon | **100** each |
| Potato | **60** |
| Kelp | **60** |
| Chorus Fruit | **0** |

These nine are every food input vanilla has, derived from the shipped recipe JSONs rather than written from memory.

**Kelp is not itself a food** — it has no food component at all; dried kelp does. It is on this list because the section is keyed on the *input*, and a table written by filtering "which foods can be cooked" would drop dried kelp from the skill entirely.

**Chorus fruit is explicitly 0, not merely absent.** It *is* a furnace food input, but it smelts into popped chorus fruit, which is not food and is not cooking — and a chorus farm is fully automatable.

### `Craft` — keyed by what came *out* of the crafting grid

Priced **per item**, then multiplied by the batch. One take of the cookie recipe is 8 items.

| Result | XP per item | Note |
|---|---|---|
| Cake | **300** | 3 milk + 2 sugar + 1 egg + 3 wheat, and it yields one |
| Pumpkin Pie | **200** | |
| Mushroom Stew / Beetroot Soup / Rabbit Stew | **150** | |
| Bread | **80** | |
| Cookie | **10** | ×8 per craft, so 80 for the recipe |
| Dried Kelp | **0** | see below |
| Honey Bottle | **0** | see below |
| Golden Apple / Golden Carrot | **0** | 8 gold ingots. A gold farm is not a cooking skill. |
| Suspicious Stew | **0** | its effects live on their own component; Cooking stays out of it |

### ⚠️ Why the split exists: dried kelp

**Smoking a kelp is a real cook worth 60. Crafting a dried kelp is not worth anything, ever.**

Nine dried kelp craft into a dried kelp block, and the block crafts straight back into nine dried kelp, consuming nothing at all. Priced per item, that is **infinite XP at the speed of a crafting grid** — no ingredient, no fuel, no farm. Honey bottles round-trip through a honey block exactly the same way.

Both are therefore `0` under `Craft` **and only under `Craft`**. One flat section could not have said that: the same item, two verbs, two prices.

---

## The hourly cook cap

`experience.yml` → `ExploitFix.Cooking.Max_Cooks_Per_Hour`, default **1200**.

> ### ⚠️ This is the only anti-farm gate Cooking has.

Every other farmable skill has something concrete to check. [Hunter](Hunter#the-spawn-origin-gate) has four separate gates. Cooking has none of them, for one reason: **an item has no spawn origin.** A cooked steak is a cooked steak whether you caught the cow yourself or a hopper fed it to a smoker while you slept. The furnace-owner map is filled by a single right-click and held for the whole session, so that array keeps paying its owner the entire time.

Where 1200 comes from — vanilla's own cook times are **100 ticks smoking, 200 smelting, 600 campfire**:

| Setup | Items/hour, unattended |
|---|---|
| One smoker | 720 |
| **Two smokers** | **1,440 — roughly the cap** |
| Eight smokers | 5,760 |

So it does not bite anyone cooking by hand or running a normal kitchen. It bites the array that runs forever.

**It counts items, not events, and that is load-bearing.** XP is priced per item and multiplied by the batch, so a cap counting crafting *actions* would let one shift-click of 64 cookies spend a single unit of the budget while paying 64 items' worth of XP — a 64× hole in the one gate the skill has.

Other things worth knowing:

- **Cooks past the cap still cook.** They just pay nothing.
- **You are told, once per hour**, the moment the cap starts biting: *"You have cooked more than your kitchen can keep books on this hour."* A gate that silently pays nothing is indistinguishable from a broken one — and one that says so on every item of a 64-cookie craft is worse.
- **A batch that straddles the boundary is credited in part**, not refused whole. Refusing a 9-item craft because 3 units of budget remain would make the cap's bite depend on batch size.
- **The window is fixed, not sliding.** It opens on the first award after the last one expired and runs a flat hour. At a boundary you can collect up to two windows' worth in quick succession; the burst is twice the cap, the sustained rate is exactly the cap.
- ⚠️ **The known cost of that shape is burstiness.** A stack of raw beef through eight smokers spends a large slice of the hour in minutes, and you then earn nothing for the rest of it.
- Set it to **0** to disable the cap entirely.

`Diminished_Returns.Threshold.Cooking` (20,000) is the second gate, and unlike most skills' entry it is reachable — see [XP and Levelling](XP-and-Levelling#anti-exploit-gates).

---

## Sub-skills

Three passives, no super ability. Unlock levels below are **RetroMode** (the default); divide by ten for Standard.

### Kitchen Efficiency — 3 ranks

Fuel burns longer when the furnace's input is a **food**.

| Rank | Unlocks at | Burn time |
|---|---|---|
| 1 | 250 | **×2** |
| 2 | 500 | **×3** |
| 3 | 850 | **×4** |

Mirrors Smelting's Fuel Efficiency ladder exactly, because it is the same mechanic on the other side of a gate that already existed. **No one can hold both bonuses on one smelt** — an input is either an ore or a food.

### Master Chef — 5 ranks

Chance to pull a **second helping** out of a finished cook, straight into the output slot.

| Rank | Unlocks at |
|---|---|
| 1 | 50 |
| 2 | 200 |
| 3 | 400 |
| 4 | 650 |
| 5 | 900 |

The chance scales with your level to a maximum of **33%** at Cooking 1000 (`advanced.yml` → `Skills.Cooking.MasterChef.ChanceMax`).

That is deliberately **below Smelting's Second Smelt (50%)**: a furnace's ore throughput is gated by having to mine the ore, while a smoker's food throughput is gated by nothing but chickens and five seconds an item, so the same chance is worth appreciably more here.

Which results can roll is a list, not a roll — `config.yml` → `Bonus_Drops.Cooking`, which is exactly the nine results of the nine paid `Cook` inputs. An item listed in both this section and `Bonus_Drops.Smelting` pays **Smelting**; no vanilla item is in both today.

> **Tuning note:** dried kelp is the one entry with a second-order effect. Dried kelp blocks are fuel, so doubling a kelp smoker's output feeds the furnace that produced it. Vanilla's kelp-to-fuel loop is strongly fuel-*negative*, and Master Chef plus Kitchen Efficiency together can push it just past break-even. That is a slow trickle rather than a dupe — but it is the row to switch off first if a kelp farm ever looks like free fuel.

### Power Cook — 5 ranks

Cooked and crafted food carries a **lingering status effect** when you eat it.

| Rank | Unlocks at | Duration |
|---|---|---|
| 1 | 100 | **3 s** |
| 2 | 250 | **6 s** |
| 3 | 450 | **9 s** |
| 4 | 700 | **12 s** |
| 5 | 1000 | **15 s** |

**The amplifier is always 0 and is not configurable.** No Strength II from a sandwich. A brewed Strength potion is 3:00 at amplifier 1 — twelve times longer before the amplifier is even counted — and that gap is the entire reason Cooking does not delete [Alchemy](Skills#alchemy).

The table, from `config.yml` → `Skills.Cooking.Power_Cook_Effects`:

| Food | Effect |
|---|---|
| Cooked Beef | Strength |
| Cooked Porkchop, Cooked Mutton | Resistance |
| Cooked Chicken, Bread, Cookie | Speed |
| Cooked Rabbit, Rabbit Stew | Jump Boost |
| Cooked Cod, Cooked Salmon | Dolphin's Grace |
| Baked Potato, Dried Kelp | Haste |
| Pumpkin Pie, Mushroom Stew, Beetroot Soup | Regeneration |

Four things this table is careful about:

- **It is keyed on the item, not on who cooked it.** An item carries no record of its own provenance, so a steak someone else smoked grants the same effect. The sub-skill's description is flavour; the mechanic is the food.
- **Cooked and crafted foods only.** An apple you picked off a tree grants nothing, and foods that already carry a vanilla effect (golden apple, pufferfish, rotten flesh, spider eye, poisonous potato, chorus fruit, honey bottle, raw chicken) are deliberately absent.
- ⚠️ **Fire Resistance and Water Breathing are banned outright**, at any duration. Fifteen seconds of either is a lava-lake shortcut and a monument shortcut, and both are Alchemy's to sell. Cooked fish grant **Dolphin's Grace** for exactly that reason.
- ⚠️ **No effect that fires every tick may ever be mapped here.** Saturation, Instant Health, Instant Damage, Hunger, Absorption, Bad Omen and Raid Omen all apply once *per tick* for their whole duration, so 3 seconds of Saturation is +60 food onto a 20-point bar — one slice of bread would end hunger as a resource forever. Bread was Saturation in an earlier draft; it is the traveller's loaf now. An effect's *name* does not tell you its cadence, and a test walks every row of this section and asserts it.

**Eating never downgrades a stronger effect you already have.** A steak in the middle of a 3:00 Strength II leaves the potion alone — that is vanilla's own rule, delegated to rather than reimplemented.

Delete a row to disable it. An unknown effect name disables that row and logs once.

---

## Campfires

A campfire cook pays exactly like a furnace cook. Right-click the campfire to become its owner; the XP lands when the result is due.

**A campfire is the one cooking block in the game that cannot be automated.** It is not an inventory — no hopper, no dropper and no dispenser can put food on one. The only route in is a player's own right-click, so the ownership map that a furnace array can exploit has nothing to exploit here.

Ownership is keyed on the block position **and the dimension**, so a campfire at the same coordinates in the Nether does not award the overworld's owner. The map is cleared between world sessions.

---

## `/mcstats cooking`

Shows your level and XP, then the live value of whichever passives you have unlocked:

- **Fuel Efficiency Multiplier** — the real multiplier at your rank, read off the manager rather than re-derived, so the screen and the mechanic can never disagree
- **Second Helping Chance** — your actual chance right now, not the config's maximum
- **Effect Duration** — Power Cook seconds at your rank
- **Hourly Cook Limit** — rendered **only when the cap is on**. A line reading "0 per hour" would be worse than no line.

---

## Not shipped

- **No super ability.** Cooking is a processing skill; nothing about it wants a timed burst.
- **No item-borne quality tier.** A "perfectly cooked steak" variant was designed and dropped: an item that differs from its vanilla counterpart does not stack with it, which jams the furnace's own output slot the moment a normal steak lands next to a special one.
- **Nothing unlocks between 1000 and the cap.** Power Cook rank 5 also sits at 1000, so the last stretch of the skill is flat. That is a known property of a three-sub-skill processing skill (Salvage is no denser) and no filler is being added to hide it.

---

## Tuning

| Where | What |
|---|---|
| `experience.yml` → `Experience_Values.Cooking.Cook` | XP per furnace/smoker/campfire input |
| `experience.yml` → `Experience_Values.Cooking.Craft` | XP per crafted item — ⚠️ keep the four `0` rows at 0 |
| `experience.yml` → `ExploitFix.Cooking.Max_Cooks_Per_Hour` | The anti-farm cap; 0 disables |
| `advanced.yml` → `Skills.Cooking.MasterChef.ChanceMax` | Second-helping chance ceiling |
| `advanced.yml` → `Skills.Cooking.PowerCook.Seconds_Per_Rank` | Effect duration ladder |
| `advanced.yml` → `Skills.Cooking.KitchenEfficiency.Multiplier_Per_Rank` | Burn-time ladder |
| `skillranks.yml` → `Cooking` | Unlock levels, both ladders |
| `config.yml` → `Skills.Cooking.Power_Cook_Effects` | Which food grants which effect |
| `config.yml` → `Bonus_Drops.Cooking` | Which results Master Chef may double |
| `config.yml` → `Skills.Cooking.Level_Cap` | 0 = no limit |
| `coreskills.yml` → `Cooking.Enabled` | Turn the whole skill off |
