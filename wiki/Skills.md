# Skills

**24 skills** — **21 primary** skills that earn XP directly, and **3 child skills** whose level is the average of their parents and which earn no XP of their own.

| Category | Skills |
|---|---|
| **Gathering** | [Mining](#mining), [Woodcutting](#woodcutting), [Herbalism](#herbalism), [Excavation](#excavation), [Fishing](#fishing) |
| **Combat** | [Swords](#swords), [Axes](#axes), [Unarmed](#unarmed), [Archery](#archery), [Crossbows](#crossbows), [Tridents](#tridents), [Maces](#maces), [Spears](#spears), [Taming](#taming) |
| **Movement** | [Parkour](#parkour), [Swimming](#swimming), [Flying](#flying) |
| **Misc** | [Stealth](#stealth), [Unarmored](#unarmored), [Repair](#repair), [Alchemy](#alchemy) |
| **Child** | [Agility](#agility-child), [Salvage](#salvage-child), [Smelting](#smelting-child) |

Your **power level** is the sum of all skill levels.

> ### ⚠️ Read this before the tables
>
> **The eight "Limit Break" sub-skills do nothing.** `ArcheryLimitBreak`, `AxesLimitBreak`, `CrossbowsLimitBreak`, `MacesLimitBreak`, `SpearsLimitBreak`, `SwordsLimitBreak`, `TridentsLimitBreak` and `UnarmedLimitBreak` exist as enum constants, appear on the `/mcstats` screen, and have full rank ladders in `skillranks.yml` and `advanced.yml` — and **not one line of code reads them**. They were dropped during the port. They are listed below marked ❌ so you don't waste time levelling toward them.

---

## Child skills

A child skill has **no save key and earns no XP**. Its level is recomputed from its parents every time your profile loads. You cannot `/addlevels` or `/addxp` one — grant to the parents.

| Child skill | Level is the average of |
|---|---|
| **Agility** | Parkour + Swimming + Flying |
| **Salvage** | Repair + Fishing |
| **Smelting** | Mining + Repair |

Because it's a **mean**, Agility 1000 requires 1000 in all three parents. 1000 Flying on its own is Agility **333**. That's deliberate: Agility's perks are an all-rounder's reward, not a specialist's.

---

# Gathering

## Mining

Earn XP by mining ore and stone. Super ability: **Super Breaker**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Super Breaker | 1 | Speed boost + triple-drop chance. Super ability. |
| Double Drops | 1 | Skilfully mine double the loot. |
| Mother Lode | 1 | Masterfully mine triple the loot. |
| Blast Mining | 8 | Bonuses to mining with TNT. Detonate with flint & steel. |
| Bigger Bombs | 1 | Increases TNT explosion radius. |
| Demolitions Expertise | 1 | Decreases damage taken from TNT explosions. |

## Woodcutting

Earn XP by chopping logs. Super ability: **Tree Feller**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Tree Feller | 1 | Fells a whole tree at once. Super ability. |
| Harvest Lumber | 1 | Skilfully extract up to double the lumber. |
| Clean Cuts | 1 | Masterfully extract up to triple the lumber. |
| Leaf Blower | 1 | Blow away leaves. |
| Knock on Wood | 2 | Find additional goodies when using Tree Feller. |

## Herbalism

Earn XP by harvesting crops and plants. Super ability: **Green Terra**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Green Terra | 1 | Triple drops, boosts Green Thumb. Super ability. |
| Green Thumb | 4 | Auto-replants crops when harvesting with a hoe. |
| Double Drops | 1 | Skilfully harvest double the loot. |
| Verdant Bounty | 1 | Masterfully harvest triple the loot. |
| Farmer's Diet | 5 | Improves hunger restored from farmed foods. |
| Hylian Luck | 1 | Sword-breaking flowers, bushes, saplings and flower pots can turn up rare treasure **instead of** the normal drop. |
| Shroom Thumb | 1 | Spread mycelium to dirt & grass. |

> **Note:** harvesting crops **from horseback pays no XP** (`Skills.Herbalism.Prevent_AFK_Leveling`, shipped on). This is an anti-AFK gate, not a bug.

## Excavation

Earn XP by digging dirt, sand, gravel and clay. Super ability: **Giga Drill Breaker**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Giga Drill Breaker | 1 | 3× drop rate, 3× XP, speed boost. Super ability. |
| Archaeology | 8 | Unearth treasure while digging; higher levels also turn up experience orbs. |

## Fishing

Earn XP by fishing.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Treasure Hunter | 8 | Fish up miscellaneous treasure. |
| Magic Hunter | 1 | Fish up **enchanted** items and enchanted books. |
| Shake | 8 | Shake items off a hooked mob or player with the rod. |
| Master Angler | 8 | Fish bite more frequently; better from a boat. |
| Fisherman's Diet | 5 | Improves hunger restored from fished foods. |
| Ice Fishing | 1 | Fish in icy biomes. |

> Fishing has an **overfishing punishment**: casting repeatedly into the same small patch of water eventually stops paying and warns you. Move the boat.

---

# Combat

Combat XP is paid **per hit**, not per kill. See [XP and Levelling](XP-and-Levelling#combat-xp-is-per-hit).

## Swords

Super ability: **Serrated Strikes**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Serrated Strikes | 1 | AoE damage with a chance to apply Rupture. Super ability. |
| Rupture | 4 | Damage-over-time that ends in an explosion if not reapplied within 5 s. |
| Counter Attack | 1 | Reflect a portion of damage back when attacked. |
| Stab | 2 | Bonus damage on hit. |
| Swords Limit Break | ❌ | **Not implemented.** |

## Axes

Super ability: **Skull Splitter**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Skull Splitter | 1 | AoE damage. Super ability. |
| Critical Strikes | 1 | Double damage. |
| Axe Mastery | 4 | Bonus damage. |
| Armor Impact | 20 | Strike hard enough to damage the target's armour. |
| Greater Impact | 1 | Bonus damage to **unarmoured** foes. |
| Axes Limit Break | ❌ | **Not implemented.** |

## Unarmed

Fighting with an empty hand. Super ability: **Berserk**. (Not to be confused with **[Unarmored](#unarmored)**, which is about wearing no *armour*.)

| Sub-skill | Ranks | Effect |
|---|---|---|
| Berserk | 1 | +50 % damage, breaks weak materials. Super ability. |
| Disarm | 1 | Knock the item out of a foe's hand. |
| Iron Grip | 1 | Prevents *you* from being disarmed. |
| Steel Arm Style | 20 | Hardens your arm over time — flat bonus damage. |
| Arrow Deflect | 1 | Deflect incoming arrows. |
| Block Cracker | 1 | Break rock with your fists. |
| Unarmed Limit Break | ❌ | **Not implemented.** |

## Archery

| Sub-skill | Ranks | Effect |
|---|---|---|
| Skill Shot | 20 | Increases bow damage. |
| Daze | 1 | Disorients foes and deals extra damage. |
| Arrow Retrieval | 1 | Chance to retrieve arrows from corpses. |
| Archery Limit Break | ❌ | **Not implemented.** |

## Crossbows

| Sub-skill | Ranks | Effect |
|---|---|---|
| Powered Shot | 20 | Increases crossbow damage. |
| Trick Shot | 3 | Ricochet arrows off steep angles. |
| Crossbows Limit Break | ❌ | **Not implemented.** |

## Tridents

| Sub-skill | Ranks | Effect |
|---|---|---|
| Impale | 10 | Increases trident damage. |
| Tridents Limit Break | ❌ | **Not implemented.** |

## Maces

| Sub-skill | Ranks | Effect |
|---|---|---|
| Crush | 4 | Bonus damage. |
| Cripple | 4 | Chance to cripple your target. |
| Maces Limit Break | ❌ | **Not implemented.** |

## Spears

| Sub-skill | Ranks | Effect |
|---|---|---|
| Spear Mastery | 8 | Bonus damage. |
| Momentum | 10 | Chance of a short movement-speed burst on attack. |
| Spears Limit Break | ❌ | **Not implemented.** |

> **Spears has no super ability.** The sub-skill depends on a custom item and a `spear` damage type that do not exist in 1.21.11.

## Taming

Earn XP by taming animals and fighting alongside your wolves.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Call of the Wild | 1 | Summon an animal to your side. **Sneak + left-click a block** holding the summon item. |
| Beast Lore | 1 | Whack a wolf or ocelot with a bone to inspect it. |
| Gore | 1 | Critical strike that applies Rupture. |
| Sharpened Claws | 1 | Damage bonus for your wolves. |
| Thick Fur | 1 | Damage reduction + fire resistance. |
| Shock Proof | 1 | Explosive damage reduction. |
| Holy Hound | 1 | Healed by magic & poison instead of harmed. |
| Fast Food Service | 1 | Chance for wolves to heal on attack. |
| Environmentally Aware | 1 | Cactus/lava phobia, fall-damage immunity. |
| Pummel | 1 | Wolves have a chance to knock foes back. |

---

# Movement

Three skills, one per medium you travel through. They exist to feed **[Agility](#agility-child)** and carry almost no perks of their own — the perks all live on Agility.

Movement XP is **speed-normalised**: you're paid per *second* of travel with each tick's distance clamped at the medium's reference speed. Going faster than the reference pays nothing extra. Full explanation on **[Movement Skills](Movement-Skills)**.

## Parkour

Earn XP by **sprinting on land**. Falling, rolling and dodging pay into Parkour too.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Snow Walker | 1 | Cross **powder snow** without sinking into it. Unlocks at Parkour 100 (RetroMode). |

Snow Walker is deliberately parented to Parkour rather than Agility, so you earn it by running and jumping — not by a swimmer and a flier dragging the three-skill average up.

## Swimming

Earn XP by **swimming**. No sub-skills of its own.

## Flying

Earn XP by **elytra gliding**. No sub-skills of its own.

---

# Misc

## Stealth

**New in this port.** Earn XP by sneaking under your own power. Super ability: **Smoke Bomb**. Full page: **[Stealth](Stealth)**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Padfoot | 1 | Sneak almost as fast as you walk. |
| Assassin | 1 | Backstab — far greater damage while sneaking, if you haven't been hit recently. |
| Smoke Bomb | 1 | Vanish. Super ability, triggered by holding **gunpowder** and right-clicking. |

## Unarmored

**New in this port.** Earn XP by taking damage with **every armour slot empty**. Full page: **[Unarmored](Unarmored)**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Iron Skin | 4 | Real armour points while wearing nothing — leather (7) → gold (11) → iron (15) → diamond (20). |
| Thorny Skin | 1 | Reflect a small sting back at melee attackers. |

## Repair

Earn XP by repairing tools and armour on an anvil (an **iron block** by default).

| Sub-skill | Ranks | Effect |
|---|---|---|
| Repair Mastery | 1 | Increased repair amount. |
| Super Repair | 1 | Chance of double effectiveness. |
| Arcane Forging | 8 | Repair magic items **without destroying the enchantments**. |

> ⚠️ **Without an Arcane Forging rank, repairing an enchanted item strips *every* enchantment.** In RetroMode that means below Repair 100. This is faithful to upstream mcMMO and is not a bug — but it will absolutely eat your good pickaxe if you don't know about it.

Material tiers (stone/gold/iron/diamond repair) unlock at increasing Repair levels.

## Alchemy

Earn XP by brewing potions.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Catalysis | 1 | Increases brewing speed — up to **4× faster** at max level. |
| Concoctions | 8 | Brew potions with more ingredients. |

> **Concoctions tier gating is not implemented.** The custom-ingredient tree in `potions.yml` loads, but the level gate that should restrict which tier you can brew has no seam in 1.21.11 to hook.

---

# Child skills

## Agility (child)

Level = mean of **Parkour + Swimming + Flying**. Earns no XP itself, but owns **ten sub-skills** — the largest sub-skill set in the mod. Super ability: **Second Wind**.

Full page: **[Movement Skills](Movement-Skills)**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Dodge | 1 | Halve incoming attack damage. |
| Roll | 1 | Land strategically to avoid fall damage (Graceful Roll is twice as effective). |
| Fleet Footed | 3 | Move faster through whatever you're travelling through. Rank 1 = land, 2 = water, 3 = air. |
| Athlete | 1 | Sprinting costs less hunger. |
| Smash | 1 | Sprint attacks hit harder and send targets flying. |
| Lead Lungs | 1 | Hold your breath far longer underwater. |
| Glide | 1 | Descend more slowly while gliding. |
| Lake Raider | 1 | Underwater digging turns up treasure. |
| Solar Wings | 1 | A worn elytra slowly mends in daylight. |
| Second Wind | 3 | Lunge, surge or soar. Super ability, triggered by holding a **feather** and right-clicking. Rank 1 = land, 2 = water, 3 = air. |

## Salvage (child)

Level = mean of **Repair + Fishing**. Salvage items back into their materials at a **gold block**.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Scrap Collector | 8 | Recover materials from an item; a perfect salvage depends on skill and luck. |
| Arcane Salvage | 8 | Extract enchantments from items into books. |

## Smelting (child)

Level = mean of **Mining + Repair**. Applies while smelting in a furnace.

| Sub-skill | Ranks | Effect |
|---|---|---|
| Second Smelt | 1 | Double the resources gained from smelting. |
| Fuel Efficiency | 3 | Increases the burn time of furnace fuel. |
| Understanding the Art | 8 | Boosts the vanilla XP a furnace drops. |
