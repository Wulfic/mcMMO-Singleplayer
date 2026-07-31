# Cooking Skill — Full Vanilla Food Mapping (Minecraft up to 1.21.x)
# Paste into your skill config or use as a design template.
# Notes:
# - Uses only existing vanilla items (no new recipes/items).
# - Each food maps to possible vanilla potion effects and quality modifiers.
# - Quality and temporary effect payloads stored via NBT only.
# - Adjust numeric values in config for balancing.

skill:
  id: cooking_vanilla_full_1_21
  name: "Cooking (Vanilla Full 1.21.x)"
  description: >
    Vanilla-only cooking skill mapping for all food items present in Minecraft up to 1.21.x.
    No new items, blocks, GUIs, models, or resource packs. All bonuses use vanilla potion effects
    and NBT metadata only.

core_philosophy:
  - "No new blocks"
  - "No new items or tools"
  - "No reinterpretation of existing items"
  - "No resource pack"
  - "No custom effects"
  - "No custom models"
  - "No custom GUIs"
  - "Only vanilla food + vanilla potion effects"
  - "mcMMO-style passives, abilities, procs, XP"

# Global toggles
feature_toggles:
  allow_custom_recipes: false
  gourmet_as_virtual_recipes: false
  use_existing_items_only: true

# XP system (conservative defaults)
xp_system:
  award_on:
    - "successful_cook"
    - "craft_food"
  xp_values:
    raw_food_cook: 5
    baked_bread: 6
    cake_cookie_pie: 6
    stew_soup: 8
    complex_meal_bonus: 4
  bulk_cooking:
    enabled: true
    base_multiplier: 1.0
    per_item_increment: 0.04
    cap_multiplier: 2.0
  perfect_timing:
    enabled: true
    window_ticks: 40
    xp_bonus: 8
    quality_increase_chance: 0.05

meal_quality:
  storage: "NBT"
  tiers:
    - name: Normal
      modifiers: {}
    - name: Great
      modifiers:
        hunger_bonus: 1
        saturation_multiplier: 1.20
        potion_duration_multiplier: 1.10
    - name: Gourmet
      modifiers:
        hunger_bonus: 2
        saturation_multiplier: 1.40
        potion_duration_multiplier: 1.25
        economy_value_multiplier: 1.25
  assignment:
    - source: "precision_roll"
    - source: "master_chef_proc"

abilities:
  precision_cooking:
    type: passive
    base_chance:
      great: 0.05
      gourmet: 0.005
    scaling:
      per_level_increase: 0.0006
    caps:
      great: 0.50
      gourmet: 0.12

  flavor_burst:
    type: active
    cooldown_seconds: 300
    duration_seconds_by_rank:
      I: 30
      II: 60
    effect_pool:
      - Strength
      - Haste
      - Speed
      - Regeneration
      - Resistance
      - Fire_Resistance
      - Water_Breathing
      - Night_Vision
      - Luck
      - Jump
    selection: "random_from_pool"
    scaling: "higher_levels_increase_effect_duration_and_chance"

  master_chef:
    type: passive
    procs:
      double_output_chance_base: 0.02
      auto_upgrade_chance_base: 0.01
      gourmet_increase_base: 0.005
    scaling_per_level: 0.00045

  kitchen_efficiency:
    type: passive
    effects:
      furnace:
        fuel_efficiency_multiplier: 1.12
        cook_speed_multiplier: 1.00
      smoker:
        cook_speed_multiplier: 1.20
      campfire:
        cook_speed_multiplier: 1.10
      fuel_consumption_reduction: 0.10

  meal_memory:
    type: passive
    mastery_gain:
      per_cook: 0.02
      max_mastery: 1.0
    mastery_bonuses:
      xp_multiplier_at_max: 1.25
      great_chance_bonus_at_max: 0.10
      gourmet_chance_bonus_at_max: 0.02

# Mapping: every vanilla food item (up to 1.21.x) -> possible effects and notes
# Each mapping line: item id, base_effect_pool, base_effect_chance, quality_bias, notes
# Effect chance is the chance that consuming a cooked/quality item grants one mapped effect (unless overridden by Flavor Burst)
gourmet_mappings:
  - item: "apple"
    effects: ["Saturation"]
    base_effect_chance: 0.05
    quality_bias:
      Great: 0.02
      Gourmet: 0.01
    notes: "Simple snack; small saturation bonus when Great/Gourmet."

  - item: "baked_potato"
    effects: ["Haste","Saturation"]
    base_effect_chance: 0.10
    quality_bias:
      Great: 0.05
      Gourmet: 0.12
    notes: "Baked potato favors Haste and saturation."

  - item: "beetroot"
    effects: []
    base_effect_chance: 0.00
    notes: "Raw beetroot has no special mapping; used in soups and stews."

  - item: "beetroot_soup"
    effects: ["Regeneration"]
    base_effect_chance: 0.12
    quality_bias:
      Great: 0.06
      Gourmet: 0.10
    notes: "Soup maps to regeneration."

  - item: "bread"
    effects: ["Saturation"]
    base_effect_chance: 0.08
    quality_bias:
      Great: 0.04
      Gourmet: 0.08
    notes: "Bread gives small saturation boosts."

  - item: "cake"            # cake is multi-slice; treat slices as cake consumption
    effects: ["Regeneration"]
    base_effect_chance: 0.10
    quality_bias:
      Great: 0.05
      Gourmet: 0.12
    notes: "Cake slice grants regeneration when high quality."

  - item: "cookie"
    effects: ["Speed"]
    base_effect_chance: 0.08
    quality_bias:
      Great: 0.03
      Gourmet: 0.07
    notes: "Cookie favors Speed."

  - item: "sweet_berries"
    effects: ["Speed"]
    base_effect_chance: 0.05
    quality_bias:
      Great: 0.02
      Gourmet: 0.05
    notes: "Snack; pairs well with pies."

  - item: "pumpkin_pie"
    effects: ["Luck","Speed"]
    base_effect_chance: 0.12
    quality_bias:
      Great: 0.06
      Gourmet: 0.14
    notes: "Pie gives luck or speed on higher quality."

  - item: "cooked_beef"     # steak
    effects: ["Strength","Luck"]
    base_effect_chance: 0.15
    quality_bias:
      Great: 0.08
      Gourmet: 0.20
    notes: "Steak is a strong candidate for Strength."

  - item: "cooked_porkchop"
    effects: ["Resistance","Haste"]
    base_effect_chance: 0.14
    quality_bias:
      Great: 0.07
      Gourmet: 0.18
    notes: "Porkchop leans Resistance/Haste."

  - item: "cooked_chicken"
    effects: ["Speed","Regeneration"]
    base_effect_chance: 0.13
    quality_bias:
      Great: 0.06
      Gourmet: 0.16
    notes: "Chicken favors Speed and regen."

  - item: "cooked_mutton"
    effects: ["Resistance","Saturation"]
    base_effect_chance: 0.12
    quality_bias:
      Great: 0.05
      Gourmet: 0.14
    notes: "Mutton gives defensive bonuses."

  - item: "cooked_rabbit"
    effects: ["Jump","Speed"]
    base_effect_chance: 0.12
    quality_bias:
      Great: 0.05
      Gourmet: 0.14
    notes: "Rabbit boosts mobility."

  - item: "cooked_cod"
    effects: ["Luck","Water_Breathing"]
    base_effect_chance: 0.10
    quality_bias:
      Great: 0.04
      Gourmet: 0.12
    notes: "Fish favors luck and water breathing."

  - item: "cooked_salmon"
    effects: ["Water_Breathing","Night_Vision"]
    base_effect_chance: 0.11
    quality_bias:
      Great: 0.05
      Gourmet: 0.13
    notes: "Salmon supports underwater exploration."

  - item: "pufferfish"
    effects: ["Water_Breathing","Night_Vision"]
    base_effect_chance: 0.05
    quality_bias:
      Great: 0.02
      Gourmet: 0.05
    notes: "Pufferfish is risky; low chance to grant effects."

  - item: "rotten_flesh"
    effects: []
    base_effect_chance: 0.00
    notes: "No positive mapping; may have rare negative interactions if desired."

  - item: "spider_eye"
    effects: []
    base_effect_chance: 0.00
    notes: "Not a food; used in brewing."

  - item: "mushroom_stew"
    effects: ["Night_Vision"]
    base_effect_chance: 0.12
    quality_bias:
      Great: 0.06
      Gourmet: 0.12
    notes: "Stews map to utility effects."

  - item: "rabbit_stew"
    effects: ["Jump","Regeneration"]
    base_effect_chance: 0.14
    quality_bias:
      Great: 0.07
      Gourmet: 0.16
    notes: "Hearty stew with mobility and regen."

  - item: "suspicious_stew"
    effects: ["varies_by_flower"]   # keep vanilla behavior: effect depends on flower used
    base_effect_chance: 0.18
    quality_bias:
      Great: 0.06
      Gourmet: 0.10
    notes: "Preserve vanilla suspicious stew mechanics; quality can extend duration."

  - item: "beetroot_soup"
    effects: ["Regeneration"]
    base_effect_chance: 0.12
    quality_bias:
      Great: 0.06
      Gourmet: 0.10

  - item: "honey_bottle"
    effects: ["Regeneration","Saturation"]
    base_effect_chance: 0.10
    quality_bias:
      Great: 0.04
      Gourmet: 0.10
    notes: "Honey is a versatile consumable."

  - item: "glow_berries"
    effects: ["Night_Vision"]
    base_effect_chance: 0.08
    quality_bias:
      Great: 0.03
      Gourmet: 0.07
    notes: "Glow berries are snackable with light-related utility."

  - item: "chorus_fruit"
    effects: ["Slow_Falling","Teleportation_placeholder"]
    base_effect_chance: 0.06
    quality_bias:
      Great: 0.02
      Gourmet: 0.05
    notes: "Chorus fruit retains teleport behavior; map to slow falling on quality."

  - item: "sweet_berries"
    effects: ["Speed"]
    base_effect_chance: 0.05

  - item: "cooked_chicken"   # duplicate handled above; included for completeness
    effects: ["Speed","Regeneration"]

  - item: "melon_slice"
    effects: ["Saturation"]
    base_effect_chance: 0.04
    notes: "Simple hydration/saturation snack."

  - item: "dried_kelp"
    effects: ["Saturation"]
    base_effect_chance: 0.03
    notes: "Low-value food; used as ingredient in virtual gourmet mapping."

  - item: "kelp"
    effects: []
    base_effect_chance: 0.00
    notes: "Raw kelp not mapped; dried kelp used."

  - item: "cooked_porkchop"  # duplicate handled above

  - item: "sugar"
    effects: []
    base_effect_chance: 0.00
    notes: "Ingredient only."

  - item: "milk_bucket"
    effects: ["Remove_Potion_Effects"]
    base_effect_chance: 1.00
    notes: "Preserve vanilla behavior: milk clears effects."

  - item: "bowl"
    effects: []
    base_effect_chance: 0.00
    notes: "Container only."

  - item: "mushroom"         # red/brown mushrooms are ingredients
    effects: []
    base_effect_chance: 0.00

  - item: "cocoa_beans"
    effects: []
    base_effect_chance: 0.00
    notes: "Used for cookies; no direct mapping."

  - item: "carrot"
    effects: ["Saturation"]
    base_effect_chance: 0.04
    notes: "Ingredient and snack."

  - item: "golden_apple"
    effects: ["Absorption","Regeneration"]
    base_effect_chance: 1.00
    quality_bias:
      Great: 0.10
      Gourmet: 0.25
    notes: "Golden apple retains strong vanilla effects; quality can extend durations."

  - item: "enchanted_golden_apple"
    effects: ["Absorption","Regeneration","Resistance","Fire_Resistance"]
    base_effect_chance: 1.00
    notes: "Keep vanilla op effects unchanged."

  - item: "golden_carrot"
    effects: ["Night_Vision"]
    base_effect_chance: 1.00
    notes: "Vanilla effect preserved."

  - item: "porkchop"        # raw handled by cook mapping

  - item: "rabbit"
    effects: []
    base_effect_chance: 0.00

  - item: "sugar_cane"
    effects: []
    base_effect_chance: 0.00

  - item: "honey_bottle"     # duplicate handled above

  - item: "cooked_cod"       # duplicate handled above

  - item: "cooked_salmon"    # duplicate handled above

  - item: "pufferfish"       # duplicate handled above

  - item: "suspicious_stew"  # duplicate handled above

  - item: "cooked_rabbit"    # duplicate handled above

  - item: "rabbit_stew"      # duplicate handled above

  - item: "mutton"           # raw handled by cook mapping

  - item: "cooked_mutton"    # duplicate handled above

  - item: "chorus_fruit"     # duplicate handled above

  - item: "cooked_cod"       # duplicate handled above

  - item: "cooked_salmon"    # duplicate handled above

  - item: "honey_bottle"     # duplicate handled above

  - item: "suspicious_stew"  # duplicate handled above

  - item: "beetroot"         # duplicate handled above

  - item: "beetroot_soup"    # duplicate handled above

  - item: "sweet_berries"    # duplicate handled above

  - item: "glow_berries"     # duplicate handled above

  - item: "melon_slice"      # duplicate handled above

  - item: "pumpkin_seeds"
    effects: []
    base_effect_chance: 0.00
    notes: "Seed item."

  - item: "pumpkin"
    effects: []
    base_effect_chance: 0.00
    notes: "Ingredient."

  - item: "cookie"           # duplicate handled above

  - item: "bread"            # duplicate handled above

  - item: "cake"             # duplicate handled above

  - item: "pumpkin_pie"      # duplicate handled above

  - item: "suspicious_stew"  # duplicate handled above

  - item: "honey_bottle"     # duplicate handled above

  - item: "rotten_flesh"     # duplicate handled above

  - item: "phantom_membrane"
    effects: ["Slow_Falling"]
    base_effect_chance: 0.20
    quality_bias:
      Great: 0.05
      Gourmet: 0.10
    notes: "Membrane grants slow falling when consumed via plugin logic (not vanilla)."

  - item: "rabbit_stew"      # duplicate handled above

  - item: "suspicious_stew"  # duplicate handled above

  - item: "cooked_beef"      # duplicate handled above

# Selection logic and combination rules
selection_logic:
  base_chance_to_grant_effect: 0.10
  quality_bonus:
    Great: 0.05
    Gourmet: 0.15
  flavor_burst_override: "If Flavor Burst active, guaranteed effect from mapping pool; duration scaled by quality and level."
  multi_item_combo:
    window_ticks: 200
    min_items_for_combo: 2
    combo_effects: "roll multiple effects from each item's mapping pool; cap at 3 simultaneous effects"
  stacking_rules:
    - "Do not stack identical potion effects from multiple items; extend duration instead."
    - "Milk clears temporary NBT effect markers on consumption."

achievements_and_stats:
  track:
    - total_items_cooked
    - gourmet_count
    - great_count
    - mastery_per_item
  achievements:
    - name: "Home Cook"
      requirement: "Cook 100 items"
    - name: "Gourmet Taster"
      requirement: "Consume 25 Gourmet items"
    - name: "Master Chef"
      requirement: "Reach Cooking level 100"

level_rewards:
  progression:
    - level: 10
      reward: "Precision Cooking I"
    - level: 25
      reward: "Kitchen Efficiency I"
    - level: 50
      reward: "Flavor Burst I"
    - level: 75
      reward: "Great Quality Unlock Improvements"
    - level: 100
      reward: "Master Chef I"
    - level: 150
      reward: "Flavor Burst II"
    - level: 200
      reward: "Kitchen Efficiency II"
    - level: 250
      reward: "Increased Gourmet Chance"
    - level: 300
      reward: "Master Chef II"
    - level: 400
      reward: "Maximum Meal Quality Bonuses"

implementation_guidelines:
  - "All behavior implemented server-side as gameplay logic; no client changes required."
  - "Use NBT tags to mark quality and temporary effect payloads; clear temporary tags on consumption."
  - "Avoid creating new items; use existing item IDs and NBT only."
  - "Flavor Burst and other active abilities should be toggled by players via commands or hotkeys exposed by the server plugin."
  - "Ensure compatibility with economy plugins by exposing an API hook for item sell-value multipliers."
  - "Provide config toggles for every numeric value for easy balancing."

balance_and_tuning_notes:
  - "Start with conservative proc rates and small XP bonuses; iterate with telemetry."
  - "Monitor bulk cooking exploits and cap bulk multipliers if needed."
  - "Make Flavor Burst cooldown and duration configurable."
  - "Allow server admins to disable specific mapped effects for balance or PvP servers."

# End of config
