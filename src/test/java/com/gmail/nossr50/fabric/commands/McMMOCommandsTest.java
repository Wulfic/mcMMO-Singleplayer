package com.gmail.nossr50.fabric.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.McTestRegistries;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins <em>which</em> mcMMO commands sit behind an op-level check (GitHub #8).
 *
 * <p>{@code /mcrefresh} shipped ungated: any player could clear every super-ability cooldown on
 * demand, which is the entire cost model of the super abilities. It was not gated because nobody
 * re-read the registration list when it was added — so this test walks the registered tree rather
 * than trusting a reviewer to, and {@link McMMOCommands#commandGating()} is the list it walks.
 */
class McMMOCommandsTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    private static CommandDispatcher<CommandSourceStack> registered() {
        final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        McMMOCommands.registerAll(dispatcher);
        return dispatcher;
    }

    @Test
    void everyCheatCommandCarriesTheOpGateAndNoOtherCommandDoes() {
        final CommandDispatcher<CommandSourceStack> dispatcher = registered();

        for (Map.Entry<String, Boolean> entry : McMMOCommands.commandGating().entrySet()) {
            final CommandNode<CommandSourceStack> node =
                    dispatcher.getRoot().getChild(entry.getKey());
            assertNotNull(node, "/" + entry.getKey() + " is not registered at all");

            if (entry.getValue()) {
                // PermissionSourcePredicate is a record, so this compares by value: the node must
                // carry the same op-level requirement, not merely some requirement.
                assertEquals(McMMOCommands.CHEAT_COMMAND, node.getRequirement(),
                        "/" + entry.getKey() + " hands out progress or removes a cost and must be "
                                + "behind the op gate");
            } else {
                // Asserted OFF the reference point too. Without this half, gating EVERY command
                // would pass the loop above and quietly make /mcstats an admin command.
                assertNotEquals(McMMOCommands.CHEAT_COMMAND, node.getRequirement(),
                        "/" + entry.getKey() + " is read-only or self-restricting; gating it costs "
                                + "the player a feature and closes no cheat");
            }
        }
    }

    @Test
    void everyRegisteredCommandIsAccountedFor() {
        // The actual defect behind #8 was not a wrong decision about /mcrefresh — it was that a
        // command got registered and the gating question was never asked. A new command that is not
        // in commandGating() fails here, which forces the question to be asked exactly once.
        final Set<String> registeredNames = new TreeSet<>();
        for (CommandNode<CommandSourceStack> node : registered().getRoot().getChildren()) {
            registeredNames.add(node.getName());
        }

        assertEquals(new TreeSet<>(McMMOCommands.commandGating().keySet()), registeredNames,
                "every registered command must have a recorded gating decision");
    }

    /**
     * A-5. {@code /mcstats agility} must answer <em>"the skill was retired, here is where its perks
     * went"</em> rather than <em>"Unknown skill"</em>.
     *
     * <p>Three properties, and the middle one is the one worth having:
     *
     * <ol>
     *   <li><b>The row exists.</b> Deleting it goes red rather than silently regressing a player
     *       who levelled Agility for weeks back to an error message.</li>
     *   <li><b>⚠️ No token in the table names a LIVE skill.</b> The branch is checked ahead of
     *       {@code matchSkill}, so a row added for a skill that still exists would shadow it: the
     *       real screen stops rendering and the player is told their working skill was retired.
     *       Nothing else in the codebase would notice.</li>
     *   <li><b>The locale key resolves.</b> {@code LocaleLoader} answers {@code !key!} for a miss
     *       rather than throwing, so a typo'd or deleted key ships as literal punctuation in chat.
     *       These keys are literals in a table — no enum-derived completeness check covers them.</li>
     * </ol>
     */
    @Test
    void everyRetiredSkillNamesALiveLocaleStringAndNoLiveSkill() {
        final Map<String, String> retired = McMMOCommands.retiredSkills();

        assertTrue(retired.containsKey("agility"),
                "Agility was retired 2026-08-17; typing its name must still explain where Fleet "
                        + "Footed and Second Wind went, not answer \"Unknown skill\"");

        for (Map.Entry<String, String> entry : retired.entrySet()) {
            final String token = entry.getKey();
            assertEquals(token.toLowerCase(Locale.ROOT), token,
                    "the table is looked up with a lower-cased token, so its keys must be lower case");

            for (PrimarySkillType live : PrimarySkillType.values()) {
                assertNotEquals(live.name().toLowerCase(Locale.ROOT), token,
                        "/mcstats " + token + " would report a LIVE skill as retired and never "
                                + "render its screen -- the retired branch runs before matchSkill");
            }

            final String message = LocaleLoader.getString(entry.getValue());
            assertFalse(message.startsWith("!") && message.endsWith("!"),
                    entry.getValue() + " does not resolve; LocaleLoader returned " + message);
            for (String parent : List.of("Parkour", "Swimming", "Flying")) {
                assertTrue(message.contains(parent),
                        "the retirement message must say where the perks went; " + parent
                                + " is missing from: " + message);
            }
        }
    }
}
