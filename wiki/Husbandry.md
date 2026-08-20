# Husbandry

**New in this port** — Husbandry does not exist in upstream mcMMO.

**How you train it:** work livestock. Six separate verbs pay XP — **breed**, **raise**, **feed a baby**, **shear**, **harvest a hive**, and **milk or brush**.

Nine sub-skills and a super ability, the largest set of any skill in the mod.

> ⚠️ **Code-complete, never play-tested.** Every number on this page is a starting estimate. Balance feedback is genuinely wanted.

---

## The idea

Taming already existed and already claims every animal in the game. Husbandry is not "Taming for farm animals" — the two are split on the **verb, never the species**:

| | Pays |
|---|---|
| **Taming** | **Once**, for making an animal yours. |
| **Husbandry** | **Repeatedly**, for what you do with it afterwards. |

So breeding a tamed wolf pays Husbandry at the full rate, while feeding a wolf to heal it in a fight stays Taming. A species split was never available — Taming's XP table already lists bees and goats, which you cannot tame at all.

---

## Earning XP

`experience.yml` → `Experience_Values.Husbandry`.

| Verb | Pays | Notes |
|---|---|---|
| **Breed a pair** | `Animal_Breeding.<Species>` | Once per breeding, **not** once per parent. |
| **Raise to adulthood** | `breed XP × Raise_Multiplier` (`1.0`) | Paid when the animal grows up. |
| **Feed a baby** | `Feed_Baby: 50` | Small — it's the one verb you can spam. |
| **Shear** | `Shear: 300` | |
| **Harvest a hive** | `Hive_Harvest: 500` | |
| **Milk** | `Milk: 200` | Cow, mooshroom **and goat**. |
| **Brush** | `Brush: 300` | Armadillo. |

### The breeding table

Per-species rather than flat, because a breeding item's cost spans two orders of magnitude — chicken seeds are free, a horse eats golden carrots, and a sniffer needs a torchflower seed dug out of suspicious sand.

| Band | Species | XP |
|---|---|---|
| Free or near-free feed | Chicken 300 · Cow / Sheep / Pig 350 · Goat 400 · Rabbit 450 | 300–450 |
| Meat and fish | Wolf / Cat / Ocelot / Mooshroom 500 · Bee 600 | 500–600 |
| Awkward to source | Turtle / Frog / Armadillo 700 · Fox / Llama / Trader Llama 800 | 700–800 |
| Nether, or a bucket per parent | Hoglin / Strider / Axolotl 900 · Panda 1000 · Camel 1100 | 900–1100 |
| Mounts | Horse / Donkey / Mule / Nautilus / Happy Ghast 1200 | 1200 |
| Archaeology-gated | Sniffer 1500 | 1500 |

> ⚠️ **An unlisted species pays nothing, deliberately** — and because the raise verb is a *multiple* of the breed value, an unlisted species pays nothing for **both halves** of its lifecycle. That contract means a mob added by a future Minecraft version or another mod cannot silently start paying a number nobody chose. It also means the table has to be kept current: Nautilus and Happy Ghast were both missing until 2026-07-30 and paid zero the whole time. **If a vanilla breedable pays nothing, that's a bug — please file it.**

At the shipped rates, a RetroMode level-1000 Husbandry is roughly **51 hours** of active breeding.

### What deliberately pays nothing

- **Scooping a mob into a bucket.** Capture is Taming's side of the verb boundary, and an axolotl can be poured out and re-scooped in two clicks forever.
- **Laying and collecting eggs.** `eggLayTime` is a passive timer, so a hopper under a coop is fully AFK income. See [Brood](#brood--unlocks-at-200).
- **Anything a dispenser does.** Every harvest verb requires a real player mid-interaction with the animal, enforced in code with no toggle. That is what keeps an AFK dispenser-and-hopper wool farm worth zero.

---

## The anti-exploit gates

### `ExploitFix.Husbandry.Harvest_Cooldown_Seconds` (default `300`)

**How long one animal must wait before it can pay a harvest award again.** Set to 0 to disable.

It covers **milking and brushing, and only those two**, because they are the only harvest verbs vanilla rate-limits by nothing whatsoever. Right-clicking a cow with a bucket is free and infinitely repeatable on the *same* cow — unmitigated it would be the fastest XP in the mod. Brushing an armadillo *looks* rate-limited and is not: the brush loot table has no conditions and never touches the armadillo's scute-shed timer, which governs only the passive shed.

**Shearing and hive harvesting are exempt on purpose.** Vanilla already limits them, in fiction and for free: a just-sheared sheep is worthless until it has eaten its way back to a full coat, and a drained hive needs five levels of bee-pollination time. Five minutes matches vanilla's own post-breeding love cooldown, so the two clocks you keep in your head are the same clock.

The cooldown counts **world ticks**, not wall-clock time — pausing the game does not drain it.

### `ExploitFix.Husbandry.Breed_Xp_Awards_Per_Window` (default `8`) and `Breed_Xp_Award_Window_Seconds` (default `30`)

**How many breedings pay Husbandry XP in a given window.** Set either to 0 to disable the cap.

Not a flavour knob. Husbandry pays per **breeding**, and Multi-Breed turns one player action into many breedings, so something has to bound what a pen can produce.

**The 30 seconds is derived, not tuned.** It is vanilla's own love duration — feeding an animal sets `loveTicks = 600` — so every breeding one handful of feed can possibly cause lands inside a single window. That is what makes the cap readable as *"one handful of feed pays at most eight breedings"*. Do not shorten the window to make it feel fairer: a window briefer than vanilla's love duration splits one click's burst across two windows and silently doubles the effective cap.

A refused breeding still **happens** and still produces its calf — the cap gates the reward, never the game. It simply pays nothing, and it leaves the calf **unmarked**, so the raise verb pays nothing for it either twenty minutes later. Otherwise the cap would be a delay rather than a cap. You are told once per window when it starts biting.

The window counts **world ticks**, not wall-clock time — pausing the game does not drain it.

> **Replaced `Skills.Husbandry.MultiBreed.MaxAdditionalAnimals` (was `4`) on 2026-08-04.** That knob capped how many animals **one breeding item** could set in love. It was wrong twice: it taxed the mechanic rather than the reward — the whole point of Multi-Breed is feeding the pen from where you stand — and it never actually bounded the XP *rate*, because it bounded XP per **item** and wheat is free. Twenty clicks in one breath paid fifty breedings straight through it. If your `advanced.yml` still carries the old key it does nothing, and the log says so at startup; delete it.

---

## Sub-skills

RetroMode unlock levels shown. Divide by 10 for Standard mode.

### Multi-Breed — unlocks at 1

**One handful of feed sets the whole pen courting.**

Feed **one** animal its breeding item and **every** eligible animal of the same species within the radius is set in love too, from that single item. There is no cap on the count — the radius is the only bound.

| Knob | Default |
|---|---|
| `BaseRadius` → `MaxRadius` | 4.0 blocks at unlock → 40.0 at max level |

`MaxRadius` is **hard-clamped to 40 in code** whatever you write: it sizes an entity sweep that runs every time any player feeds any animal, so a mistyped 400 would scan a box eight chunks across.

How much XP that spread is worth is bounded separately, by the [per-window award cap](#exploitfixhusbandrybreed_xp_awards_per_window-default-8-and-breed_xp_award_window_seconds-default-30).

Unlocked at level 1, along with Twins and Bountiful Harvest, because breeding and shearing are the skill's *entry* verbs — gating them would mean the early skill is levelled by doing the one thing nothing rewards yet. Their strength still scales from zero.

### Twins — unlocks at 1

**Chance for a breeding to bear two young.** `ChanceMax: 25.0`

25 %, not the 100 % that Herbalism's and Mining's double drops use. It **multiplies** with Multi-Breed rather than adding to it — the two together at 100 would turn one wheat into a whole herd. At 25 a twin birth stays a pleasant surprise at max rank.

> **Egg-laying breeders never twin** — frog, sniffer and turtle. Vanilla hands them an egg rather than a baby, and duplicating that would be a different mechanic.

### Bountiful Harvest — unlocks at 1

**Your animals give up more, and your tools last longer.**

The harvest family's shared reward, driving **all four** harvest verbs — shear, hive, milk and brush — through the same two rolls.

| Knob | Default | Effect |
|---|---|---|
| `ChanceMax` | **50.0** | A second helping of whatever the harvest just dropped. |
| `DurabilitySaveChanceMax` | **25.0** | The harvest costs the tool no durability at all. |

The bonus is a copy of whatever the harvest actually handed over, so a sheep's colour and a mooshroom's variant carry into it for free. Rolled **once per harvest, not once per item** — a shear either doubles or it doesn't.

The durability save is deliberately the smaller number: a bonus drop is a windfall you notice, a durability save is only ever felt as "my shears last longer", and at 100 it would quietly turn shears into an infinite tool.

### Beekeeper — unlocks at 100

**The hive is robbed and the bees never mind.**

The headline half is **binary at the unlock level and has no knob**, on purpose. The point of it is being able to stop carrying a campfire and stop planning your apiary around one; a 90 %-of-the-time version is worse than not having it, because you would still have to build the campfire for the tenth harvest.

Under the hood it is expressed as *"you always count as standing over a lit campfire"* — vanilla's own branch — so it suppresses both the nearby-bee retargeting **and** the hive's emergency release in one stroke.

`ChanceMax: 30.0` is the second, smaller half: an extra helping of comb or honey. It **stacks with Bountiful Harvest** rather than replacing it, so a maxed beekeeper visibly out-yields a maxed generalist on a hive.

### Accelerated Growth — unlocks at 150

**Your young grow up sooner, and feed goes further.**

| Knob | Default | Effect |
|---|---|---|
| `MaxGrowthAcceleration` | **0.30** | Fraction of a newborn's childhood skipped, applied **once at birth**. |
| `ChanceMax` | **25.0** | Chance that one feed counts as two. |

Applied at birth rather than by speeding up ageing every tick — you see the same thing and the sub-skill never touches a tick path. **Only animals you bred yourself**; a baby you found in the wild has nobody to credit.

Hard-clamped to 0.90 in code. Keep it modest: raising is the one income in this skill that cannot be rushed — twenty real minutes per animal — and that is exactly why it pays as much as breeding does.

Gated at a level rather than unlocked at 1, unlike the three above, because it shortens the wait on the raise verb: handing it over at level 1 speeds up an income the player has not yet earned the right to speed up.

### Brood — unlocks at 200

**Your thrown eggs are far likelier to hatch, sometimes in fours.**

| Knob | Default | Effect |
|---|---|---|
| `ChanceMax` | **35.0** | Chance to rescue an egg vanilla was about to waste. |
| `MultiChickChanceMax` | **20.0** | Chance a hatch yields four chicks instead of one. |

`ChanceMax` **layers on top of** vanilla's 1-in-8 rather than replacing it, so the effective hatch rate is `12.5 % + ChanceMax × 87.5 %`. Written that way round deliberately — if it replaced the roll, a configured 10 would be a *downgrade* on vanilla, which is the kind of knob nobody notices is backwards.

> **Laying and collecting eggs pays no XP and never will**, and a hatched chick carries no bred-by marker either. `eggLayTime` is a passive timer, so a hopper under a coop is AFK income — and a marker would have turned that same farm into a raise-XP farm twenty minutes later. Brood is a **yield** sub-skill only.

### Selective Breeding — unlocks at 250

**Each generation of your stock outdoes the last.** `MaxStatBias: 0.25`

The fraction of the gap between the rolled value and the best the **species** allows that a foal is nudged. Applied to the *outcome* of vanilla's own inheritance roll, not to the dice, so good parents still matter. It can only ever improve a foal, never worsen one.

**Only the horse family has inheritable stats in vanilla** — health, speed and jump strength — so that is where this bites.

> ⚠️ **Keep it modest: it compounds down the generations.** Every foal is bred from parents that were themselves biased, so a number that looks fair for one breeding walks toward the species maximum far faster than it reads. Hard-clamped to 0.50, because 1.0 would not be "very good horses" — it would be every horse at exactly the maximum from the first breeding, which deletes horse breeding as an activity instead of rewarding it.

> **Not implemented:** the rare-variant half of the idea (a chance at an unusual coat or type). Variants are set inside each species' own `createChild` with no shared funnel, so it would need one hook per species plus a species-keyed table — exactly the shape that has already rotted several times in this port.

### Hidden Bounty — unlocks at 300

**A well-worked herd gives up more than it should.** `ChanceMax: 20.0`

A rare find on **any** harvest verb, on top of whatever the animal already gave you.

`ChanceMax` is only the **first of two gates**. On a success, the `Husbandry` section of `treasures.yml` is walked in file order and each candidate rolls its own `Drop_Chance`, so the real rate for any one item is the product of the two.

| Treasure | Drop chance | Unlocks at | From |
|---|---|---|---|
| Name Tag | 4 % | 500 | Shear, Hive, Milk, Brush |
| Honey Block | 8 % | 200 | Hive |
| String ×2 | 15 % | 0 | Shear |
| Armadillo Scute ×2 | 12 % | 0 | Brush |

The name tag is deliberately reachable from every verb: a keeper who works their herd long enough should eventually turn up something that has nothing to do with livestock. **File order decides reachability** — that warning lives in `treasures.yml` itself.

---

## Herdsman's Call — the super ability

**Sound the horn and the whole herd answers.** Unlocks at **Husbandry 100**.

Not a readied tool ability. **Right-click while holding a goat horn** (`Skills.Husbandry.Herdsmans_Call_Item`) and it fires immediately. The item is **never consumed**, and mcMMO only *observes* the click — so the horn still sounds as vanilla intends.

For the duration, three things happen at once:

| Effect | Detail |
|---|---|
| **The herd follows you** | Animals of any breedable species within `Radius` (16 blocks) path toward you under **vanilla's own navigation** — not teleported, not velocity-shoved, so fences, walls and water still stop them. Only already-idle animals are moved, so they don't jitter fighting their own goals. |
| **Harvest cooldowns are ignored** | Every harvest verb skips the five-minute per-animal gate — and does **not** stamp the animal's clock, so the ability doesn't hand you a second free round when it ends. |
| **Every harvest double-yields** | All four harvest verbs, and it does **not** require a Bountiful Harvest rank. |

`Radius` is hard-clamped to 40 in code: it sizes an entity sweep that runs **every tick for the whole duration**.

`DurationTicks: 200` is a **floor, not the length**. The standard super-ability machinery decides the real duration from your skill level and `config.yml`'s `Ability_Length` / `Ability_Length_Cap` / `Max_Seconds.Herdsmans_Call`, exactly like every other super — this number only stops the ability being worth two seconds at the level it first unlocks.

> ⚠️ **Goat horn must differ from `Second_Wind_Item` (feather) and `Smoke_Bomb_Item` (gunpowder).** All three actives listen on the same use-item event, so a shared item fires one and prints another's refusal message — which looks like a broken ability rather than a config collision. See [Super Abilities](Super-Abilities#item-triggered-abilities).

Why a goat horn and not a breeding item: feeding animals is this skill's core loop, and overloading that click invites confusion. A horn is thematic, and it has a real acquisition cost — a goat has to ram a block for you — which suits an ability unlocked at 100.

---

## Tuning

| File | Section |
|---|---|
| `experience.yml` | `Experience_Values.Husbandry.*` — the six verbs and the breeding table |
| `experience.yml` | `ExploitFix.Husbandry.Harvest_Cooldown_Seconds` |
| `advanced.yml` | `Skills.Husbandry.*` — one block per sub-skill |
| `skillranks.yml` | `Husbandry.*` — unlock levels |
| `treasures.yml` | `Husbandry:` — the Hidden Bounty table |
| `config.yml` | `Skills.Husbandry.Level_Cap`, `Skills.Husbandry.Herdsmans_Call_Item` |
| `config.yml` | `Abilities.Cooldowns.Herdsmans_Call`, `Abilities.Max_Seconds.Herdsmans_Call` |
