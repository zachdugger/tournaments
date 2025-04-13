package com.blissy.tournaments.handlers;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.config.TournamentsConfig;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.data.TournamentParticipant;
import com.blissy.tournaments.util.TeleportUtil;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Checks if tournament participants stay within the allowed distance of the tournament area
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class PlayerDistanceChecker {

    private static final int CHECK_INTERVAL = 200; // Check every 10 seconds (20 ticks per second)
    private static final double MAX_DISTANCE = 1000.0; // Max distance in blocks
    private static int tickCounter = 0;

    // Map to store entry point locations for each tournament
    private static final Map<String, Vector3d> tournamentEntryPoints = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        // Check all active tournaments
        TournamentManager manager = TournamentManager.getInstance();
        for (Tournament tournament : manager.getAllTournaments().values()) {
            if (tournament.getStatus() != Tournament.TournamentStatus.IN_PROGRESS) continue;

            // Get entry point for this tournament
            Vector3d entryPoint = getEntryPoint();
            if (entryPoint == null) continue;

            // Check all participants
            for (TournamentParticipant participant : tournament.getParticipants()) {
                UUID playerId = participant.getPlayerId();
                if (tournament.isPlayerEliminated(playerId)) continue;

                ServerPlayerEntity player = participant.getPlayer();
                if (player == null || !player.isAlive()) continue;

                // Check if player is in the same dimension
                String playerDimension = player.level.dimension().location().toString();
                String tournamentDimension = TournamentsConfig.COMMON.entryDimension.get();

                if (!playerDimension.equals(tournamentDimension)) {
                    disqualifyPlayer(player, tournament, "leaving the tournament dimension");
                    continue;
                }

                // Check distance
                double distanceSquared = calculateDistanceSquared(
                        player.getX(), player.getY(), player.getZ(),
                        entryPoint.x, entryPoint.y, entryPoint.z);

                if (distanceSquared > MAX_DISTANCE * MAX_DISTANCE) {
                    disqualifyPlayer(player, tournament, "moving too far from the tournament area");
                }
            }
        }
    }

    private static void disqualifyPlayer(ServerPlayerEntity player, Tournament tournament, String reason) {
        if (player == null || tournament == null) return;

        UUID playerId = player.getUUID();

        // Find their current match
        TournamentMatch currentMatch = null;
        for (TournamentMatch match : tournament.getCurrentMatches()) {
            if (match.hasPlayer(playerId) &&
                    (match.getStatus() == TournamentMatch.MatchStatus.SCHEDULED ||
                            match.getStatus() == TournamentMatch.MatchStatus.IN_PROGRESS)) {
                currentMatch = match;
                break;
            }
        }

        // If they're in a match, award the win to the opponent
        if (currentMatch != null) {
            UUID opponentId = currentMatch.getOpponent(playerId);
            if (opponentId != null) {
                tournament.recordMatchResult(opponentId, playerId);

                // Notify players
                player.sendMessage(
                        new StringTextComponent("You have been disqualified for " + reason)
                                .withStyle(TextFormatting.RED),
                        player.getUUID());

                tournament.broadcastMessage(player.getName().getString() +
                        " has been disqualified for " + reason + ".");
            }
        } else {
            // If not in a match, just eliminate them
            tournament.eliminatePlayer(playerId);

            player.sendMessage(
                    new StringTextComponent("You have been disqualified for " + reason)
                            .withStyle(TextFormatting.RED),
                    player.getUUID());

            tournament.broadcastMessage(player.getName().getString() +
                    " has been disqualified for " + reason + ".");
        }
    }

    private static Vector3d getEntryPoint() {
        // Get from config
        double x = TournamentsConfig.COMMON.entryX.get();
        double y = TournamentsConfig.COMMON.entryY.get();
        double z = TournamentsConfig.COMMON.entryZ.get();

        return new Vector3d(x, y, z);
    }

    private static double calculateDistanceSquared(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}