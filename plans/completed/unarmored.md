# Unarmored — Plan

> ## ✅ BUILT (2026-07-28) — code-complete, **never played**
>
> Everything below is the plan as written before the build; it is kept as the record of *why*, not as
> a description of what exists. What actually shipped, and where it deviates:
>
> | Stage | What | Where |
> |---|---|---|
> | 1 | Skill registered, MC-free `UnarmoredManager`, all config/locale | `39cf0ad5f` |
> | 2 | **XP** — pre-armour capture + the victim-branch award | `7ebc32625` |
> | 2b | **Per-attacker XP cap** (below) — the D-U4 ruling | this pass |
> | 3 | **Iron Skin** — managed `ARMOR` modifier, re-derived per tick | this pass |
> | 4 | **Thorny Skin** — melee reflect | this pass |
>
> **Deviations and findings the plan did not anticipate:**
> - **⚠️ mcMMO's damage seam is POST-armour.** `LivingEntity#applyDamage` runs
>   `applyArmorToDamage` *then* `modifyAppliedDamage`, so Iron Skin would have throttled the very
>   skill that grants it — the 500→1000 stretch (7.5M of 11.01M XP) would run at ⅓–½ rate. XP is
>   therefore metered on a **second injector** on `applyArmorToDamage`, stashed and consumed at the
>   **top** of `onModifyAppliedDamage` (Serrated Strikes, Counter Attack and Thorny Skin all deal
>   nested damage from inside that method and each re-runs `applyArmorToDamage`).
> - **✅ D-U3 resolved as recommended**, in `PlayerMovementTracker`'s sweep — but it sits **above**
>   the Agility early-return, or a player's armour would depend on an unrelated skill loading.
> - **✅ The "does the skin show on the vanilla armour HUD?" worry is dead.** `ARMOR` is registered
>   `setTracked(true)` and `getArmor()` is literally `floor(getAttributeValue(ARMOR))`, so the armour
>   bar fills in for free. **The cosmetic follow-up in "Cuts / deferrals" is cancelled, not deferred.**
> - **⚠️ NEW RULING — D-U4, the per-attacker XP cap.** `Require_Living_Attacker` (below) does *not*
>   close the farm that matters: a zombie **is** a living attacker, so one hitting you through a slab
>   while saturation regen keeps up is a passive ~250 XP/s ⇒ level 1000 in ~12 h against the 92 h
>   budget. Ruled 2026-07-28: cap at **20 awards per attacker**
>   (`ExploitFix.Unarmored.Max_Awards_Per_Attacker`), the same `MetadataStore` mechanism Agility
>   Dodge uses, four times the size so it never bites a real fight.
> - **⚠️ The exploit gate needed a clause its name does not imply**: a player is a `LivingEntity`, so
>   without `attacker != victim` your own Blast Mining charge pays full XP.
> - **⚠️ "Unarmored" means an OCCUPIED SLOT**, not an `isArmor` item — a carved pumpkin or an elytra
>   disables the skill. Otherwise the rule is "free armour as long as mcMMO doesn't recognise your hat".
>
> **Play-test rows: `PLAYTEST_G.md` session 9.** The balance figures below are still paper.

**Read [00-OVERVIEW.md](00-OVERVIEW.md) first.** Unarmored is a **standalone primary skill** (D1). It is
**event-driven for XP** (damage taken) and **depends on F2** (the attribute service) but **not on F1** —
which is why the overview recommends building it **first** among the new skills: it validates the
managed-attribute lifecycle without the tick-sampler risk. It is a `MISC_SKILLS` (defensive) skill.

Wiki source: `raw_site_text.md` §"Unarmored".

## Concept

Reward the player for fighting without armor by giving them innate "skin" armor and a thorns reflect.
You level it by **taking damage while all four armor slots are empty.** The payoff is tiered permanent
armor points (only while unarmored) plus a thorns effect.

**XP source:** damage *taken* while unarmored, hooked in the player-damage path (`EntityDamageListener`).
XP proportional to damage taken. **Only when helmet/chest/legs/boots are all empty** — one piece of
armor and no XP (and no skin bonus).

## Sub-skills

Wiki tiers are level-gated skins. Model them as one scaling sub-skill (Iron Skin) with tier breakpoints,
plus Thorny Skin. RetroMode numbers.

| Sub-skill | Type | Mechanic | Scaling (wiki → proposed) | Unlock | Risk |
|---|---|---|---|---|---|
| **Iron Skin** (the "skins") | passive | Innate armor points while unarmored, growing in tiers | Leather @100, Gold @200, Iron @500, Diamond @1000 → a managed `GENERIC_ARMOR` (+ maybe `GENERIC_ARMOR_TOUGHNESS`) modifier applied **only while unarmored**, whose magnitude steps up at those breakpoints (or scales continuously; pick one, see D-U2). | 100 | Med (attribute + reactivity) |
| **Thorny Skin** | passive | Reflect a little damage to melee attackers while unarmored | @350 (wiki); reflect scales up to ~half a heart. Like vanilla Thorns but from the skill, unarmored only. | 350 | Low |

## Design decisions specific to Unarmored

- **D-U1 — the incoherent "stacks and doubles" clause:** the wiki says the skin gives armor "when not
  wearing armor" **and then** "if you're wearing actual armor that defence is doubled." Those contradict.
  **Recommendation: skin applies ONLY while unarmored** (that's the skill's entire premise — reward going
  without armor). **Drop the "doubles real armor" clause.** If the user wants a wearing-armor synergy,
  that's a *different* skill and a separate decision. Flag it.
- **D-U2 — stepped tiers vs continuous scaling:** stepped (the wiki's leather/gold/iron/diamond
  breakpoints) is more faithful and more legible to the player ("I hit Iron Skin!"). Continuous is
  smoother but invisible. **Recommend stepped**, with the armor-point values matching the real armor sets
  (leather ≈7, gold ≈11, iron ≈15, diamond ≈20 armor points — verify current vanilla values). Config the
  exact numbers.
- **D-U3 — reactivity is the risk:** the skin must appear/disappear the moment armor is equipped or
  removed, on level-up across a breakpoint, on respawn (new entity — `[[respawn-stale-handle]]`), and be
  cleared on logout. There is no clean "armor changed" Fabric event; either (a) an equipment-change mixin
  or (b) a cheap per-tick armor-slot check folded into F1's sweep (even though Unarmored doesn't need F1
  for XP, riding F1 for the armor-slot check is the pragmatic option). **Recommend: re-derive the skin
  modifier each tick from current armor state via F1's sweep**, so it's always consistent and there's no
  stale-modifier bug. This makes Unarmored a *light* F1 consumer after all — note that in the build order.

## MC-free core (`UnarmoredManager extends SkillManager`)

- `float getUnarmoredXp(double damageTaken)` — XP from a hit taken while unarmored.
- `double getSkinArmorPoints(boolean unarmored)` — the armor points to apply: `0` if armored, else the
  tier value for the current level (stepped per D-U2). Deterministic, testable at each breakpoint.
- `boolean thornsReady()` / `double getThornsDamage()` — the reflect amount, unarmored + rank gated.
- Standard `canX()` gates.

The manager decides armor points and thorns damage from level + an `isUnarmored` boolean; the listener
provides the boolean and applies the attribute/reflect.

## MC-typed trigger layer

1. **XP (damage taken):** in `EntityDamageListener`, on the player-*victim* branch, if all four armor
   slots are empty award `getUnarmoredXp(damageTaken)`. Confirm the port has (or add) a victim-side hook —
   the existing listener is attacker-centric for combat XP, so this may be a new branch.
2. **Iron Skin (armor):** via F2, apply a managed `GENERIC_ARMOR` (+ toughness at diamond tier?) modifier
   sized by `getSkinArmorPoints(unarmored)`. Re-derived each tick from armor state (D-U3): armored →
   modifier absent; unarmored → modifier at the current tier. Idempotent; cleared on logout/respawn.
3. **Thorny Skin:** in `EntityDamageListener`, on the player-victim branch, when unarmored and
   `thornsReady()` and the damage source is a living melee attacker, deal `getThornsDamage()` back to the
   attacker. Don't reflect environmental/fall damage.

## Registration specifics

- `PrimarySkillType.UNARMORED`; `MISC_SKILLS`.
- `SubSkillType`: `UNARMORED_IRON_SKIN` (tiers), `UNARMORED_THORNY_SKIN`.
  - ⚠️ **Name-clash check:** the existing `UNARMED_*` sub-skills are close in spelling — keep `UNARMORED_`
    prefix distinct and make sure nothing does prefix-matching that confuses `UNARMED` vs `UNARMORED`
    (`SubSkillType.getParentSkill` splits on the first `_`, so `UNARMORED_IRON_SKIN` → parent `UNARMORED`;
    fine, but eyeball the `SkillTools.buildSubSkillParentMap` prefix logic to be sure `UNARMED` and
    `UNARMORED` don't collide).
- No super ability → **skip `SuperAbilityType`.**
- `experience.yml`: `Unarmored` XP modifier + bar color. Consider an exploit toggle — standing in a
  damage source (cactus/fire) to farm XP is the obvious cheese; throttle or exclude non-combat damage.
- `advanced.yml`: tier breakpoint levels + armor-point values, Thorny unlock + max reflect.
- Locale block `Unarmored.*`.

## Balance / XP tuning

- **Free permanent armor is strong.** Diamond-equivalent armor at level 1000 with *no durability cost* is
  a big deal — that's the skill's fantasy, but tune the XP curve so it's a long grind, and confirm it
  doesn't trivialize combat once maxed.
- **XP-from-damage-taken is farmable** by sitting in lava/cactus/fire. Recommend counting **only combat
  damage from a living attacker** for XP (exclude fall/fire/cactus/drowning), or heavily throttle
  environmental damage. This is the main exploit vector — add a §G cheese-attempt row.
- Thorny reflect must stay tiny (half a heart cap per wiki) — don't turn it into a mob-melter.

## Testing

- **Unit (MC-free):** `getSkinArmorPoints` at 99/100/200/500/1000 (0 → leather → gold → iron → diamond)
  and `0` whenever `unarmored=false`; `getUnarmoredXp` proportional to damage; `thornsReady`/
  `getThornsDamage` gates + cap.
- **F2 lifecycle (critical):** equip armor → skin modifier removed same tick; remove armor → reapplied;
  cross a tier on level-up → magnitude updates, not stacks; respawn → re-derived on the new entity, no
  duplicate; logout → cleared. These are the bugs that only show live — unit-test the derivation and
  §G-test the entity transitions.
- **§G rows:** take mob hits bare-handed → Unarmored XP delta; at 100 you visibly tank more (armor bar
  behavior — note the skin may not *show* on the vanilla armor HUD; decide if that's acceptable or if it
  needs a display); equip a chestplate → the skin bonus vanishes; unarmored melee attacker takes chip
  reflect damage (Thorny) at 350; try to farm XP in cactus → confirm it's throttled/excluded.
- **Regression:** old profile loads with Unarmored defaulted.

## Cuts / deferrals

- **"Doubles real armor" — CUT** (D-U1) unless explicitly reinstated.
- If the skin not showing on the vanilla armor HUD is a problem, an armor-bar display is a **separate,
  later** cosmetic task — do not block the skill on it.
