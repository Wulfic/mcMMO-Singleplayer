# Cooking Skill Design Goals

## Core Philosophy

- No new blocks
- No new items or tools
- No reinterpretation of existing items
- No resource pack
- No custom effects
- No custom models
- No custom GUIs
- Only vanilla food + vanilla potion effects
- mcMMO-style passives, abilities, procs, XP

This skill is purely gameplay logic layered on top of vanilla Minecraft.

---

# XP System (Tool-Free)

## XP Sources

XP is awarded whenever the player successfully cooks or crafts food.

### Basic Cooking
- Cooked Beef
- Cooked Porkchop
- Cooked Chicken
- Cooked Mutton
- Cooked Rabbit
- Cooked Cod
- Cooked Salmon
- Baked Potato

### Baking
- Bread
- Cookie
- Cake
- Pumpkin Pie

### Stews & Soups
- Mushroom Stew
- Rabbit Stew
- Beetroot Soup
- Suspicious Stew

### Complex Meals
Recipes requiring multiple ingredients grant additional XP.

### Bulk Cooking Bonus
Cooking many items in one furnace/smoker session grants increasing XP bonuses.

### Perfect Timing Bonus
Removing food within a short window after it finishes cooking grants:

- Bonus XP
- Increased chance of producing higher-quality meals

---

# Meal Quality System (NBT Only)

Every cooked item has a chance to receive one of three quality levels.

## Normal
Default food.

## Great
Slight improvements:

- +1 Hunger (where applicable)
- +20% Saturation
- Slightly longer buff durations

## Gourmet
Rare result.

Benefits:

- +2 Hunger
- +40% Saturation
- Longest potion durations
- Increased sell value (economy plugins)
- Counts toward Cooking achievements/statistics

Quality is stored entirely through NBT tags.

No custom items are added.

---

# Abilities

## Precision Cooking

Passive

Chance for cooked food to become:

- Great
- Gourmet

Chance scales with Cooking level.

---

## Flavor Burst

Active Ability

Duration:
30–60 seconds.

While active, every qualifying meal cooked gains an additional vanilla potion effect.

Possible effects include:

- Strength I
- Haste I
- Speed I
- Regeneration I
- Resistance I
- Fire Resistance
- Water Breathing
- Night Vision
- Luck I

Higher Cooking levels increase effect duration.

---

## Master Chef

Passive

Chance to:

- Double cooked output
- Automatically upgrade meal quality
- Increase Gourmet chance

---

## Kitchen Efficiency

Passive

Improves cooking stations.

Examples:

- Furnace fuel lasts longer
- Smoker cooks faster
- Campfires cook faster
- Reduced fuel consumption

Implemented entirely through gameplay logic.

---

## Meal Memory

Passive

Players gradually master recipes they cook frequently.

Mastered recipes gain:

- Increased XP
- Higher Great chance
- Slightly increased Gourmet chance

Encourages specializing in favorite foods instead of random grinding.

---

# Gourmet Meal Ideas

These are created entirely from vanilla ingredients and represented with NBT—no custom items.

## 1. Gourmet Steak
Ingredients:
- Cooked Beef

Effect:
- Strength I

---

## 2. Herb-Crusted Steak
Ingredients:
- Cooked Beef
- Dried Kelp

Effect:
- Regeneration I

---

## 3. Smoked Pork Feast
Ingredients:
- Cooked Porkchop

Effect:
- Resistance I

---

## 4. Honey Glazed Chicken
Ingredients:
- Cooked Chicken
- Honey Bottle

Effect:
- Speed I

---

## 5. Shepherd's Mutton
Ingredients:
- Cooked Mutton
- Potato

Effect:
- Resistance I

---

## 6. Fisherman's Salmon
Ingredients:
- Cooked Salmon
- Dried Kelp

Effect:
- Water Breathing

---

## 7. Crispy Cod Basket
Ingredients:
- Cooked Cod

Effect:
- Luck I

---

## 8. Rabbit Hunter's Stew
Ingredients:
- Rabbit Stew

Effect:
- Jump Boost I

---

## 9. Woodland Mushroom Stew
Ingredients:
- Mushroom Stew

Effect:
- Night Vision

---

## 10. Farmer's Beet Soup
Ingredients:
- Beetroot Soup

Effect:
- Regeneration I

---

## 11. Golden Baked Potato
Ingredients:
- Baked Potato

Effect:
- Haste I

---

## 12. Village Bread Loaf
Ingredients:
- Bread

Effect:
- Saturation boost

---

## 13. Sweet Berry Pie
Ingredients:
- Pumpkin Pie
- Sweet Berries

Effect:
- Speed I

---

## 14. Royal Pumpkin Pie
Ingredients:
- Pumpkin Pie

Effect:
- Luck I

---

## 15. Chocolate Cookie Stack
Ingredients:
- Cookie

Effect:
- Speed I

---

## 16. Celebration Cake Slice
Ingredients:
- Cake

Effect:
- Regeneration I

---

## 17. Ocean Explorer's Meal
Ingredients:
- Cooked Salmon
- Cooked Cod

Effect:
- Water Breathing
- Night Vision

---

## 18. Nether Fire Chili
Ingredients:
- Cooked Beef
- Nether Wart
- Blaze Powder

Effect:
- Fire Resistance
- Strength I

---

## 19. End Expedition Meal
Ingredients:
- Chorus Fruit
- Cooked Chicken

Effect:
- Slow Falling
- Night Vision

---

## 20. Miner's Lunch
Ingredients:
- Baked Potato
- Cooked Beef

Effect:
- Haste I
- Resistance I

---

## 21. Adventurer's Breakfast
Ingredients:
- Bread
- Cooked Porkchop

Effect:
- Speed I
- Saturation bonus

---

## 22. Ranger's Trail Meal
Ingredients:
- Cooked Rabbit
- Carrot

Effect:
- Jump Boost I
- Speed I

---

## 23. Deep Sea Dinner
Ingredients:
- Cooked Salmon
- Kelp

Effect:
- Water Breathing
- Luck I

---

## 24. Warrior's Feast
Ingredients:
- Steak
- Baked Potato

Effect:
- Strength I
- Resistance I

---

## 25. Feast of Champions
Ingredients:
- Rabbit Stew
- Pumpkin Pie

Effect:
- Strength I
- Regeneration I
- Luck I

---

# Level Rewards

| Level | Reward |
|-------:|--------|
| 10 | Precision Cooking I |
| 25 | Kitchen Efficiency I |
| 50 | Flavor Burst I |
| 75 | Great Quality Unlock Improvements |
| 100 | Master Chef I |
| 150 | Flavor Burst II |
| 200 | Kitchen Efficiency II |
| 250 | Increased Gourmet Chance |
| 300 | Master Chef II |
| 400 | Maximum Meal Quality Bonuses |