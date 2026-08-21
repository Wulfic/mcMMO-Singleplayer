package com.gmail.nossr50.fabric.commands;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.gmail.nossr50.commands.skills.SkillStatsRenderer;
import com.gmail.nossr50.config.CoreSkillsConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.text.TextUtils;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.SkillAvailability;
import com.gmail.nossr50.util.skills.SkillGating;
import com.gmail.nossr50.util.skills.SkillTools;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * mcMMO's in-game commands, converted from the legacy Bukkit {@code CommandExecutor}/{@code
 * TabExecutor} tree to Brigadier (CONVERSION_TODO Phase 4). Registered via Fabric's
 * {@link CommandRegistrationCallback}.
 *
 * <p>Scope: the singleplayer-relevant self commands plus the two admin XP commands that make
 * progression observable/testable. The party/chat/scoreboard/admin-broadcast command tree was cut
 * with the multiplayer layer (Phase 1.5), so it is not ported.
 *
 * <ul>
 *   <li>{@code /mcmmo} — mod + version banner.</li>
 *   <li>{@code /mcstats} — the caller's level and XP for every skill, plus power level.</li>
 *   <li>{@code /mcstats <skill>} — the full-detail per-skill screen (legacy {@code /<skillname>}):
 *       XP-gain method, level/XP, sub-skill ranks, and each sub-skill's current effect values,
 *       rendered by {@link SkillStatsRenderer}.</li>
 *   <li>{@code /mcability} — toggle whether super abilities may be readied/activated.</li>
 *   <li>{@code /mcrefresh} — clear the caller's super-ability cooldowns and active modes
 *       (op level 2; it removes the entire cost model of the super abilities).</li>
 *   <li>{@code /addlevels <skill|all> <amount>} — admin: grant skill levels (op level 2).</li>
 *   <li>{@code /addxp <skill|all> <amount>} — admin: grant raw XP through the real gain pipeline.</li>
 * </ul>
 */
public final class McMMOCommands {

    /**
     * Skills that mcMMO used to have, mapped to the locale key explaining where they went (A-5).
     *
     * <p><b>Why this exists.</b> A player who levelled a skill for weeks and then gets
     * <em>"Unknown skill: agility"</em> concludes the mod broke, files an issue, and is right to.
     * The token is not unknown — it is retired, and the answer to "where did my perks go" is a
     * fact the mod holds and they do not.
     *
     * <p>⚠️ <b>Checked BEFORE {@code matchSkill}</b>, which logs a warning for a name it cannot
     * resolve. A retired name is not operator error and must not be logged as such.
     *
     * <p>The locale keys are written out as literals rather than derived from the token, so
     * {@code grep} finds them. A key built by concatenation is invisible to every catalogue guard
     * in this repo — the {@code XPBar.<Skill>} family is the standing example.
     */
    private static final Map<String, String> RETIRED_SKILLS = Map.of(
            // Retired 2026-08-17. Fleet Footed and Second Wind became six single-rank sub-skills,
            // two under each of Parkour, Swimming and Flying.
            "agility", "Commands.Skills.Retired.Agility");

    /** "all" targets every non-child skill in the level/xp admin commands. */
    private static final String ALL_TOKEN = "all";

    private McMMOCommands() {
    }

    /** Register every mcMMO command. Called once at mod load from {@code McMMOMod#onInitialize}. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> registerAll(dispatcher));
    }

    /**
     * The gate on every command that hands out progress or removes a cost — level 2, the same level
     * vanilla puts {@code /gamemode} and {@code /give} behind (GitHub #8).
     *
     * <p>Package-private so {@code McMMOCommandsTest} can assert <em>which</em> commands carry it.
     * Brigadier stores the predicate instance it is handed, so that test compares the same object.
     *
     * <p>⚠️ <b>Declared as a plain {@link java.util.function.Predicate}, deliberately.</b> Minecraft
     * reworked the command permission API at the <code>1.21.10 → 1.21.11</code> boundary and the
     * concrete return type is version-specific:
     *
     * <ul>
     *   <li>{@code 1.21.11+}: {@code requirePermissionLevel(PermissionCheck)} returns a
     *       {@code PermissionSourcePredicate} <i>record</i>;</li>
     *   <li>{@code ≤1.21.10}: {@code requirePermissionLevel(int)} returns a
     *       {@code PermissionLevelPredicate} <i>interface</i>.</li>
     * </ul>
     *
     * Both implement {@code Predicate<T>}, so widening the declaration confines the entire band
     * difference to the <em>argument</em> on the next line — one token instead of an import, a type
     * and a call. Naming the concrete type here bought nothing and made this file diverge per band.
     */
    static final Predicate<CommandSourceStack> CHEAT_COMMAND =
            Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    @VisibleForTesting
    static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Read-only, and deliberately ungated: they show the caller their own data and nothing else.
        dispatcher.register(literal("mcmmo").executes(ctx -> info(ctx.getSource())));
        dispatcher.register(literal("mcstats")
                .executes(ctx -> stats(ctx.getSource()))
                .then(argument("skill", StringArgumentType.word())
                        .suggests(skillOnlySuggestions())
                        .executes(ctx -> statsForSkill(ctx.getSource(),
                                StringArgumentType.getString(ctx, "skill")))));
        // Also ungated on purpose: /mcability only ever RESTRICTS the caller (it is the build-mode
        // switch that stops Super Breaker firing while you place blocks). Gating a self-imposed
        // restriction behind op would be a worse game with no cheat closed.
        dispatcher.register(literal("mcability").executes(ctx -> ability(ctx.getSource())));

        // Cheat-adjacent: clears every super-ability cooldown on demand, which is the whole cost
        // model of the super abilities. It shipped ungated -- GitHub #8.
        dispatcher.register(literal("mcrefresh")
                .requires(CHEAT_COMMAND)
                .executes(ctx -> refresh(ctx.getSource())));

        dispatcher.register(literal("addlevels")
                .requires(CHEAT_COMMAND)
                .then(argument("skill", StringArgumentType.word())
                        .suggests(skillSuggestions())
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(McMMOCommands::addLevels))));

        dispatcher.register(literal("addxp")
                .requires(CHEAT_COMMAND)
                .then(argument("skill", StringArgumentType.word())
                        .suggests(skillSuggestions())
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(McMMOCommands::addXp))));
    }

    /**
     * Every command this class registers, and whether it needs the {@link #CHEAT_COMMAND} gate.
     *
     * <p>Exists so the audit is a data structure a test can walk rather than six registration calls
     * somebody has to re-read. Adding a command without adding it here fails
     * {@code McMMOCommandsTest#everyRegisteredCommandIsAccountedFor} — which is the point: GitHub #8
     * happened because a cheat command was registered and nobody re-checked the list.
     */
    static @NotNull Map<String, String> retiredSkills() {
        return RETIRED_SKILLS;
    }

    static @NotNull Map<String, Boolean> commandGating() {
        return Map.of(
                "mcmmo", false,
                "mcstats", false,
                "mcability", false,
                "mcrefresh", true,
                "addlevels", true,
                "addxp", true);
    }

    // --- /mcmmo -------------------------------------------------------------

    private static int info(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("mcMMO")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(" (Fabric singleplayer port)").withStyle(ChatFormatting.GRAY)),
                false);
        source.sendSuccess(() -> Component.literal("Use ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/mcstats").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" to view your skills.").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    // --- /mcstats -----------------------------------------------------------

    private static int stats(CommandSourceStack source) throws CommandSyntaxException {
        final ServerPlayer vanilla = source.getPlayerOrException();
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(vanilla.getUUID());
        if (mmoPlayer == null) {
            source.sendFailure(Component.literal("Your mcMMO data has not loaded yet."));
            return 0;
        }

        final SkillTools skillTools = McMMOMod.getSkillTools();
        source.sendSuccess(() -> Component.literal("--- mcMMO Stats ---").withStyle(ChatFormatting.GOLD),
                false);

        for (PrimarySkillType skill : SkillTools.NON_CHILD_SKILLS) {
            // GitHub #10: a skill the player switched off is not listed at all. Their level is still
            // stored and still counts toward the power level below — disabling is a pause, not a
            // reset — but a line reading "Mining: Lv.412" for a skill that pays nothing and procs
            // nothing is exactly the half-disabled state the issue warns about.
            if (!SkillGating.isSkillEnabled(skill)) {
                continue;
            }
            final int level = mmoPlayer.getSkillLevel(skill);
            final int xp = mmoPlayer.getProfile().getSkillXpLevel(skill);
            final int xpToLevel = mmoPlayer.getProfile().getXpToLevel(skill);
            final String name = skillTools.getLocalizedSkillName(skill);
            source.sendSuccess(() -> Component.literal(name + ": ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Lv." + level).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (" + xp + "/" + xpToLevel + " XP)")
                            .withStyle(ChatFormatting.GRAY)), false);
        }

        final int power = mmoPlayer.getPowerLevel();
        source.sendSuccess(() -> Component.literal("Power Level: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(String.valueOf(power)).withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("Use ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/mcstats <skill>").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" for a skill's full details.").withStyle(ChatFormatting.GRAY)),
                false);
        return 1;
    }

    // --- /mcstats <skill> ---------------------------------------------------

    /** Full-detail per-skill screen (legacy {@code /<skillname>}), rendered by {@link
     * SkillStatsRenderer}. */
    private static int statsForSkill(CommandSourceStack source, String token)
            throws CommandSyntaxException {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(source.getPlayerOrException().getUUID());
        if (mmoPlayer == null) {
            source.sendFailure(Component.literal("Your mcMMO data has not loaded yet."));
            return 0;
        }

        // A-5. Ahead of matchSkill, which would both answer null and log the name as invalid.
        final String retired = RETIRED_SKILLS.get(token.toLowerCase(Locale.ROOT));
        if (retired != null) {
            source.sendSuccess(() -> TextUtils.toText(LocaleLoader.getString(retired)), false);
            return 1;
        }

        final PrimarySkillType skill = McMMOMod.getSkillTools().matchSkill(token);
        if (skill == null) {
            source.sendFailure(Component.literal("Unknown skill: " + token));
            return 0;
        }

        // GitHub #10. Named explicitly rather than treated as an unknown skill: the player asked about
        // a real skill and deserves the actual reason, plus the level they still have and where to
        // switch it back on. Rendering the normal screen would list ranks and proc chances that are
        // all, right now, zero in practice.
        if (!SkillGating.isSkillEnabled(skill)) {
            final String name = McMMOMod.getSkillTools().getLocalizedSkillName(skill);
            final int level = mmoPlayer.getSkillLevel(skill);
            // Two different reasons, and pointing at coreskills.yml for the wrong one sends the
            // player to edit a key that will not help: a skill this Minecraft version cannot furnish
            // (no spear items, or no mace, exists here) stays off however that file is set. The set
            // of gated skills lives in SkillAvailability.GATED -- do not re-list it here.
            final String why = SkillAvailability.isSkillSupported(skill)
                    ? " Your level (" + level + ") is saved and will come back untouched — re-enable "
                            + "it under '" + CoreSkillsConfig.enabledPath(skill) + "' in coreskills.yml."
                    : " This version of Minecraft does not have the items this skill works on, so it "
                            + "cannot be switched on here. Your level (" + level + ") is saved.";
            source.sendSuccess(() -> Component.literal(name + " is disabled.").withStyle(ChatFormatting.RED)
                    .append(Component.literal(why).withStyle(ChatFormatting.GRAY)), false);
            return 1;
        }

        // The renderer emits §-coded strings (it is Minecraft-free); this is the boundary where they
        // become vanilla Text.
        SkillStatsRenderer.forSkill(skill)
                .render(mmoPlayer, line -> source.sendSuccess(() -> TextUtils.toText(line), false));
        return 1;
    }

    // --- /mcability ---------------------------------------------------------

    /** Toggles whether the caller may ready/activate super abilities ({@code abilityUse}). */
    private static int ability(CommandSourceStack source) throws CommandSyntaxException {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(source.getPlayerOrException().getUUID());
        if (mmoPlayer == null) {
            source.sendFailure(Component.literal("Your mcMMO data has not loaded yet."));
            return 0;
        }

        mmoPlayer.toggleAbilityUse();
        final boolean on = mmoPlayer.getAbilityUse();
        source.sendSuccess(() -> Component.literal("Super abilities ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(on ? "enabled" : "disabled")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    // --- /mcrefresh ---------------------------------------------------------

    /** Clears the caller's super-ability cooldowns and any active ability modes. */
    private static int refresh(CommandSourceStack source) throws CommandSyntaxException {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(source.getPlayerOrException().getUUID());
        if (mmoPlayer == null) {
            source.sendFailure(Component.literal("Your mcMMO data has not loaded yet."));
            return 0;
        }

        mmoPlayer.resetCooldowns();
        mmoPlayer.resetAbilityMode();
        source.sendSuccess(() -> Component.literal("Your super-ability cooldowns have been refreshed.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // --- /addlevels & /addxp ------------------------------------------------

    private static int addLevels(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final McMMOPlayer mmoPlayer = requireLoadedPlayer(source);
        if (mmoPlayer == null) {
            return 0;
        }

        final List<PrimarySkillType> skills = resolveSkills(source,
                StringArgumentType.getString(ctx, "skill"));
        if (skills == null) {
            return 0;
        }
        final int amount = IntegerArgumentType.getInteger(ctx, "amount");

        for (PrimarySkillType skill : skills) {
            mmoPlayer.addLevels(skill, amount);
        }
        source.sendSuccess(() -> Component.literal(
                "Added " + amount + " level(s) to " + skillLabel(skills) + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int addXp(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final McMMOPlayer mmoPlayer = requireLoadedPlayer(source);
        if (mmoPlayer == null) {
            return 0;
        }

        final List<PrimarySkillType> skills = resolveSkills(source,
                StringArgumentType.getString(ctx, "skill"));
        if (skills == null) {
            return 0;
        }
        final int amount = IntegerArgumentType.getInteger(ctx, "amount");

        for (PrimarySkillType skill : skills) {
            mmoPlayer.beginXpGain(skill, amount, XPGainReason.COMMAND, XPGainSource.COMMAND);
        }
        source.sendSuccess(() -> Component.literal(
                "Added " + amount + " XP to " + skillLabel(skills) + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // --- helpers ------------------------------------------------------------

    private static McMMOPlayer requireLoadedPlayer(CommandSourceStack source)
            throws CommandSyntaxException {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(source.getPlayerOrException().getUUID());
        if (mmoPlayer == null) {
            source.sendFailure(Component.literal("Target's mcMMO data has not loaded yet."));
        }
        return mmoPlayer;
    }

    /**
     * Resolve a skill token to one skill, or every non-child skill for {@value #ALL_TOKEN}. Returns
     * {@code null} (after sending an error to {@code source}) if the token matches no skill.
     */
    private static List<PrimarySkillType> resolveSkills(CommandSourceStack source, String token) {
        if (ALL_TOKEN.equalsIgnoreCase(token)) {
            return SkillTools.NON_CHILD_SKILLS;
        }
        final PrimarySkillType skill = McMMOMod.getSkillTools().matchSkill(token);
        if (skill == null) {
            source.sendFailure(Component.literal("Unknown skill: " + token));
            return null;
        }
        return List.of(skill);
    }

    private static String skillLabel(List<PrimarySkillType> skills) {
        if (skills.size() == 1) {
            return McMMOMod.getSkillTools().getLocalizedSkillName(skills.get(0));
        }
        return "all skills";
    }

    /** Suggests every non-child skill name (lowercased enum) plus {@value #ALL_TOKEN}. */
    private static SuggestionProvider<CommandSourceStack> skillSuggestions() {
        return (ctx, builder) -> {
            builder.suggest(ALL_TOKEN);
            for (PrimarySkillType skill : SkillTools.NON_CHILD_SKILLS) {
                builder.suggest(skill.name().toLowerCase(Locale.ROOT));
            }
            return builder.buildFuture();
        };
    }

    /** Suggests every skill name (including child skills) for {@code /mcstats <skill>}; no "all". */
    private static SuggestionProvider<CommandSourceStack> skillOnlySuggestions() {
        return (ctx, builder) -> {
            for (PrimarySkillType skill : PrimarySkillType.values()) {
                builder.suggest(skill.name().toLowerCase(Locale.ROOT));
            }
            return builder.buildFuture();
        };
    }
}
