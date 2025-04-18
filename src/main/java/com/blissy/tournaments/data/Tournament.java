package com.blissy.tournaments.data;

import com.blissy.tournaments.battle.ScheduledBattleManager;
import com.blissy.tournaments.config.TournamentsConfig;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.util.TeleportUtil;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class Tournament {
    private final String name;
    private final int maxParticipants;
    private final UUID hostId;
    private final Map<UUID, TournamentParticipant> participants;
    private final Set<UUID> eliminatedPlayers;
    private TournamentStatus status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;
    private Instant scheduledStartTime;

    // Tournament bracket management
    private List<List<TournamentParticipant>> brackets;
    private List<TournamentMatch> matches;
    private int currentRound;

    public Tournament(String name, int maxParticipants, ServerPlayerEntity host) {
        this.name = name;
        this.maxParticipants = Math.min(maxParticipants,
                TournamentsConfig.COMMON.maxParticipants.get());
        this.hostId = host.getUUID();
        this.participants = new HashMap<>();
        this.eliminatedPlayers = new HashSet<>(); // Initialize
        this.status = TournamentStatus.WAITING;
        this.createdAt = Instant.now();
        this.scheduledStartTime = null;
        this.currentRound = 0;
        this.brackets = new ArrayList<>();
        this.matches = new ArrayList<>();

        // Add host as first participant
        addParticipant(host);
    }

    /**
     * Add a new participant to the tournament
     */
    public boolean addParticipant(ServerPlayerEntity player) {
        // Check tournament status
        if (status != TournamentStatus.WAITING) {
            player.sendMessage(
                    new StringTextComponent("This tournament has already started.")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return false;
        }

        // Check participant limit
        if (participants.size() >= maxParticipants) {
            player.sendMessage(
                    new StringTextComponent("This tournament is full.")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return false;
        }

        UUID playerId = player.getUUID();
        TournamentParticipant participant = new TournamentParticipant(player);
        participants.put(playerId, participant);

        // Broadcast join message
        broadcastMessage(player.getName().getString() + " has joined the tournament!");

        return true;
    }

    /**
     * Remove a participant from the tournament
     */
    public boolean removeParticipant(ServerPlayerEntity player) {
        UUID playerId = player.getUUID();

        if (!participants.containsKey(playerId)) {
            return false;
        }

        // Check if tournament has started and teleport leaving player to exit
        if (status == TournamentStatus.IN_PROGRESS &&
                TournamentsConfig.COMMON.enableTeleports.get() &&
                !eliminatedPlayers.contains(playerId)) {
            // Player is leaving an active tournament they weren't eliminated from
            boolean success = TeleportUtil.teleportToExitPoint(player);
            if (!success) {
                Tournaments.LOGGER.warn("Failed to teleport leaving player {} to exit point",
                        player.getName().getString());
            }

            // Mark as eliminated
            eliminatedPlayers.add(playerId);
        }

        participants.remove(playerId);

        // Broadcast leave message
        broadcastMessage(player.getName().getString() + " has left the tournament.");

        // If host leaves, assign new host
        if (playerId.equals(hostId) && !participants.isEmpty()) {
            UUID newHostId = participants.keySet().iterator().next();
            // Notify the new host
            ServerPlayerEntity newHost = participants.get(newHostId).getPlayer();
            if (newHost != null) {
                newHost.sendMessage(
                        new StringTextComponent("You are now the host of this tournament.")
                                .withStyle(TextFormatting.GOLD),
                        newHost.getUUID());
            }
        }

        // Check if only one participant remains and tournament is in progress
        if (status == TournamentStatus.IN_PROGRESS && participants.size() == 1) {
            // Get the last participant
            TournamentParticipant lastParticipant = participants.values().iterator().next();

            // Announce the last player as winner
            broadcastMessage("Only one participant remains!");
            broadcastMessage("★ Tournament Winner: " + lastParticipant.getPlayerName() + "! ★");

            // End the tournament with this participant as winner
            end();
        }

        return true;
    }

    /**
     * Start the tournament
     */
    public void start() {
        // Check if we can start
        if (status != TournamentStatus.WAITING) {
            return;
        }

        if (participants.size() < 2) {
            broadcastMessage("Cannot start tournament with fewer than 2 participants.");
            return;
        }

        // Change status
        status = TournamentStatus.IN_PROGRESS;
        startedAt = Instant.now();

        // Generate initial tournament bracket
        generateBracket();

        broadcastMessage("Tournament has started!");

        // Teleport all participants to the entry point if teleports are enabled
        if (TournamentsConfig.COMMON.enableTeleports.get()) {
            broadcastMessage("Teleporting all participants to the tournament arena...");

            for (TournamentParticipant participant : participants.values()) {
                ServerPlayerEntity player = participant.getPlayer();
                if (player != null && player.isAlive()) {
                    boolean success = TeleportUtil.teleportToEntryPoint(player);
                    if (!success) {
                        Tournaments.LOGGER.warn("Failed to teleport player {} to tournament entry point",
                                player.getName().getString());
                        player.sendMessage(
                                new StringTextComponent("Failed to teleport to tournament arena. Please contact an admin.")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    }
                }
            }
        }

        // Schedule first round matches
        scheduleCurrentRoundMatches();
    }

    /**
     * Generate tournament bracket based on participants
     */
    private void generateBracket() {
        // Shuffle participants to randomize initial matchups
        List<TournamentParticipant> shuffledParticipants = new ArrayList<>(participants.values());
        Collections.shuffle(shuffledParticipants);

        // Create initial round
        List<TournamentParticipant> currentRoundBracket = new ArrayList<>(shuffledParticipants);
        brackets.add(currentRoundBracket);
        currentRound = 0;

        // Display tournament bracket
        broadcastMessage("Tournament bracket generated!");
        for (int i = 0; i < currentRoundBracket.size(); i += 2) {
            if (i + 1 < currentRoundBracket.size()) {
                String player1 = currentRoundBracket.get(i).getPlayerName();
                String player2 = currentRoundBracket.get(i + 1).getPlayerName();
                broadcastMessage("Match " + ((i/2) + 1) + ": " + player1 + " vs " + player2);
            } else {
                // Odd number, player gets a bye
                String player = currentRoundBracket.get(i).getPlayerName();
                broadcastMessage("Match " + ((i/2) + 1) + ": " + player + " receives a bye");
            }
        }
    }

    /**
     * Schedule matches for the current round
     */
    private void scheduleCurrentRoundMatches() {
        List<TournamentParticipant> currentRoundBracket = brackets.get(currentRound);

        // Clear previous round matches
        matches.clear();

        // Create matches for current round
        for (int i = 0; i < currentRoundBracket.size(); i += 2) {
            if (i + 1 < currentRoundBracket.size()) {
                TournamentParticipant player1 = currentRoundBracket.get(i);
                TournamentParticipant player2 = currentRoundBracket.get(i + 1);

                // Create match
                TournamentMatch match = new TournamentMatch(player1, player2);
                matches.add(match);

                // Get player entities
                ServerPlayerEntity p1 = player1.getPlayer();
                ServerPlayerEntity p2 = player2.getPlayer();

                if (p1 != null && p2 != null) {
                    // Notify players about their match
                    p1.sendMessage(
                            new StringTextComponent("You have been matched against " + p2.getName().getString() +
                                    ". Type /tournament ready when you are ready to battle!")
                                    .withStyle(TextFormatting.GOLD),
                            p1.getUUID());

                    p2.sendMessage(
                            new StringTextComponent("You have been matched against " + p1.getName().getString() +
                                    ". Type /tournament ready when you are ready to battle!")
                                    .withStyle(TextFormatting.GOLD),
                            p2.getUUID());

                    broadcastMessage("Match scheduled: " + match.getDescription() +
                            " - Players must type /tournament ready to begin");
                } else {
                    broadcastMessage("Could not schedule match: " + match.getDescription() +
                            " - one or both players offline");
                }
            } else {
                // Odd number of players, this player gets a bye to next round
                TournamentParticipant player = currentRoundBracket.get(i);
                broadcastMessage(player.getPlayerName() + " advances to next round with a bye");
            }
        }
    }

    /**
     * Mark a player as eliminated
     */
    public void eliminatePlayer(UUID playerId) {
        if (!participants.containsKey(playerId)) {
            Tournaments.LOGGER.warn("Attempted to eliminate player {} who is not in tournament {}",
                    playerId, name);
            return;
        }

        // If already eliminated, don't do it again
        if (eliminatedPlayers.contains(playerId)) {
            Tournaments.LOGGER.debug("Player {} is already eliminated from tournament {}",
                    playerId, name);
            return;
        }

        // Add to eliminated players list
        eliminatedPlayers.add(playerId);
        Tournaments.LOGGER.info("Player {} eliminated from tournament {}", playerId, name);

        // Get the player
        TournamentParticipant participant = participants.get(playerId);
        ServerPlayerEntity player = participant.getPlayer();
        String playerName = participant.getPlayerName();

        // Teleport to exit point if available
        if (player != null && player.isAlive() && TournamentsConfig.COMMON.enableTeleports.get()) {
            Tournaments.LOGGER.info("Teleporting eliminated player {} to exit point", playerName);

            boolean success = TeleportUtil.teleportToExitPoint(player);
            if (!success) {
                Tournaments.LOGGER.warn("Failed to teleport eliminated player {} to exit point", playerName);

                // Try again after a short delay
                player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(20, () -> {
                    Tournaments.LOGGER.info("Attempting delayed teleport for eliminated player {}", playerName);
                    boolean delayedSuccess = TeleportUtil.teleportToExitPoint(player);

                    if (!delayedSuccess) {
                        Tournaments.LOGGER.error("Delayed teleport for eliminated player {} also failed", playerName);
                        player.sendMessage(
                                new net.minecraft.util.text.StringTextComponent(
                                        "Failed to teleport you to the exit point. Please contact an administrator.")
                                        .withStyle(net.minecraft.util.text.TextFormatting.RED),
                                player.getUUID());
                    }
                }));
            } else {
                player.sendMessage(
                        new net.minecraft.util.text.StringTextComponent("You have been eliminated from the tournament!")
                                .withStyle(net.minecraft.util.text.TextFormatting.RED),
                        player.getUUID());
            }
        } else if (player != null) {
            // Notify even if we can't teleport
            player.sendMessage(
                    new net.minecraft.util.text.StringTextComponent("You have been eliminated from the tournament!")
                            .withStyle(net.minecraft.util.text.TextFormatting.RED),
                    player.getUUID());
        }

        // Broadcast elimination message
        broadcastMessage(playerName + " has been eliminated from the tournament.");

        // Check if only one active player remains
        int activePlayersRemaining = participants.size() - eliminatedPlayers.size();
        if (activePlayersRemaining <= 1) {
            // Find the last active player
            TournamentParticipant lastActive = null;
            for (TournamentParticipant p : participants.values()) {
                if (!eliminatedPlayers.contains(p.getPlayerId())) {
                    lastActive = p;
                    break;
                }
            }

            // If we found the last active player, they win
            if (lastActive != null) {
                broadcastMessage("Only one participant remains active!");
                broadcastMessage("★ Tournament Winner: " + lastActive.getPlayerName() + "! ★");
                end();
            }
        }
    }

    /**
     * Check if a player is eliminated
     */
    public boolean isPlayerEliminated(UUID playerId) {
        return eliminatedPlayers.contains(playerId);
    }

    /**
     * Get the number of eliminated players
     */
    public int getEliminatedPlayerCount() {
        return eliminatedPlayers.size();
    }

    /**
     * Record a match result
     * @param winnerUUID UUID of the winning player
     * @param loserUUID UUID of the losing player
     * @return true if result was recorded successfully
     */
    public boolean recordMatchResult(UUID winnerUUID, UUID loserUUID) {
        // Find the match
        for (TournamentMatch match : matches) {
            if (match.hasPlayer(winnerUUID) && match.hasPlayer(loserUUID)) {
                // Mark as completed with winner
                if (match.complete(winnerUUID)) {
                    // Update participant stats
                    if (participants.containsKey(winnerUUID)) {
                        participants.get(winnerUUID).incrementWins();
                    }
                    if (participants.containsKey(loserUUID)) {
                        participants.get(loserUUID).incrementLosses();

                        // Mark the loser as eliminated
                        eliminatePlayer(loserUUID);
                    }

                    // Broadcast result
                    String winnerName = participants.containsKey(winnerUUID) ?
                            participants.get(winnerUUID).getPlayerName() : "Unknown";
                    String loserName = participants.containsKey(loserUUID) ?
                            participants.get(loserUUID).getPlayerName() : "Unknown";

                    broadcastMessage(winnerName + " has defeated " + loserName + "!");

                    // Check if round is complete
                    checkRoundCompletion();

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if current round is complete and advance if needed
     */
    private void checkRoundCompletion() {
        // Check if all matches are completed
        boolean allCompleted = true;
        for (TournamentMatch match : matches) {
            if (match.getStatus() != TournamentMatch.MatchStatus.COMPLETED &&
                    match.getStatus() != TournamentMatch.MatchStatus.CANCELLED) {
                allCompleted = false;
                break;
            }
        }

        if (allCompleted) {
            advanceRound();
        }
    }

    /**
     * Advance to next round or end tournament
     */
    public void advanceRound() {
        // Determine winners from current round
        List<TournamentParticipant> winners = determineRoundWinners();

        // If only one winner, end tournament
        if (winners.size() <= 1) {
            end();
            return;
        }

        // Add winners to next round
        brackets.add(winners);
        currentRound++;

        broadcastMessage("Advancing to Round " + (currentRound + 1) +
                ". Remaining participants: " + winners.size());

        // Schedule next round matches
        scheduleCurrentRoundMatches();
    }

    /**
     * Determine winners for the current round
     */
    private List<TournamentParticipant> determineRoundWinners() {
        List<TournamentParticipant> currentRoundBracket = brackets.get(currentRound);
        List<TournamentParticipant> winners = new ArrayList<>();

        // Get winners from completed matches
        for (TournamentMatch match : matches) {
            if (match.getStatus() == TournamentMatch.MatchStatus.COMPLETED) {
                UUID winnerId = match.getWinnerId();
                if (winnerId != null && participants.containsKey(winnerId)) {
                    winners.add(participants.get(winnerId));
                }
            }
        }

        // Add byes (participants without matches)
        for (TournamentParticipant participant : currentRoundBracket) {
            boolean hasMatch = false;
            for (TournamentMatch match : matches) {
                if (match.hasPlayer(participant.getPlayerId())) {
                    hasMatch = true;
                    break;
                }
            }

            if (!hasMatch) {
                // Participant had a bye, add to winners
                winners.add(participant);
            }
        }

        return winners;
    }

    /**
     * End the tournament
     */
    public void end() {
        if (status == TournamentStatus.ENDED) {
            return;
        }

        status = TournamentStatus.ENDED;
        endedAt = Instant.now();

        // Determine and announce winner
        TournamentParticipant winner = determineOverallWinner();
        if (winner != null) {
            broadcastMessage("★ Tournament Winner: " + winner.getPlayerName() + "! ★");

            // Give rewards if enabled
            if (TournamentsConfig.COMMON.enableRewards.get()) {
                ServerPlayerEntity playerEntity = winner.getPlayer();
                if (playerEntity != null) {
                    // Implement reward logic here
                    playerEntity.sendMessage(
                            new StringTextComponent("You've received tournament rewards!")
                                    .withStyle(TextFormatting.GOLD),
                            playerEntity.getUUID());
                }
            }
        } else {
            broadcastMessage("Tournament concluded with no clear winner.");
        }
    }

    /**
     * Determine the overall tournament winner
     */
    private TournamentParticipant determineOverallWinner() {
        if (brackets.isEmpty()) {
            return null;
        }

        // First check if only one active player remains
        List<TournamentParticipant> activePlayers = new ArrayList<>();
        for (TournamentParticipant participant : participants.values()) {
            if (!eliminatedPlayers.contains(participant.getPlayerId())) {
                activePlayers.add(participant);
            }
        }

        if (activePlayers.size() == 1) {
            return activePlayers.get(0);
        }

        // Otherwise use last round bracket
        List<TournamentParticipant> finalRound = brackets.get(brackets.size() - 1);
        return finalRound.isEmpty() ? null : finalRound.get(0);
    }

    /**
     * Broadcast a message to all tournament participants
     */
    public void broadcastMessage(String message) {
        for (TournamentParticipant participant : participants.values()) {
            ServerPlayerEntity player = participant.getPlayer();
            if (player != null) {
                player.sendMessage(
                        new StringTextComponent("[Tournament: " + name + "] " + message)
                                .withStyle(TextFormatting.GOLD),
                        player.getUUID());
            }
        }
    }

    // Getters and utility methods
    public String getName() {
        return name;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public UUID getHostId() {
        return hostId;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public Collection<TournamentParticipant> getParticipants() {
        return participants.values();
    }

    public List<TournamentMatch> getCurrentMatches() {
        return new ArrayList<>(matches);
    }

    /**
     * Get the scheduled start time for the tournament
     */
    public Instant getScheduledStartTime() {
        return scheduledStartTime;
    }

    /**
     * Set the scheduled start time for the tournament
     */
    public void setScheduledStartTime(Instant time) {
        this.scheduledStartTime = time;
    }

    public enum TournamentStatus {
        WAITING,      // Tournament created, waiting for participants
        IN_PROGRESS,  // Tournament is currently running
        ENDED         // Tournament has concluded
    }
}