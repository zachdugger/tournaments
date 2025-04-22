package com.blissy.tournaments.battle;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages ready check state for tournament battles
 */
public class ReadyCheckManager {
    // Map to track which players are ready for their matches
    private static final Map<UUID, Boolean> readyPlayers = new HashMap<>();

    /**
     * Mark a player as ready for their current match
     * @param player The player marking as ready
     * @param tournament The tournament they are in
     * @return True if both players are now ready, false otherwise
     */
    public static boolean markPlayerReady(ServerPlayerEntity player, Tournament tournament) {
        if (player == null || tournament == null) {
            return false;
        }

        UUID playerId = player.getUUID();

        // Find the player's current match
        TournamentMatch match = findPlayerCurrentMatch(playerId, tournament);
        if (match == null) {
            player.sendMessage(
                    new StringTextComponent("You don't have an active match scheduled")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return false;
        }

        // Mark this player as ready
        readyPlayers.put(playerId, true);

        // Get opponent
        UUID opponentId = match.getOpponent(playerId);
        ServerPlayerEntity opponent = null;

        // Get the opponent player entity
        if (tournament.getParticipants() != null) {
            for (var participant : tournament.getParticipants()) {
                if (participant.getPlayerId().equals(opponentId)) {
                    opponent = participant.getPlayer();
                    break;
                }
            }
        }

        // Notify players
        player.sendMessage(
                new StringTextComponent("You are now ready for your match!")
                        .withStyle(TextFormatting.GREEN),
                player.getUUID());

        // Check if opponent is also ready
        if (readyPlayers.getOrDefault(opponentId, false)) {
            // Both players are ready, start the battle
            if (opponent != null) {
                player.sendMessage(
                        new StringTextComponent("Both players are ready! Starting battle...")
                                .withStyle(TextFormatting.GOLD),
                        player.getUUID());

                opponent.sendMessage(
                        new StringTextComponent("Both players are ready! Starting battle...")
                                .withStyle(TextFormatting.GOLD),
                        opponent.getUUID());

                // Mark match as in progress
                match.start();

                // Initiate the battle
                // Store the format in a final variable for the lambda
                final String battleFormat = null; // Set to a specific format if needed

                // Initiate the battle
                com.blissy.tournaments.compat.PixelmonHandler.createTournamentBattle(player, opponent);

                // Clear ready status for this match
                clearReadyStatus(match);

                return true;
            } else {
                player.sendMessage(
                        new StringTextComponent("Your opponent is offline. Please wait for them to reconnect.")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                return false;
            }
        } else {
            // Opponent not ready yet
            player.sendMessage(
                    new StringTextComponent("Waiting for your opponent to be ready...")
                            .withStyle(TextFormatting.YELLOW),
                    player.getUUID());

            if (opponent != null) {
                opponent.sendMessage(
                        new StringTextComponent(player.getName().getString() + " is ready for your match! Type /tournament ready when you're ready to battle.")
                                .withStyle(TextFormatting.YELLOW),
                        opponent.getUUID());
            }

            return false;
        }
    }

    /**
     * Find the current match for a player
     */
    private static TournamentMatch findPlayerCurrentMatch(UUID playerId, Tournament tournament) {
        if (tournament.getStatus() != Tournament.TournamentStatus.IN_PROGRESS) {
            return null;
        }

        for (TournamentMatch match : tournament.getCurrentMatches()) {
            if (match.hasPlayer(playerId) && match.getStatus() == TournamentMatch.MatchStatus.SCHEDULED) {
                return match;
            }
        }

        return null;
    }

    /**
     * Clear ready status for a match when it starts or ends
     */
    public static void clearReadyStatus(TournamentMatch match) {
        if (match == null) return;

        readyPlayers.remove(match.getPlayer1Id());
        readyPlayers.remove(match.getPlayer2Id());
    }

    /**
     * Check if a player can use the ready command
     */
    public static boolean canPlayerUseReadyCommand(ServerPlayerEntity player, Tournament tournament) {
        if (player == null || tournament == null) {
            return false;
        }

        return findPlayerCurrentMatch(player.getUUID(), tournament) != null;
    }
}