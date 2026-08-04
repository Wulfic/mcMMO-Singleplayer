package com.gmail.nossr50.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.gmail.nossr50.util.McTestRegistries;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.server.command.ServerCommandSource;
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

    private static CommandDispatcher<ServerCommandSource> registered() {
        final CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        McMMOCommands.registerAll(dispatcher);
        return dispatcher;
    }

    @Test
    void everyCheatCommandCarriesTheOpGateAndNoOtherCommandDoes() {
        final CommandDispatcher<ServerCommandSource> dispatcher = registered();

        for (Map.Entry<String, Boolean> entry : McMMOCommands.commandGating().entrySet()) {
            final CommandNode<ServerCommandSource> node =
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
        for (CommandNode<ServerCommandSource> node : registered().getRoot().getChildren()) {
            registeredNames.add(node.getName());
        }

        assertEquals(new TreeSet<>(McMMOCommands.commandGating().keySet()), registeredNames,
                "every registered command must have a recorded gating decision");
    }
}
