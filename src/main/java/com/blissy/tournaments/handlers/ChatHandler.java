package com.blissy.tournaments.handlers;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.gui.TournamentCreationGUI;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles chat interactions for the tournament creation process
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class ChatHandler {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity player = event.getPlayer();
        CompoundNBT playerData = player.getPersistentData();
        String message = event.getMessage();

        // Check if we're waiting for player input for tournament creation
        if (playerData.contains("WaitingForTournamentName") && playerData.getBoolean("WaitingForTournamentName")) {
            // Handle tournament name input
            playerData.putBoolean("WaitingForTournamentName", false);

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(
                        new StringTextComponent("Tournament creation cancelled")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                TournamentCreationGUI.openCreationGUI(player);
            } else {
                // Store the name
                TournamentCreationGUI.storeCreationSetting(player, "name", message);
                player.sendMessage(
                        new StringTextComponent("Tournament name set to: " + message)
                                .withStyle(TextFormatting.GREEN),
                        player.getUUID());
                TournamentCreationGUI.openCreationGUI(player);
            }

            // Cancel the chat message
            event.setCanceled(true);
            return;
        } else if (playerData.contains("WaitingForMinLevel") && playerData.getBoolean("WaitingForMinLevel")) {
            // Handle min level input
            playerData.putBoolean("WaitingForMinLevel", false);

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(
                        new StringTextComponent("Min level setting cancelled")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                TournamentCreationGUI.openCreationGUI(player);
            } else {
                try {
                    int level = Integer.parseInt(message);
                    if (level < 1 || level > 100) {
                        player.sendMessage(
                                new StringTextComponent("Invalid level. Must be between 1 and 100")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    } else {
                        TournamentCreationGUI.storeCreationSetting(player, "minLevel", String.valueOf(level));
                        player.sendMessage(
                                new StringTextComponent("Minimum level set to: " + level)
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(
                            new StringTextComponent("Invalid number format")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                }
                TournamentCreationGUI.openCreationGUI(player);
            }

            // Cancel the chat message
            event.setCanceled(true);
            return;
        } else if (playerData.contains("WaitingForMaxLevel") && playerData.getBoolean("WaitingForMaxLevel")) {
            // Handle max level input
            playerData.putBoolean("WaitingForMaxLevel", false);

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(
                        new StringTextComponent("Max level setting cancelled")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                TournamentCreationGUI.openCreationGUI(player);
            } else {
                try {
                    int level = Integer.parseInt(message);
                    if (level < 1 || level > 100) {
                        player.sendMessage(
                                new StringTextComponent("Invalid level. Must be between 1 and 100")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    } else {
                        TournamentCreationGUI.storeCreationSetting(player, "maxLevel", String.valueOf(level));
                        player.sendMessage(
                                new StringTextComponent("Maximum level set to: " + level)
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(
                            new StringTextComponent("Invalid number format")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                }
                TournamentCreationGUI.openCreationGUI(player);
            }

            // Cancel the chat message
            event.setCanceled(true);
            return;
        } else if (playerData.contains("WaitingForMaxParticipants") && playerData.getBoolean("WaitingForMaxParticipants")) {
            // Handle max participants input
            playerData.putBoolean("WaitingForMaxParticipants", false);

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(
                        new StringTextComponent("Tournament size setting cancelled")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                TournamentCreationGUI.openCreationGUI(player);
            } else {
                try {
                    int count = Integer.parseInt(message);
                    if (count < 4 || count > 64) {
                        player.sendMessage(
                                new StringTextComponent("Invalid count. Must be between 4 and 64")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    } else {
                        TournamentCreationGUI.storeCreationSetting(player, "maxParticipants", String.valueOf(count));
                        player.sendMessage(
                                new StringTextComponent("Tournament size set to: " + count + " players")
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(
                            new StringTextComponent("Invalid number format")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                }
                TournamentCreationGUI.openCreationGUI(player);
            }

            // Cancel the chat message
            event.setCanceled(true);
            return;
        } else if (playerData.contains("WaitingForFormat") && playerData.getBoolean("WaitingForFormat")) {
            // Handle format input
            playerData.putBoolean("WaitingForFormat", false);

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(
                        new StringTextComponent("Format setting cancelled")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                TournamentCreationGUI.openCreationGUI(player);
            } else {
                // Check if format is valid
                String format = message.toUpperCase();
                if (format.equals("SINGLE_ELIMINATION") ||
                        format.equals("DOUBLE_ELIMINATION") ||
                        format.equals("ROUND_ROBIN")) {

                    TournamentCreationGUI.storeCreationSetting(player, "format", format);
                    player.sendMessage(
                            new StringTextComponent("Tournament format set to: " + format)
                                    .withStyle(TextFormatting.GREEN),
                            player.getUUID());
                } else {
                    player.sendMessage(
                            new StringTextComponent("Invalid format. Must be SINGLE_ELIMINATION, DOUBLE_ELIMINATION, or ROUND_ROBIN")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                }
                TournamentCreationGUI.openCreationGUI(player);
            }

            // Cancel the chat message
            event.setCanceled(true);
            return;
        } else if (playerData.contains("WaitingForEntryFee") && playerData.getBoolean("WaitingForEntryFee")) {
            // Handle entry fee input
            playerData.putBoolean("WaitingForEntryFee", false);

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(
                        new StringTextComponent("Entry fee setting cancelled")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                TournamentCreationGUI.openCreationGUI(player);
            } else {
                try {
                    double fee = Double.parseDouble(message);
                    if (fee < 0) {
                        player.sendMessage(
                                new StringTextComponent("Invalid fee. Must be 0 or greater")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    } else {
                        TournamentCreationGUI.storeCreationSetting(player, "entryFee", String.valueOf(fee));
                        player.sendMessage(
                                new StringTextComponent("Entry fee set to: " + fee)
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(
                            new StringTextComponent("Invalid number format")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                }
                TournamentCreationGUI.openCreationGUI(player);
            }

            // Cancel the chat message
            event.setCanceled(true);
            return;
        } else if (playerData.contains("WaitingForStartDelay") && playerData.getBoolean("WaitingForStartDelay")) {
            // Handle start delay input
            playerData.putBoolean("WaitingForStartDelay", false);

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(
                        new StringTextComponent("Start delay setting cancelled")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                TournamentCreationGUI.openCreationGUI(player);
            } else {
                try {
                    double delay = Double.parseDouble(message);
                    if (delay < 0) {
                        player.sendMessage(
                                new StringTextComponent("Invalid delay. Must be 0 or greater")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    } else {
                        TournamentCreationGUI.storeCreationSetting(player, "startDelay", String.valueOf(delay));

                        // Format the message based on delay value
                        String timeMsg;
                        if (delay == 0) {
                            timeMsg = "Tournament will start when manually triggered";
                        } else if (delay < 1) {
                            timeMsg = String.format("Tournament will start in %.0f minutes", delay * 60);
                        } else {
                            timeMsg = String.format("Tournament will start in %.1f hours", delay);
                        }

                        player.sendMessage(
                                new StringTextComponent(timeMsg)
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(
                            new StringTextComponent("Invalid number format. Please enter a number like 0.5 or 1")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                }
                TournamentCreationGUI.openCreationGUI(player);
            }

            // Cancel the chat message
            event.setCanceled(true);
            return;
        }
    }
}