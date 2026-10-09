package dev.uncanny.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.uncanny.config.UncannyConfig;
import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.generation.ProceduralDimensionGenerator;
import dev.uncanny.generation.RoomTemplates;
import dev.uncanny.lore.LedgerManager;
import dev.uncanny.lore.LoreManager;
import dev.uncanny.lore.LoreStage;
import dev.uncanny.lore.SealManager;
import dev.uncanny.net.UncannyPayloads;
import dev.uncanny.player.PlayerProgress;
import dev.uncanny.player.UncannyPlayerData;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /uncanny
 *
 * A testing and support command, level 2 only. It is not part of the experience and
 * the game never mentions it, but without it there is no way to check that a seal
 * actually moved or that the Ledger is generating pages for the right player.
 *
 *   /uncanny status            what the mod currently believes
 *   /uncanny goto <layer>      move to a layer, ignoring the gates
 *   /uncanny stage <1-9>       force a player's lore stage
 *   /uncanny ledger            open the Ledger for yourself, as it is now
 *   /uncanny weaken <seal>     damage one seal immediately
 *   /uncanny templates         list the registered room templates
 *   /uncanny evaluate          why each anomaly can or cannot run right now
 *   /uncanny force <event>     run one anomaly immediately (testing only)
 *   /uncanny instability <0-100>  set the hidden instability (testing only)
 *
 * The last three exist so the horror system can be balanced without waiting an
 * hour for a roll. They are permission level 2: outside a test session, they
 * do not exist.
 */
public final class UncannyCommand {

    private UncannyCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("uncanny")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("status").executes(UncannyCommand::status))
                .then(CommandManager.literal("templates").executes(UncannyCommand::templates))
                .then(CommandManager.literal("ledger").executes(UncannyCommand::ledger))
                .then(CommandManager.literal("goto")
                        .then(CommandManager.argument("layer", StringArgumentType.word())
                                .executes(UncannyCommand::gotoLayer)))
                .then(CommandManager.literal("stage")
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 9))
                                .executes(UncannyCommand::setStage)))
                .then(CommandManager.literal("weaken")
                        .then(CommandManager.argument("seal", IntegerArgumentType.integer(1, 7))
                                .executes(UncannyCommand::weaken)))
                .then(CommandManager.literal("evaluate")
                        .executes(UncannyCommand::evaluate))
                .then(CommandManager.literal("force")
                        .then(CommandManager.argument("event", StringArgumentType.word())
                                .executes(UncannyCommand::force)))
                .then(CommandManager.literal("instability")
                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0, 100))
                                .executes(UncannyCommand::instability))));
    }

    private static int status(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFeedback(() -> Text.literal("player only"), false);
            return 0;
        }
        UncannyWorldState state = UncannyWorldState.get(player.getServerWorld());
        UncannyPlayerData data = state.player(player.getUuid());
        LoreStage stage = PlayerProgress.stage(data, state);

        context.getSource().sendFeedback(() -> Text.literal(
                "uncanny: stage " + stage.level() + " (" + stage.label() + ")"), false);
        context.getSource().sendFeedback(() -> Text.literal(
                "entry " + data.entryLabel() + ", ledger state " + data.ledgerState
                        + ", anchor " + data.anchorStatus), false);
        context.getSource().sendFeedback(() -> Text.literal(
                "lore " + data.discoveredLore.size() + ", clues " + data.clues.size()
                        + ", anchors " + data.anchorsSeen.size()
                        + ", layers " + data.firstDimensionEntry.size()), false);
        context.getSource().sendFeedback(() -> Text.literal(
                "anomalies " + data.anomaliesSeen + " (major " + data.majorAnomaliesSeen + ")"
                        + ", seals " + SealManager.describe(state)), false);
        context.getSource().sendFeedback(() -> Text.literal(
                "layer " + DimensionManager.current(player).path()
                        + ", queued chunks " + ProceduralDimensionGenerator.queued()
                        + ", templates " + RoomTemplates.count()
                        + ", documents " + LoreManager.size()), false);
        context.getSource().sendFeedback(() -> Text.literal(String.format(java.util.Locale.ROOT,
                "instability %.2f, underground %d%%, areas %d, doors %d, anomaly spots %d",
                dev.uncanny.player.RealityInstability.value(data),
                (int) (data.memory.undergroundRatio() * 100),
                data.memory.areaVisits.size(), data.memory.doorUses.size(),
                state.anomalyLocationCount())), false);
        return 1;
    }

    private static int templates(CommandContext<ServerCommandSource> context) {
        for (String id : RoomTemplates.ids()) {
            context.getSource().sendFeedback(() -> Text.literal("  " + id), false);
        }
        return RoomTemplates.count();
    }

    private static int ledger(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        UncannyWorldState state = UncannyWorldState.get(player.getServerWorld());
        UncannyPlayerData data = state.player(player.getUuid());
        if (data.ledgerState < 1) {
            LedgerManager.advance(data, state);
        }
        UncannyPayloads.sendLedger(player, state, false);
        return 1;
    }

    private static int gotoLayer(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "layer").toLowerCase();
        UncannyDimension dimension = UncannyDimension.fromPath(name);
        if (dimension == null || dimension == UncannyDimension.OVERWORLD) {
            context.getSource().sendFeedback(() -> Text.literal("unknown layer: " + name), false);
            return 0;
        }
        boolean sent = DimensionManager.send(player, dimension);
        context.getSource().sendFeedback(() -> Text.literal(sent ? "sent to " + name : "no such world: " + name),
                false);
        return sent ? 1 : 0;
    }

    private static int setStage(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        int level = IntegerArgumentType.getInteger(context, "level");
        UncannyWorldState state = UncannyWorldState.get(player.getServerWorld());
        UncannyPlayerData data = state.player(player.getUuid());
        // The stage is derived, so faking it means adding discoveries. Crude, but it
        // is a debug command and it does not lie about how the value is stored.
        int target = level * 6;
        while (data.clues.size() < target) {
            data.markClue("debug_" + data.clues.size());
        }
        state.markDirty();
        context.getSource().sendFeedback(() -> Text.literal(
                "stage now " + PlayerProgress.stage(data, state).level()), false);
        return 1;
    }

    /**
     * Why every anomaly can or cannot run right now. The balancing surface, on
     * demand: grace period, stage, cooldowns, labeled conditions, probability.
     */
    private static int evaluate(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFeedback(() -> Text.literal("player only"), false);
            return 0;
        }
        UncannyWorldState state = UncannyWorldState.get(player.getServerWorld());
        long tick = player.getServerWorld().getTime();
        for (String line : dev.uncanny.events.UncannyEventManager.explain(player, state, tick)) {
            context.getSource().sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    /**
     * Runs one anomaly immediately, bypassing every gate. Development only:
     * permission level 2, never reachable in normal play, and it books the same
     * cooldowns a real run would.
     */
    private static int force(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFeedback(() -> Text.literal("player only"), false);
            return 0;
        }
        String id = StringArgumentType.getString(context, "event");
        if (!UncannyConfig.get().devForceCommands) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "devForceCommands is off in config/uncanny.json (leave it off)"), false);
            return 0;
        }
        dev.uncanny.events.UncannyEvent event = dev.uncanny.events.UncannyEventManager.find(id);
        if (event == null) {
            context.getSource().sendFeedback(() -> Text.literal("unknown event: " + id
                    + " (try /uncanny templates or the ids in AllEvents)"), false);
            return 0;
        }
        UncannyWorldState state = UncannyWorldState.get(player.getServerWorld());
        long tick = player.getServerWorld().getTime();
        long seed = dev.uncanny.util.SeedUtil.mix(player.getUuid().getMostSignificantBits(), tick, 0xF0CE);
        dev.uncanny.events.EventContext eventContext = new dev.uncanny.events.EventContext(
                player.getServer(), player.getServerWorld(), player, state,
                state.player(player.getUuid()), tick, seed);
        try {
            event.run(eventContext);
            context.getSource().sendFeedback(() -> Text.literal("ran " + id), false);
            return 1;
        } catch (RuntimeException e) {
            context.getSource().sendFeedback(
                    () -> Text.literal("failed: " + e.getMessage()), false);
            return 0;
        }
    }

    /** Sets the hidden instability (0-100 -> 0.0-1.0). Testing only. */
    private static int instability(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFeedback(() -> Text.literal("player only"), false);
            return 0;
        }
        if (!UncannyConfig.get().devForceCommands) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "devForceCommands is off in config/uncanny.json (leave it off)"), false);
            return 0;
        }
        int value = IntegerArgumentType.getInteger(context, "value");
        UncannyWorldState state = UncannyWorldState.get(player.getServerWorld());
        UncannyPlayerData data = state.player(player.getUuid());
        dev.uncanny.player.RealityInstability.set(data, value / 100.0);
        state.markDirty();
        String reply = String.format(java.util.Locale.ROOT, "instability now %.2f",
                dev.uncanny.player.RealityInstability.value(data));
        context.getSource().sendFeedback(() -> Text.literal(reply), false);
        return value;
    }

    private static int weaken(CommandContext<ServerCommandSource> context) {
        int seal = IntegerArgumentType.getInteger(context, "seal") - 1;
        UncannyWorldState state = UncannyWorldState.get(context.getSource().getServer());
        // weaken() returns nothing; the new value is read back from the world state,
        // because that is the value every other system will see.
        SealManager.weaken(state, seal, 100);
        int integrity = state.sealIntegrity(seal);
        context.getSource().sendFeedback(() -> Text.literal(
                "SEAL " + UncannyWorldState.sealName(seal) + " integrity " + integrity
                        + " (" + SealManager.describe(state) + ")"), false);
        return 1;
    }
}
