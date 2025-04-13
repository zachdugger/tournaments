package com.blissy.tournaments.data;

import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.UUID;

/**
 * Represents a match between two tournament participants
 */
public class TournamentMatch {
    private final UUID player1Id;
    private final UUID player2Id;
    private final String player1Name;
    private final String player2Name;
    private UUID winnerId;
    private MatchStatus status;

    public enum MatchStatus {
        SCHEDULED,   // Match has been scheduled but not started
        IN_PROGRESS, // Match is currently being played
        COMPLETED,   // Match has been completed
        CANCELLED    // Match was cancelled (e.g., player dropout)
    }

    public TournamentMatch(TournamentParticipant player1, TournamentParticipant player2) {
        this.player1Id = player1.getPlayerId();
        this.player2Id = player2.getPlayerId();
        this.player1Name = player1.getPlayerName();
        this.player2Name = player2.getPlayerName();
        this.status = MatchStatus.SCHEDULED;
    }

    /**
     * Mark match as in progress
     */
    public void start() {
        this.status = MatchStatus.IN_PROGRESS;
    }

    /**
     * Complete the match with a winner
     * @param winnerId UUID of the winning player
     * @return true if the match was successfully completed
     */
    public boolean complete(UUID winnerId) {
        if (winnerId.equals(player1Id) || winnerId.equals(player2Id)) {
            this.winnerId = winnerId;
            this.status = MatchStatus.COMPLETED;
            return true;
        }
        return false;
    }

    /**
     * Cancel the match
     */
    public void cancel() {
        this.status = MatchStatus.CANCELLED;
    }

    /**
     * Check if a player is in this match
     */
    public boolean hasPlayer(UUID playerId) {
        return player1Id.equals(playerId) || player2Id.equals(playerId);
    }

    /**
     * Get the opponent of a player
     * @param playerId UUID of the player
     * @return UUID of the opponent or null if player not in match
     */
    public UUID getOpponent(UUID playerId) {
        if (player1Id.equals(playerId)) return player2Id;
        if (player2Id.equals(playerId)) return player1Id;
        return null;
    }

    // Getters
    public UUID getPlayer1Id() { return player1Id; }
    public UUID getPlayer2Id() { return player2Id; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Name() { return player2Name; }
    public UUID getWinnerId() { return winnerId; }
    public MatchStatus getStatus() { return status; }

    /**
     * Get match description for display
     */
    public String getDescription() {
        return player1Name + " vs " + player2Name;
    }
}