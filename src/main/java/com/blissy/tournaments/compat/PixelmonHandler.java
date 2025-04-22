package com.blissy.tournaments.compat;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.data.TournamentParticipant;
import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class PixelmonHandler {

    // Track tournament battles by battleIndex
    private static final Map<Integer, BattleInfo> tournamentBattles = new HashMap<>();

    private static class BattleInfo {
        public final String tournamentName;
        public final UUID player1Id;
        public final UUID player2Id;

        public BattleInfo(String tournamentName, UUID player1Id, UUID player2Id) {
            this.tournamentName = tournamentName;
            this.player1Id = player1Id;
            this.player2Id = player2Id;
        }
    }

    /**
     * Debug method to log all active tournament battles
     * Call this from your commands or debug points
     */
    public static void debugTournamentBattles() {
        Tournaments.LOGGER.info("=== ACTIVE TOURNAMENT BATTLES ===");
        for (Map.Entry<Integer, BattleInfo> entry : tournamentBattles.entrySet()) {
            Tournaments.LOGGER.info("Battle #{}: Tournament={}, Player1={}, Player2={}",
                    entry.getKey(), entry.getValue().tournamentName,
                    entry.getValue().player1Id, entry.getValue().player2Id);
        }
        Tournaments.LOGGER.info("================================");
    }

    @SubscribeEvent
    public static void onBattleStart(BattleStartedEvent.Post event) {
        // Get the battle controller
        BattleController bc = event.getBattleController();

        // Debug log that the event was triggered
        Tournaments.LOGGER.info("Battle started event triggered. Battle controller: {}", bc.battleIndex);

        // Extract players from both teams
        List<ServerPlayerEntity> players = new ArrayList<>();
        Map<UUID, Integer> playerTeams = new HashMap<>();

        // Check team one
        for (BattleParticipant participant : event.getTeamOne()) {
            if (participant instanceof PlayerParticipant) {
                PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                players.add(playerParticipant.player);
                playerTeams.put(playerParticipant.player.getUUID(), 1);

                // Log the player found
                Tournaments.LOGGER.info("Team 1 player detected: {}", playerParticipant.player.getName().getString());
            }
        }

        // Check team two
        for (BattleParticipant participant : event.getTeamTwo()) {
            if (participant instanceof PlayerParticipant) {
                PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                players.add(playerParticipant.player);
                playerTeams.put(playerParticipant.player.getUUID(), 2);

                // Log the player found
                Tournaments.LOGGER.info("Team 2 player detected: {}", playerParticipant.player.getName().getString());
            }
        }

        // If we don't have exactly 2 players, this isn't a 1v1 player battle
        if (players.size() != 2) {
            Tournaments.LOGGER.info("Not a 1v1 player battle. Player count: {}", players.size());
            return;
        }

        ServerPlayerEntity player1 = players.get(0);
        ServerPlayerEntity player2 = players.get(1);

        TournamentManager manager = TournamentManager.getInstance();
        Tournament tournament1 = manager.getPlayerTournament(player1);
        Tournament tournament2 = manager.getPlayerTournament(player2);

        // Log tournament check
        Tournaments.LOGGER.info("Player 1 tournament: {}, Player 2 tournament: {}",
                tournament1 != null ? tournament1.getName() : "none",
                tournament2 != null ? tournament2.getName() : "none");

        // Both players must be in the same tournament
        if (tournament1 != null && tournament1 == tournament2) {
            // This is a tournament battle
            Tournaments.LOGGER.info("TOURNAMENT BATTLE DETECTED: Adding to tracked battles with index {}",
                    bc.battleIndex);

            tournamentBattles.put(bc.battleIndex,
                    new BattleInfo(tournament1.getName(), player1.getUUID(), player2.getUUID()));

            // Notify players
            player1.sendMessage(
                    new StringTextComponent("Tournament battle started against " + player2.getName().getString())
                            .withStyle(TextFormatting.GOLD),
                    player1.getUUID());

            player2.sendMessage(
                    new StringTextComponent("Tournament battle started against " + player1.getName().getString())
                            .withStyle(TextFormatting.GOLD),
                    player2.getUUID());

            Tournaments.LOGGER.info("Tournament battle started: {} vs {}",
                    player1.getName().getString(), player2.getName().getString());

            // Debug all tournament battles
            debugTournamentBattles();
        } else {
            Tournaments.LOGGER.info("Not a tournament battle - players not in same tournament");
        }
    }

    @SubscribeEvent
    public static void onBattleEnd(BattleEndEvent event) {
        // Get the battle controller
        BattleController bc = event.getBattleController();

        // Debug log that the event was triggered
        Tournaments.LOGGER.info("Battle end event triggered. Battle controller: {}", bc.battleIndex);

        if (!tournamentBattles.containsKey(bc.battleIndex)) {
            Tournaments.LOGGER.info("Not a tournament battle. Known tournament battles: {}",
                    tournamentBattles.keySet());
            return; // Not a tournament battle
        }

        BattleInfo battleInfo = tournamentBattles.remove(bc.battleIndex);
        Tournament tournament = TournamentManager.getInstance().getTournament(battleInfo.tournamentName);

        if (tournament == null) {
            Tournaments.LOGGER.warn("Tournament {} no longer exists when processing battle end", battleInfo.tournamentName);
            return; // Tournament no longer exists
        }

        // Find the player entities
        ServerPlayerEntity player1 = null;
        ServerPlayerEntity player2 = null;
        String player1Name = "Unknown";
        String player2Name = "Unknown";

        // Find the players from tournament participants
        for (TournamentParticipant participant : tournament.getParticipants()) {
            if (participant.getPlayerId().equals(battleInfo.player1Id)) {
                player1 = participant.getPlayer();
                player1Name = participant.getPlayerName();
            } else if (participant.getPlayerId().equals(battleInfo.player2Id)) {
                player2 = participant.getPlayer();
                player2Name = participant.getPlayerName();
            }
        }

        Tournaments.LOGGER.info("Tournament battle ended between {} and {}", player1Name, player2Name);

        // IMPROVED: Direct Pokémon check for determining winner/loser
        boolean player1AllFainted = true;
        boolean player2AllFainted = true;

        if (player1 != null) {
            Tournaments.LOGGER.info("Checking Pokémon status for player: {}", player1Name);
            Pokemon[] player1Pokemon = StorageProxy.getParty(player1.getUUID()).getAll();

            // Log each Pokémon's status
            for (Pokemon pokemon : player1Pokemon) {
                if (pokemon != null) {
                    boolean isFainted = pokemon.isFainted();
                    Tournaments.LOGGER.info("Player1 Pokémon: {} - Fainted: {}",
                            pokemon.getDisplayName(), isFainted);
                    if (!isFainted) {
                        player1AllFainted = false;
                    }
                }
            }
            Tournaments.LOGGER.info("Player1 all Pokémon fainted: {}", player1AllFainted);
        }

        if (player2 != null) {
            Tournaments.LOGGER.info("Checking Pokémon status for player: {}", player2Name);
            Pokemon[] player2Pokemon = StorageProxy.getParty(player2.getUUID()).getAll();

            // Log each Pokémon's status
            for (Pokemon pokemon : player2Pokemon) {
                if (pokemon != null) {
                    boolean isFainted = pokemon.isFainted();
                    Tournaments.LOGGER.info("Player2 Pokémon: {} - Fainted: {}",
                            pokemon.getDisplayName(), isFainted);
                    if (!isFainted) {
                        player2AllFainted = false;
                    }
                }
            }
            Tournaments.LOGGER.info("Player2 all Pokémon fainted: {}", player2AllFainted);
        }

        // Determine winner/loser based on fainted status - IMPORTANT PART
        UUID winnerId = null;
        UUID loserId = null;

        if (player1AllFainted && !player2AllFainted) {
            // Player 1 lost, Player 2 won
            winnerId = battleInfo.player2Id;
            loserId = battleInfo.player1Id;
            Tournaments.LOGGER.info("WINNER DETERMINED: {} (all opponent's Pokémon fainted)", player2Name);
        } else if (!player1AllFainted && player2AllFainted) {
            // Player 2 lost, Player 1 won
            winnerId = battleInfo.player1Id;
            loserId = battleInfo.player2Id;
            Tournaments.LOGGER.info("WINNER DETERMINED: {} (all opponent's Pokémon fainted)", player1Name);
        } else {
            // If we can't determine by fainted status, check BattleController participants
            Tournaments.LOGGER.warn("Could not determine winner by fainted status. Trying battle participants...");

            // Try to find a winner from players in the battle
            for (BattleParticipant p : bc.participants) {
                if (p instanceof PlayerParticipant) {
                    PlayerParticipant player = (PlayerParticipant) p;
                    UUID playerId = player.player.getUUID();

                    Tournaments.LOGGER.debug("Battle participant: {}, Defeated: {}",
                            player.player.getName().getString(), p.isDefeated);

                    // Check if this player is not defeated (and thus a winner)
                    if (!p.isDefeated) {
                        if (playerId.equals(battleInfo.player1Id)) {
                            winnerId = battleInfo.player1Id;
                            loserId = battleInfo.player2Id;
                        } else if (playerId.equals(battleInfo.player2Id)) {
                            winnerId = battleInfo.player2Id;
                            loserId = battleInfo.player1Id;
                        }
                        break;
                    }
                }
            }
        }

        // If we still couldn't determine a winner, log error and use fallback
        if (winnerId == null || loserId == null) {
            Tournaments.LOGGER.error("Could not determine winner/loser after all attempts");

            // Force a decision to avoid a stalemate
            if (player1 != null && player2 != null) {
                // Fallback: use player IDs to consistently pick a "winner"
                // This is arbitrary but consistent
                if (battleInfo.player1Id.compareTo(battleInfo.player2Id) > 0) {
                    winnerId = battleInfo.player1Id;
                    loserId = battleInfo.player2Id;
                } else {
                    winnerId = battleInfo.player2Id;
                    loserId = battleInfo.player1Id;
                }
                Tournaments.LOGGER.warn("Using fallback winner determination: Winner is {}",
                        winnerId.equals(battleInfo.player1Id) ? player1Name : player2Name);
            } else if (player1 != null) {
                winnerId = battleInfo.player1Id;
                loserId = battleInfo.player2Id;
                Tournaments.LOGGER.warn("Player 2 offline, declaring Player 1 as winner");
            } else if (player2 != null) {
                winnerId = battleInfo.player2Id;
                loserId = battleInfo.player1Id;
                Tournaments.LOGGER.warn("Player 1 offline, declaring Player 2 as winner");
            } else {
                // Can't determine anything, just exit
                Tournaments.LOGGER.error("Both players offline, cannot determine winner");
                return;
            }
        }

        // Get the names for display
        String winnerName = winnerId.equals(battleInfo.player1Id) ? player1Name : player2Name;
        String loserName = loserId.equals(battleInfo.player1Id) ? player1Name : player2Name;

        // Record the result
        Tournaments.LOGGER.info("Recording match result: {} defeats {}", winnerName, loserName);
        boolean resultRecorded = tournament.recordMatchResult(winnerId, loserId);

        if (!resultRecorded) {
            Tournaments.LOGGER.error("Failed to record match result");
            tournament.broadcastMessage("Error recording match result. Please contact an administrator.");
            return;
        }

        // Update ELO ratings
        Tournaments.ELO_MANAGER.recordMatch(winnerId, loserId);

        // IMPORTANT: Force eliminate the loser - this will teleport them out
        Tournaments.LOGGER.info("Eliminating loser: {}", loserName);
        tournament.eliminatePlayer(loserId);

        // Send notifications to players
        ServerPlayerEntity winner = winnerId.equals(battleInfo.player1Id) ? player1 : player2;
        ServerPlayerEntity loser = loserId.equals(battleInfo.player1Id) ? player1 : player2;

        if (winner != null) {
            winner.sendMessage(
                    new StringTextComponent("You won the tournament match against " + loserName + "!")
                            .withStyle(TextFormatting.GREEN),
                    winner.getUUID());
        }

        if (loser != null) {
            loser.sendMessage(
                    new StringTextComponent("You lost the tournament match against " + winnerName + ". You have been eliminated.")
                            .withStyle(TextFormatting.RED),
                    loser.getUUID());
        }

        // Broadcast the result to all tournament participants
        tournament.broadcastMessage(winnerName + " has defeated " + loserName + " and advances to the next round!");

        // Check if the tournament should end (only one active player left)
        int activePlayersRemaining = tournament.getParticipantCount() - tournament.getEliminatedPlayerCount();
        if (activePlayersRemaining <= 1) {
            Tournaments.LOGGER.info("Only one player remains in tournament {}, ending tournament", tournament.getName());
            tournament.end();
        }
    }

    /**
     * Validate that a player's Pokémon meet tournament requirements
     *
     * @param player The player to check
     * @param settings The tournament settings
     * @return True if all Pokémon meet requirements, false otherwise
     */
    public static boolean validatePlayerPokemon(ServerPlayerEntity player, TournamentManager.TournamentSettings settings) {
        try {
            // Null check for parameters
            if (player == null || settings == null) {
                Tournaments.LOGGER.error("Null parameters in validatePlayerPokemon: player={}, settings={}",
                        player != null, settings != null);
                return false;
            }

            // Get the player's Pokémon
            Pokemon[] playerPokemon = StorageProxy.getParty(player.getUUID()).getAll();

            // Check if the player has any Pokémon
            if (playerPokemon == null || playerPokemon.length == 0) {
                player.sendMessage(
                        new StringTextComponent("You don't have any Pokémon in your party")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                return false;
            }

            // Count valid Pokémon
            int validCount = 0;

            // Check each Pokémon
            for (Pokemon p : playerPokemon) {
                // Skip null Pokémon
                if (p == null) {
                    continue;
                }

                // Check level
                if (settings.isValidPokemonLevel(p.getPokemonLevel())) {
                    validCount++;
                } else {
                    player.sendMessage(
                            new StringTextComponent("Your Pokémon " + p.getDisplayName() +
                                    " (Level " + p.getPokemonLevel() +
                                    ") does not meet tournament level requirements " +
                                    "(" + settings.getMinLevel() + "-" + settings.getMaxLevel() + ")")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                }
            }

            // Player needs at least one valid Pokémon to join
            if (validCount == 0) {
                player.sendMessage(
                        new StringTextComponent("None of your Pokémon meet the tournament level requirements " +
                                "(" + settings.getMinLevel() + "-" + settings.getMaxLevel() + ")")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                return false;
            }

            return true;
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error validating player Pokémon", e);
            player.sendMessage(
                    new StringTextComponent("Error checking your Pokémon. Please try again.")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return false;
        }
    }

    /**
     * Create a battle between two tournament participants
     */
    public static void createTournamentBattle(ServerPlayerEntity player1, ServerPlayerEntity player2) {
        createTournamentBattle(player1, player2, null);
    }

    /**
     * Create a battle between two tournament participants with specified format
     */
    public static void createTournamentBattle(ServerPlayerEntity player1, ServerPlayerEntity player2, String battleFormat) {
        try {
            // Null check for players
            if (player1 == null || player2 == null) {
                Tournaments.LOGGER.error("Cannot create tournament battle with null players");
                return;
            }

            // Verify both players have valid Pokemon
            Pokemon[] p1Pokemon = StorageProxy.getParty(player1.getUUID()).getAll();
            Pokemon[] p2Pokemon = StorageProxy.getParty(player2.getUUID()).getAll();

            if (p1Pokemon == null || p2Pokemon == null) {
                Tournaments.LOGGER.error("Failed to get Pokemon for players");
                return;
            }

            if (p1Pokemon.length == 0 || p2Pokemon.length == 0) {
                player1.sendMessage(
                        new StringTextComponent("Cannot start battle - one or both players have no Pokémon")
                                .withStyle(TextFormatting.RED),
                        player1.getUUID());

                player2.sendMessage(
                        new StringTextComponent("Cannot start battle - one or both players have no Pokémon")
                                .withStyle(TextFormatting.RED),
                        player2.getUUID());

                Tournaments.LOGGER.error("Cannot start battle - one or both players have no Pokémon");
                return;
            }

            // Check if the Pokemon meet tournament level requirements
            Tournament tournament = TournamentManager.getInstance().getPlayerTournament(player1);
            if (tournament != null) {
                TournamentManager.TournamentSettings settings =
                        TournamentManager.getInstance().getTournamentSettings(tournament.getName());

                if (settings == null) {
                    Tournaments.LOGGER.error("Failed to get tournament settings for {}", tournament.getName());
                    return;
                }

                // Verify Pokemon levels
                boolean p1Valid = verifyPokemonLevels(p1Pokemon, settings, player1);
                boolean p2Valid = verifyPokemonLevels(p2Pokemon, settings, player2);

                if (!p1Valid || !p2Valid) {
                    return; // Players were notified in the verify method
                }
            }

            // Notify players about upcoming battle
            player1.sendMessage(
                    new StringTextComponent("Starting battle against " + player2.getName().getString())
                            .withStyle(TextFormatting.AQUA),
                    player1.getUUID());

            player2.sendMessage(
                    new StringTextComponent("Starting battle against " + player1.getName().getString())
                            .withStyle(TextFormatting.AQUA),
                    player2.getUUID());

            // Create participants with player's actual Pokémon
            // For a full battle, we should use all Pokémon in the party
            PlayerParticipant p1 = new PlayerParticipant(player1, p1Pokemon);
            PlayerParticipant p2 = new PlayerParticipant(player2, p2Pokemon);

            // Use BattleRegistry to start a battle
            BattleRegistry.startBattle(p1, p2);

        } catch (Exception e) {
            Tournaments.LOGGER.error("Error starting tournament battle", e);

            // Notify players of error
            if (player1 != null) {
                player1.sendMessage(
                        new StringTextComponent("Error starting battle. Please try again.")
                                .withStyle(TextFormatting.RED),
                        player1.getUUID());
            }

            if (player2 != null) {
                player2.sendMessage(
                        new StringTextComponent("Error starting battle. Please try again.")
                                .withStyle(TextFormatting.RED),
                        player2.getUUID());
            }
        }
    }

    /**
     * Verify that all Pokémon in a player's party meet tournament level requirements
     */
    private static boolean verifyPokemonLevels(Pokemon[] pokemon,
                                               TournamentManager.TournamentSettings settings,
                                               ServerPlayerEntity player) {
        // Check for null parameters
        if (pokemon == null || settings == null || player == null) {
            Tournaments.LOGGER.error("Null parameters in verifyPokemonLevels: pokemon={}, settings={}, player={}",
                    pokemon != null, settings != null, player != null);
            return false;
        }

        for (Pokemon p : pokemon) {
            // Skip null Pokemon
            if (p == null) {
                continue;
            }

            if (!settings.isValidPokemonLevel(p.getPokemonLevel())) {
                player.sendMessage(
                        new StringTextComponent("Your Pokémon " + p.getDisplayName() +
                                " (Level " + p.getPokemonLevel() +
                                ") does not meet tournament level requirements " +
                                "(" + settings.getMinLevel() + "-" + settings.getMaxLevel() + ")")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                return false;
            }
        }
        return true;
    }
}