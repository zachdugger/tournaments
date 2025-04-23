package com.blissy.tournaments.handlers;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.data.RecurringTournament;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

/**
 * Handler for checking and creating recurring tournaments
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class RecurringTournamentHandler {

    private static final int CHECK_INTERVAL = 1200; // Check every minute (20 ticks * 60s)
    private static int tickCounter = 0;

    /**
     * Check for recurring tournaments every minute
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        // Don't run if no players are online
        if (ServerLifecycleHooks.getCurrentServer() == null ||
                ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerCount() == 0) {
            return;
        }

        Tournaments.LOGGER.debug("Checking recurring tournaments...");
        RecurringTournament.checkAllRecurringTournaments();
    }

    /**
     * Check if a player has permission to create recurring tournaments
     */
    public static boolean canCreateRecurringTournament(ServerPlayerEntity player) {
        // Permission level 2 or higher can create recurring tournaments
        return player != null && player.hasPermissions(2);
    }

    /**
     * Check if a player has permission to create normal tournaments
     */
    public static boolean canCreatePlayerTournament(ServerPlayerEntity player) {
        // Players with "tournaments.create" permission can create tournaments
        // This is a simpler check than the full permission system
        return player != null && (player.hasPermissions(2) ||
                player.getPersistentData().getBoolean("tournaments.create"));
    }

    /**
     * Set player tournament creation permission
     */
    public static void setPlayerTournamentPermission(ServerPlayerEntity player, boolean canCreate) {
        if (player != null) {
            player.getPersistentData().putBoolean("tournaments.create", canCreate);
        }
    }
}