package com.blissy.tournaments.commands;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.battle.ReadyCheckManager;
import com.blissy.tournaments.compat.PixelmonHandler;
import com.blissy.tournaments.config.TournamentsConfig;
import com.blissy.tournaments.config.UIConfigLoader;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.data.TournamentParticipant;
import com.blissy.tournaments.gui.TournamentCreationGUI;
import com.blissy.tournaments.gui.TournamentMainGUI;
import com.blissy.tournaments.elo.EloManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class TournamentCommands {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSource> dispatcher = event.getDispatcher();

        // Register the /tournament command with subcommands
        dispatcher.register(
                Commands.literal("tournament")
                        .executes(context -> {
                            // Open the tournament main GUI
                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                            TournamentMainGUI.openMainGui(player);
                            return 1;
                        })
                        .then(Commands.literal("join")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            String tournamentName = StringArgumentType.getString(context, "name");

                                            if (TournamentManager.getInstance().joinTournament(tournamentName, player)) {
                                                player.sendMessage(
                                                        new StringTextComponent("Successfully joined tournament: " + tournamentName)
                                                                .withStyle(TextFormatting.GREEN),
                                                        player.getUUID());
                                            } else {
                                                player.sendMessage(
                                                        new StringTextComponent("Failed to join tournament: " + tournamentName)
                                                                .withStyle(TextFormatting.RED),
                                                        player.getUUID());
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("leave")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    if (TournamentManager.getInstance().leaveTournament(player)) {
                                        player.sendMessage(
                                                new StringTextComponent("Successfully left tournament")
                                                        .withStyle(TextFormatting.GREEN),
                                                player.getUUID());
                                    } else {
                                        player.sendMessage(
                                                new StringTextComponent("You are not in a tournament")
                                                        .withStyle(TextFormatting.RED),
                                                player.getUUID());
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    Map<String, Tournament> tournaments = TournamentManager.getInstance().getAllTournaments();

                                    if (tournaments.isEmpty()) {
                                        player.sendMessage(
                                                new StringTextComponent("No active tournaments")
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());
                                    } else {
                                        player.sendMessage(
                                                new StringTextComponent("Active Tournaments:")
                                                        .withStyle(TextFormatting.GOLD),
                                                player.getUUID());

                                        for (Tournament tournament : tournaments.values()) {
                                            TextFormatting color;
                                            switch (tournament.getStatus()) {
                                                case WAITING:
                                                    color = TextFormatting.GREEN;
                                                    break;
                                                case IN_PROGRESS:
                                                    color = TextFormatting.GOLD;
                                                    break;
                                                case ENDED:
                                                    color = TextFormatting.RED;
                                                    break;
                                                default:
                                                    color = TextFormatting.WHITE;
                                            }

                                            player.sendMessage(
                                                    new StringTextComponent("- " + tournament.getName() +
                                                            " (" + tournament.getParticipantCount() + "/" +
                                                            tournament.getMaxParticipants() + ") [" +
                                                            tournament.getStatus() + "]")
                                                            .withStyle(color),
                                                    player.getUUID());
                                        }
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("ready")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's tournament
                                    Tournament tournament = TournamentManager.getInstance().getPlayerTournament(player);
                                    if (tournament == null) {
                                        player.sendMessage(
                                                new StringTextComponent("You are not in a tournament")
                                                        .withStyle(TextFormatting.RED),
                                                player.getUUID());
                                        return 0;
                                    }

                                    // Check if player can use ready command
                                    if (!ReadyCheckManager.canPlayerUseReadyCommand(player, tournament)) {
                                        player.sendMessage(
                                                new StringTextComponent("You don't have an active match scheduled")
                                                        .withStyle(TextFormatting.RED),
                                                player.getUUID());
                                        return 0;
                                    }

                                    // Mark player as ready
                                    ReadyCheckManager.markPlayerReady(player, tournament);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("create")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    TournamentCreationGUI.openCreationGUI(player);
                                    return 1;
                                })
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            String name = StringArgumentType.getString(context, "name");

                                            // Use default values
                                            TournamentCreationGUI.createTournament(player, name, 100, 100, 8, "SINGLE_ELIMINATION");
                                            return 1;
                                        })
                                        .then(Commands.argument("size", IntegerArgumentType.integer(2, 64))
                                                .executes(context -> {
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    String name = StringArgumentType.getString(context, "name");
                                                    int size = IntegerArgumentType.getInteger(context, "size");

                                                    // Use default values for other parameters
                                                    TournamentCreationGUI.createTournament(player, name, 100, 100, size, "SINGLE_ELIMINATION");
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("delete")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            String tournamentName = StringArgumentType.getString(context, "name");

                                            // Get the tournament
                                            Tournament tournament = TournamentManager.getInstance().getTournament(tournamentName);
                                            if (tournament == null) {
                                                player.sendMessage(
                                                        new StringTextComponent("Tournament not found: " + tournamentName)
                                                                .withStyle(TextFormatting.RED),
                                                        player.getUUID());
                                                return 0;
                                            }

                                            // Delete the tournament
                                            if (TournamentManager.getInstance().deleteTournament(tournamentName)) {
                                                player.sendMessage(
                                                        new StringTextComponent("Tournament deleted: " + tournamentName)
                                                                .withStyle(TextFormatting.GREEN),
                                                        player.getUUID());
                                            } else {
                                                player.sendMessage(
                                                        new StringTextComponent("Failed to delete tournament: " + tournamentName)
                                                                .withStyle(TextFormatting.RED),
                                                        player.getUUID());
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Log current tournaments
                                    player.sendMessage(
                                            new StringTextComponent("Debugging tournaments...")
                                                    .withStyle(TextFormatting.YELLOW),
                                            player.getUUID());

                                    Map<String, Tournament> tournaments = TournamentManager.getInstance().getAllTournaments();
                                    player.sendMessage(
                                            new StringTextComponent("Active tournaments: " + tournaments.size())
                                                    .withStyle(TextFormatting.YELLOW),
                                            player.getUUID());

                                    for (Tournament tournament : tournaments.values()) {
                                        player.sendMessage(
                                                new StringTextComponent("- " + tournament.getName() +
                                                        " (" + tournament.getParticipantCount() + " players, status: " +
                                                        tournament.getStatus() + ")")
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());

                                        // List participants
                                        player.sendMessage(
                                                new StringTextComponent("  Participants:")
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());

                                        for (TournamentParticipant participant : tournament.getParticipants()) {
                                            player.sendMessage(
                                                    new StringTextComponent("    - " + participant.getPlayerName() +
                                                            " (eliminated: " + tournament.isPlayerEliminated(participant.getPlayerId()) + ")")
                                                            .withStyle(TextFormatting.YELLOW),
                                                    player.getUUID());
                                        }

                                        // List matches
                                        player.sendMessage(
                                                new StringTextComponent("  Matches:")
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());

                                        for (TournamentMatch match : tournament.getCurrentMatches()) {
                                            player.sendMessage(
                                                    new StringTextComponent("    - " + match.getPlayer1Name() + " vs " +
                                                            match.getPlayer2Name() + " (status: " + match.getStatus() + ")")
                                                            .withStyle(TextFormatting.YELLOW),
                                                    player.getUUID());
                                        }
                                    }

                                    // Debug active battles
                                    player.sendMessage(
                                            new StringTextComponent("Debugging active tournament battles...")
                                                    .withStyle(TextFormatting.YELLOW),
                                            player.getUUID());

                                    PixelmonHandler.debugTournamentBattles();

                                    return 1;
                                })
                        )
                        .then(Commands.literal("forcebattleresult")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .then(Commands.argument("tournamentName", StringArgumentType.string())
                                        .then(Commands.argument("winner", StringArgumentType.string())
                                                .then(Commands.argument("loser", StringArgumentType.string())
                                                        .executes(context -> {
                                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                            String tournamentName = StringArgumentType.getString(context, "tournamentName");
                                                            String winnerName = StringArgumentType.getString(context, "winner");
                                                            String loserName = StringArgumentType.getString(context, "loser");

                                                            Tournament tournament = TournamentManager.getInstance().getTournament(tournamentName);
                                                            if (tournament == null) {
                                                                player.sendMessage(
                                                                        new StringTextComponent("Tournament not found: " + tournamentName)
                                                                                .withStyle(TextFormatting.RED),
                                                                        player.getUUID());
                                                                return 0;
                                                            }

                                                            // Find player UUIDs by name
                                                            UUID winnerId = null;
                                                            UUID loserId = null;

                                                            for (TournamentParticipant participant : tournament.getParticipants()) {
                                                                if (participant.getPlayerName().equalsIgnoreCase(winnerName)) {
                                                                    winnerId = participant.getPlayerId();
                                                                } else if (participant.getPlayerName().equalsIgnoreCase(loserName)) {
                                                                    loserId = participant.getPlayerId();
                                                                }
                                                            }

                                                            if (winnerId == null || loserId == null) {
                                                                player.sendMessage(
                                                                        new StringTextComponent("Could not find players in tournament: " +
                                                                                winnerName + " and/or " + loserName)
                                                                                .withStyle(TextFormatting.RED),
                                                                        player.getUUID());
                                                                return 0;
                                                            }

                                                            // Force record the match result
                                                            boolean resultRecorded = tournament.recordMatchResult(winnerId, loserId);

                                                            if (resultRecorded) {
                                                                // Update ELO ratings
                                                                Tournaments.ELO_MANAGER.recordMatch(winnerId, loserId);

                                                                // Force eliminate the loser
                                                                tournament.eliminatePlayer(loserId);

                                                                player.sendMessage(
                                                                        new StringTextComponent("Force recorded match result: " +
                                                                                winnerName + " defeats " + loserName)
                                                                                .withStyle(TextFormatting.GREEN),
                                                                        player.getUUID());

                                                                tournament.broadcastMessage(winnerName + " has defeated " +
                                                                        loserName + " and advances to the next round!");

                                                                return 1;
                                                            } else {
                                                                player.sendMessage(
                                                                        new StringTextComponent("Failed to record match result")
                                                                                .withStyle(TextFormatting.RED),
                                                                        player.getUUID());
                                                                return 0;
                                                            }
                                                        })
                                                )))
                        )
                        .then(Commands.literal("reloadconfig")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    try {
                                        // Reload the config
                                        UIConfigLoader.loadConfig(null);
                                        UIConfigLoader.saveConfig();

                                        player.sendMessage(
                                                new StringTextComponent("Tournament UI configuration reloaded successfully")
                                                        .withStyle(TextFormatting.GREEN),
                                                player.getUUID());

                                        Tournaments.LOGGER.info("UI config reloaded by {}", player.getName().getString());
                                    } catch (Exception e) {
                                        Tournaments.LOGGER.error("Failed to reload UI config", e);
                                        player.sendMessage(
                                                new StringTextComponent("Error reloading UI configuration: " + e.getMessage())
                                                        .withStyle(TextFormatting.RED),
                                                player.getUUID());
                                    }

                                    return 1;
                                })
                        )
                        .then(Commands.literal("setentry")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's current position
                                    double x = player.getX();
                                    double y = player.getY();
                                    double z = player.getZ();
                                    String dimension = player.level.dimension().location().toString();

                                    // Set config values (temporarily)
                                    TournamentsConfig.COMMON.entryX.set(x);
                                    TournamentsConfig.COMMON.entryY.set(y);
                                    TournamentsConfig.COMMON.entryZ.set(z);
                                    TournamentsConfig.COMMON.entryDimension.set(dimension);

                                    // Store the values in a server NBT data file
                                    storeLocationInServerData("tournament_entry", x, y, z, dimension);

                                    player.sendMessage(
                                            new StringTextComponent("Tournament entry point set to your current location")
                                                    .withStyle(TextFormatting.GREEN),
                                            player.getUUID());

                                    return 1;
                                })
                        )
                        .then(Commands.literal("setexit")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's current position
                                    double x = player.getX();
                                    double y = player.getY();
                                    double z = player.getZ();
                                    String dimension = player.level.dimension().location().toString();

                                    // Set config values (temporarily)
                                    TournamentsConfig.COMMON.exitX.set(x);
                                    TournamentsConfig.COMMON.exitY.set(y);
                                    TournamentsConfig.COMMON.exitZ.set(z);
                                    TournamentsConfig.COMMON.exitDimension.set(dimension);

                                    // Store the values in a server NBT data file
                                    storeLocationInServerData("tournament_exit", x, y, z, dimension);

                                    player.sendMessage(
                                            new StringTextComponent("Tournament exit point set to your current location")
                                                    .withStyle(TextFormatting.GREEN),
                                            player.getUUID());

                                    return 1;
                                })
                        )
                        .then(Commands.literal("setmatchtp1")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's current position
                                    double x = player.getX();
                                    double y = player.getY();
                                    double z = player.getZ();
                                    String dimension = player.level.dimension().location().toString();

                                    // Store the values in a server NBT data file
                                    storeLocationInServerData("tournament_match_pos1", x, y, z, dimension);

                                    player.sendMessage(
                                            new StringTextComponent("Tournament match position 1 set to your current location")
                                                    .withStyle(TextFormatting.GREEN),
                                            player.getUUID());

                                    return 1;
                                })
                        )
                        .then(Commands.literal("setmatchtp2")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's current position
                                    double x = player.getX();
                                    double y = player.getY();
                                    double z = player.getZ();
                                    String dimension = player.level.dimension().location().toString();

                                    // Store the values in a server NBT data file
                                    storeLocationInServerData("tournament_match_pos2", x, y, z, dimension);

                                    player.sendMessage(
                                            new StringTextComponent("Tournament match position 2 set to your current location")
                                                    .withStyle(TextFormatting.GREEN),
                                            player.getUUID());

                                    return 1;
                                })
                        )
                        .then(Commands.literal("resetelo")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    Tournaments.ELO_MANAGER.resetRankings();
                                    context.getSource().sendSuccess(
                                            new StringTextComponent("ELO rankings have been reset"),
                                            true);
                                    return 1;
                                })
                        )

        );

    }

    /**
     * Store a location in the server's NBT data
     */
    private static void storeLocationInServerData(String id, double x, double y, double z, String dimension) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;

            // Get or create the data directory
            File dataDir = new File(server.getServerDirectory(), "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            // Create data file
            File locationFile = new File(dataDir, "tournaments_locations.dat");

            // Create or load NBT data
            CompoundNBT root = new CompoundNBT();
            if (locationFile.exists()) {
                try (FileInputStream stream = new FileInputStream(locationFile)) {
                    root = CompressedStreamTools.readCompressed(stream);
                } catch (Exception e) {
                    Tournaments.LOGGER.error("Failed to read locations file", e);
                    // Create new if failed to read
                    root = new CompoundNBT();
                }
            }

            // Add or update location data
            CompoundNBT locationData = new CompoundNBT();
            locationData.putDouble("x", x);
            locationData.putDouble("y", y);
            locationData.putDouble("z", z);
            locationData.putString("dimension", dimension);

            root.put(id, locationData);

            // Save the data
            try (FileOutputStream stream = new FileOutputStream(locationFile)) {
                CompressedStreamTools.writeCompressed(root, stream);
                Tournaments.LOGGER.info("Saved tournament location: {} at ({}, {}, {}) in {}",
                        id, x, y, z, dimension);
            } catch (Exception e) {
                Tournaments.LOGGER.error("Failed to save locations file", e);
            }
        } catch (Exception e) {
            Tournaments.LOGGER.error("Failed to store location data", e);
        }
    }
}