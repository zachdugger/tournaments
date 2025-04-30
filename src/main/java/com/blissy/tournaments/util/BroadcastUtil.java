package com.blissy.tournaments.util;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.STitlePacket;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

/**
 * Utility for sending title/subtitle messages to players
 */
public class BroadcastUtil {

    /**
     * Send a title message (large center screen text)
     */
    public static void sendTitle(ServerPlayerEntity player, String message, TextFormatting color, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;

        ITextComponent text = new StringTextComponent(message).withStyle(color);
        player.connection.send(new STitlePacket(STitlePacket.Type.TITLE, text, fadeIn, stay, fadeOut));
    }

    /**
     * Send a subtitle message (smaller text below title)
     */
    public static void sendSubtitle(ServerPlayerEntity player, String message, TextFormatting color, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;

        ITextComponent text = new StringTextComponent(message).withStyle(color);
        player.connection.send(new STitlePacket(STitlePacket.Type.SUBTITLE, text, fadeIn, stay, fadeOut));
    }

    /**
     * Send an actionbar message (text above hotbar)
     */
    public static void sendActionBar(ServerPlayerEntity player, String message, TextFormatting color) {
        if (player == null) return;

        ITextComponent text = new StringTextComponent(message).withStyle(color);
        player.connection.send(new STitlePacket(STitlePacket.Type.ACTIONBAR, text, 10, 70, 20));
    }

    /**
     * Clear all titles from player's screen
     */
    public static void clearTitles(ServerPlayerEntity player) {
        if (player == null) return;

        player.connection.send(new STitlePacket(STitlePacket.Type.CLEAR, null));
    }

    /**
     * Run countdown sequence for player
     */
    public static void runCountdown(ServerPlayerEntity player, String message, int seconds, Runnable onComplete) {
        if (player == null) return;

        // Clear any existing titles
        clearTitles(player);

        // Start with the message
        sendTitle(player, message, TextFormatting.GOLD, 10, 20, 10);

        // Set up the countdown with timed tasks
        for (int i = seconds; i > 0; i--) {
            final int count = i;
            player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(
                    (seconds - i) * 20, // 20 ticks = 1 second
                    () -> {
                        sendTitle(player, String.valueOf(count), TextFormatting.RED, 0, 20, 5);

                        if (count == 1 && onComplete != null) {
                            // Execute completion task after 1 second
                            player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(
                                    20, onComplete
                            ));
                        }
                    }
            ));
        }
    }
}