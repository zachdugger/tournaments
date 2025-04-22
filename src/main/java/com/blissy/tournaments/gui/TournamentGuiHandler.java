package com.blissy.tournaments.gui;

import com.blissy.tournaments.Tournaments;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Handler for managing tournament GUI containers
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class TournamentGuiHandler {

    /**
     * Handle container open events
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) {
            return;
        }

        Container container = event.getContainer();
        if (container instanceof ContainerFactory.ClickInterceptingContainer) {
            Tournaments.LOGGER.debug("Tournament container opened with ID: {}", container.containerId);
        }
    }

    /**
     * Handle container close events
     */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) {
            return;
        }

        Container container = event.getContainer();
        if (container instanceof ContainerFactory.ClickInterceptingContainer) {
            Tournaments.LOGGER.debug("Tournament container closed with ID: {}", container.containerId);
        }
    }

    /**
     * Set item lore (description) for an ItemStack
     * @param stack ItemStack to modify
     * @param lore List of text components for lore
     */
    public static void setItemLore(ItemStack stack, List<ITextComponent> lore) {
        CompoundNBT displayTag = stack.getOrCreateTagElement("display");
        ListNBT loreList = new ListNBT();

        for (ITextComponent component : lore) {
            String json = ITextComponent.Serializer.toJson(component);
            loreList.add(StringNBT.valueOf(json));
        }

        displayTag.put("Lore", loreList);
    }

    /**
     * Process an item for GUI actions
     */
    public static void processItemClick(ServerPlayerEntity player, ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("GuiAction")) {
            String action = stack.getTag().getString("GuiAction");
            String tournamentName = stack.getTag().contains("TournamentName") ?
                    stack.getTag().getString("TournamentName") : null;

            Tournaments.LOGGER.info("GUI CLICK DEBUG: Processing action '{}' for tournament '{}'",
                    action, tournamentName != null ? tournamentName : "none");

            // First, handle specific creation GUI actions
            if (action.equals("setName")) {
                // Have player type name in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the tournament name in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.YELLOW),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForTournamentName", true);
                return;
            } else if (action.equals("setMinLevel")) {
                // Have player type min level in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the minimum Pokémon level (1-100) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.AQUA),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForMinLevel", true);
                return;
            } else if (action.equals("setMaxLevel")) {
                // Have player type max level in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the maximum Pokémon level (1-100) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.BLUE),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForMaxLevel", true);
                return;
            } else if (action.equals("setMaxParticipants")) {
                // Have player type max participants in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the tournament size (4-64) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.GREEN),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForMaxParticipants", true);
                return;
            } else if (action.equals("setFormat")) {
                // Have player type format in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the tournament format (SINGLE_ELIMINATION, DOUBLE_ELIMINATION, ROUND_ROBIN) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.LIGHT_PURPLE),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForFormat", true);
                return;
            } else if (action.equals("setEntryFee")) {
                // Have player type entry fee in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the entry fee amount in chat (type '0' for no fee, or 'cancel' to cancel):")
                                .withStyle(TextFormatting.GOLD),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForEntryFee", true);
                return;
            } else if (action.equals("setStartDelay")) {
                // Have player type start delay in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please enter the time (in hours) before the tournament begins (e.g., 0.5 for 30 minutes, or 0 for manual start):")
                                .withStyle(TextFormatting.LIGHT_PURPLE),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForStartDelay", true);
                return;
            } else if (action.equals("createTournament")) {
                // Create tournament with specified or default values
                String name = TournamentCreationGUI.getCreationSetting(player, "name");
                String minLevelStr = TournamentCreationGUI.getCreationSetting(player, "minLevel");
                String maxLevelStr = TournamentCreationGUI.getCreationSetting(player, "maxLevel");
                String maxParticipantsStr = TournamentCreationGUI.getCreationSetting(player, "maxParticipants");
                String format = TournamentCreationGUI.getCreationSetting(player, "format");
                String entryFeeStr = TournamentCreationGUI.getCreationSetting(player, "entryFee");
                String startDelayStr = TournamentCreationGUI.getCreationSetting(player, "startDelay");

                // Parse values (use null for defaults)
                Integer minLevel = minLevelStr != null ? Integer.parseInt(minLevelStr) : null;
                Integer maxLevel = maxLevelStr != null ? Integer.parseInt(maxLevelStr) : null;
                Integer maxParticipants = maxParticipantsStr != null ? Integer.parseInt(maxParticipantsStr) : null;
                Double entryFee = entryFeeStr != null ? Double.parseDouble(entryFeeStr) : null;
                Double startDelay = startDelayStr != null ? Double.parseDouble(startDelayStr) : null;

                // Create the tournament
                player.closeContainer();
                TournamentCreationGUI.createTournament(player, name, minLevel, maxLevel, maxParticipants, format, entryFee, startDelay);
                return;
            } else if (action.equals("create")) {
                // Explicitly handle create action
                Tournaments.LOGGER.info("GUI CLICK DEBUG: Detected 'create' action, opening creation GUI");

                // Close current container
                player.closeContainer();

                // Check permissions
                if (player.hasPermissions(2)) {
                    // Open creation GUI directly from here
                    TournamentCreationGUI.openCreationGUI(player);
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to create tournaments")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());

                    // Reopen main GUI
                    TournamentMainGUI.openMainGui(player);
                }
                return;
            }

            // Process other actions through the main GUI handler
            Tournaments.LOGGER.info("GUI CLICK DEBUG: Delegating to TournamentMainGUI.processGuiAction");
            TournamentMainGUI.processGuiAction(player, action, tournamentName);
        }
    }
}