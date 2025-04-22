package com.blissy.tournaments.data;

import com.blissy.tournaments.Tournaments;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.time.Instant;
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
    private final Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

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
        this.createdAt = Instant.now();
    }

    /**
     * Mark match as in progress
     */
    public void start() {
        this.status = MatchStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
        Tournaments.LOGGER.info("Match started: {} vs {}", player1Name, player2Name);
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
            this.completedAt = Instant.now();

            String winnerName = winnerId.equals(player1Id) ? player1Name : player2Name;
            String loserName = winnerId.equals(player1Id) ? player2Name : player1Name;

            Tournaments.LOGGER.info("Match completed: {} defeated {}", winnerName, loserName);

            return true;
        }
        return false;
    }

    /**
     * Cancel the match
     */
    public void cancel() {
        this.status = MatchStatus.CANCELLED;
        Tournaments.LOGGER.info("Match cancelled: {} vs {}", player1Name, player2Name);
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

    /**
     * Get the duration of the match in milliseconds
     * Returns -1 if match hasn't completed yet
     */
    public long getDurationMs() {
        if (status != MatchStatus.COMPLETED || startedAt == null || completedAt == null) {
            return -1;
        }
        return completedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    // Getters
    public UUID getPlayer1Id() { return player1Id; }
    public UUID getPlayer2Id() { return player2Id; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Name() { return player2Name; }
    public UUID getWinnerId() { return winnerId; }
    public MatchStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    /**
     * Get match description for display
     */
    public String getDescription() {
        return player1Name + " vs " + player2Name;
    }
}