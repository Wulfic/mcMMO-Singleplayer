package com.gmail.nossr50.commands;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.SkillTools;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
 *   <li>{@code /addlevels <skill|all> <amount>} — admin: grant skill levels (op level 2).</li>
 *   <li>{@code /addxp <skill|all> <amount>} — admin: grant raw XP through the real gain pipeline.</li>
 * </ul>
 */
public final class McMMOCommands {

    /** "all" targets every non-child skill in the level/xp admin commands. */
    private static final String ALL_TOKEN = "all";

    private McMMOCommands() {
    }

    /** Register every mcMMO command. Called once at mod load from {@code McMMOMod#onInitialize}. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> registerAll(dispatcher));
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("mcmmo").executes(ctx -> info(ctx.getSource())));
        dispatcher.register(literal("mcstats").executes(ctx -> stats(ctx.getSource())));

        dispatcher.register(literal("addlevels")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(argument("skill", StringArgumentType.word())
                        .suggests(skillSuggestions())
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(McMMOCommands::addLevels))));

        dispatcher.register(literal("addxp")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(argument("skill", StringArgumentType.word())
                        .suggests(skillSuggestions())
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(McMMOCommands::addXp))));
    }

    // --- /mcmmo -------------------------------------------------------------

    private static int info(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("mcMMO")
                .formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal(" (Fabric singleplayer port)").formatted(Formatting.GRAY)),
                false);
        source.sendFeedback(() -> Text.literal("Use ").formatted(Formatting.GRAY)
                .append(Text.literal("/mcstats").formatted(Formatting.YELLOW))
                .append(Text.literal(" to view your skills.").formatted(Formatting.GRAY)), false);
        return 1;
    }

    // --- /mcstats -----------------------------------------------------------

    private static int stats(ServerCommandSource source) throws CommandSyntaxException {
        final ServerPlayerEntity vanilla = source.getPlayerOrThrow();
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(vanilla.getUuid());
        if (mmoPlayer == null) {
            source.sendError(Text.literal("Your mcMMO data has not loaded yet."));
            return 0;
        }

        final SkillTools skillTools = McMMOMod.getSkillTools();
        source.sendFeedback(() -> Text.literal("--- mcMMO Stats ---").formatted(Formatting.GOLD),
                false);

        for (PrimarySkillType skill : SkillTools.NON_CHILD_SKILLS) {
            final int level = mmoPlayer.getSkillLevel(skill);
            final int xp = mmoPlayer.getProfile().getSkillXpLevel(skill);
            final int xpToLevel = mmoPlayer.getProfile().getXpToLevel(skill);
            final String name = skillTools.getLocalizedSkillName(skill);
            source.sendFeedback(() -> Text.literal(name + ": ").formatted(Formatting.YELLOW)
                    .append(Text.literal("Lv." + level).formatted(Formatting.GREEN))
                    .append(Text.literal(" (" + xp + "/" + xpToLevel + " XP)")
                            .formatted(Formatting.GRAY)), false);
        }

        final int power = mmoPlayer.getPowerLevel();
        source.sendFeedback(() -> Text.literal("Power Level: ").formatted(Formatting.GOLD)
                .append(Text.literal(String.valueOf(power)).formatted(Formatting.GREEN)), false);
        return 1;
    }

    // --- /addlevels & /addxp ------------------------------------------------

    private static int addLevels(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        final ServerCommandSource source = ctx.getSource();
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
        source.sendFeedback(() -> Text.literal(
                "Added " + amount + " level(s) to " + skillLabel(skills) + ".")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int addXp(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        final ServerCommandSource source = ctx.getSource();
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
        source.sendFeedback(() -> Text.literal(
                "Added " + amount + " XP to " + skillLabel(skills) + ".")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    // --- helpers ------------------------------------------------------------

    private static McMMOPlayer requireLoadedPlayer(ServerCommandSource source)
            throws CommandSyntaxException {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(source.getPlayerOrThrow().getUuid());
        if (mmoPlayer == null) {
            source.sendError(Text.literal("Target's mcMMO data has not loaded yet."));
        }
        return mmoPlayer;
    }

    /**
     * Resolve a skill token to one skill, or every non-child skill for {@value #ALL_TOKEN}. Returns
     * {@code null} (after sending an error to {@code source}) if the token matches no skill.
     */
    private static List<PrimarySkillType> resolveSkills(ServerCommandSource source, String token) {
        if (ALL_TOKEN.equalsIgnoreCase(token)) {
            return SkillTools.NON_CHILD_SKILLS;
        }
        final PrimarySkillType skill = McMMOMod.getSkillTools().matchSkill(token);
        if (skill == null) {
            source.sendError(Text.literal("Unknown skill: " + token));
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
    private static SuggestionProvider<ServerCommandSource> skillSuggestions() {
        return (ctx, builder) -> {
            builder.suggest(ALL_TOKEN);
            for (PrimarySkillType skill : SkillTools.NON_CHILD_SKILLS) {
                builder.suggest(skill.name().toLowerCase(Locale.ROOT));
            }
            return builder.buildFuture();
        };
    }
}
