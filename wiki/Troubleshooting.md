# Troubleshooting

**Start here: check the log.** Every error path in this port logs. A silent failure in game is usually a loud one in `.minecraft/logs/latest.log`.

---

## "My super ability won't unlock"

**Almost always RetroMode.** It's on by default, which multiplies every unlock level in `skillranks.yml` by **10**. Rank 1 of most super abilities is skill level **50**, not 5.

Check with `/mcstats <skill>` — it shows each sub-skill's rank and what you currently have.

## "I granted myself levels and nothing happened"

Same cause as above, plus: `/addlevels` sets the number directly. If you want level-ups, milestone advancements and XP bars to fire as they would in play, use **`/addxp`** — it pushes XP through the real gain pipeline.

Both commands need **permission level 2**: op level 2 on a server, or a world created with **Allow Cheats: ON**.

## "A config change did nothing"

Two likely causes:

1. **Most values are read once at load time.** Reload the world.
2. **The key was already in your file.** mcMMO back-fills *absent* keys on load but never overwrites present ones — so a changed default in a new release does not reach an existing config. **Delete the key (or the file) and let it regenerate.**

## "My skill levels vanished"

Skill data is stored **per world** in `<world save>/mcmmo/players/`. A different world means different skills; there is no global profile.

If it's the *same* world, check the log for a warning about a renamed skill section.

## "Acrobatics is gone / Agility is 0"

Working as intended. Acrobatics was renamed **Agility**, and Agility then became a **child skill** — its level is the mean of Parkour, Swimming and Flying, which all start at 0.

Child skills have no save key at all, so there was nothing for old progress to migrate to. Train the three parents. See [Movement Skills](Movement-Skills).

## "Stealth earns no XP at all"

Check these in order:

1. **Are you sneaking on the ground?** Crouch-swimming pays nothing by design, and neither does sneaking on the spot — you have to actually cover distance.
2. **`ExploitFix.Stealth.Require_Movement_Input`.** This gate reads your real server-side input state so being *carried* earns nothing. If input packets aren't arriving for some reason, the gate reduces Stealth XP to **zero** rather than merely mis-tuning it. Setting it to `false` temporarily will tell you whether this is the cause — if that fixes it, please **file an issue**, because that's a real bug and not something you should have to leave off.

## "Unarmored earns no XP"

**Every armour slot must be empty** — all four, including the helmet you forgot about.

Then: XP only pays for damage from a **living attacker other than you** (`Require_Living_Attacker`). Cactus, fire, fall, lava, drowning and your own TNT all pay nothing on purpose.

And one attacker only pays **20 times** (`Max_Awards_Per_Attacker`) before it stops counting — so a single mob you've been standing in front of for an hour has long since stopped paying.

## "Iron Skin isn't showing up"

It's granted **only while all four armour slots are empty**, and re-derived every tick — so it disappears the instant you equip anything.

When it *is* active it shows on the **normal vanilla armour bar**, because those are real armour points. If your armour bar shows nothing while you're naked, check your Unarmored level against the tier table: rank 1 is Unarmored **100** in RetroMode.

## "My enchanted tool lost all its enchantments when I repaired it"

**Working as intended, faithfully to upstream mcMMO.** Without an **Arcane Forging** rank, repairing an enchanted item strips *every* enchantment. In RetroMode that means below Repair 100.

This one genuinely eats good gear. It's flagged in [Skills](Skills#repair) for that reason.

## "Super Breaker keeps firing while I'm building"

Use **`/mcability`** to toggle abilities off. Or require sneaking to arm them:

```yaml
Abilities:
    Activation:
        Only_Activate_When_Sneaking: true
```

## "Call of the Wild doesn't work"

Sneak + **left-click a block**. Sneak-left-clicking **air** is the one gesture that isn't wired — Fabric has no left-click-air callback.

## "Second Wind and Smoke Bomb interfere with each other"

They're configured to the same item. Both listen on the same use-item event, so sharing an item fires one and prints the other's refusal message. Give them different items:

```yaml
Skills:
    Agility:
        Second_Wind_Item: FEATHER
    Stealth:
        Smoke_Bomb_Item: GUNPOWDER
```

## "I'm invisible but mobs still see me / people can see my armour"

Vanilla Invisibility **does not hide armour or held items**. That's vanilla behaviour, not a Smoke Bomb bug.

## "Breaking a block I placed gives no XP"

Working as intended. Placed blocks are tracked so you can't farm XP by placing and re-breaking — and the record **survives restarts** (`placed_blocks.dat` in your world folder). Place → quit → reopen → mine still pays nothing.

## "Levelling Limit Break does nothing"

Correct — **it does nothing**. All eight Limit Break sub-skills are dead enums: they show in `/mcstats`, they have rank ladders in the configs, and no code reads them. See [Differences from mcMMO](Differences-from-mcMMO#-dead-enums--present-but-doing-nothing).

## "Harvesting crops from my horse gives no XP"

Working as intended — `Skills.Herbalism.Prevent_AFK_Leveling` is shipped on. Upstream ships the key but never consults it; this port does.

## "Swimming/Flying level absurdly fast or slow"

Likely real, and likely the reference speeds. Land's is the known vanilla sprint speed, but **Water (3.16) and Air (30.0) are estimates that have not been measured in-game**.

Correcting them in `experience.yml` → `Experience_Values.Agility.Movement.Reference_Speed` is the intended fix. If you measure a better number, **please open an issue with it** — that's genuinely useful data.

---

## Filing a good issue

[Issues](https://github.com/Wulfic/mcMMO-Singleplayer/issues). Please include:

- **What you observed**, not what you expected — a `/mcstats` delta, the message on screen, the block that did or didn't change
- Minecraft, Fabric Loader and mcMMO versions
- Whether **RetroMode** is on
- The relevant chunk of `latest.log`
- Any other mods installed
