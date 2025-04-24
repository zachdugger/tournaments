package com.blissy.tournaments.gui;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.config.UIConfigLoader;
import com.blissy.tournaments.data.EloPlayer;
import com.blissy.tournaments.data.RecurringTournament;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.data.TournamentParticipant;
import com.blissy.tournaments.handlers.RecurringTournamentHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class TournamentMainGUI {

    // Store a reference to containers for click detection
    private static final List<Integer> tournamentContainerIds = new ArrayList<>();
    private static boolean processingAction = false;

    /**
     * Open the main tournament GUI for a player
     */
    public static void openMainGui(ServerPlayerEntity player) {
        // Get title from config
        JsonObject config = UIConfigLoader.getMainScreenConfig();
        String title = config.has("title") ? config.get("title").getAsString() : "Tournament Management";

        // Use container factory to create a GUI with click handling
        ContainerFactory.openTournamentGui(player, title, (inventory, p) -> {
            // Build the GUI
            populateMainGui(inventory, p);
        });
    }

    /**
     * Open the tournament matches GUI for a tournament
     */
    public static void openMatchesGui(ServerPlayerEntity player, String tournamentName) {
        // Get the tournament
        Tournament tournament = TournamentManager.getInstance().getTournament(tournamentName);
        if (tournament == null) {
            player.sendMessage(new StringTextComponent("Tournament not found").withStyle(TextFormatting.RED),
                    player.getUUID());
            return;
        }

        // Get title from config
        JsonObject config = UIConfigLoader.getMatchesScreenConfig();
        String title = config.has("title") ?
                config.get("title").getAsString() + ": " + tournamentName :
                "Tournament Matches: " + tournamentName;

        // Use container factory to create a GUI with click handling
        ContainerFactory.openTournamentGui(player, title, (inventory, p) -> {
            // Populate with tournament matches
            populateMatchesGui(inventory, tournament);
        });
    }

    /**
     * Open the recurring tournaments GUI
     */
    public static void openRecurringTournamentsGui(ServerPlayerEntity player) {
        // Get initial config
        JsonObject initialConfig = UIConfigLoader.getMainScreenConfig();
        // Create a final variable for the modified config to use in lambda
        final JsonObject configToUse = initialConfig.has("recurring_matches_screen")
                ? initialConfig.getAsJsonObject("recurring_matches_screen")
                : initialConfig;

        String title = configToUse.has("title") ? configToUse.get("title").getAsString() : "Recurring Tournaments";

        ContainerFactory.openTournamentGui(player, title, (inventory, p) -> {
            populateRecurringTournamentsGui(inventory, configToUse, p);
        });
    }

    /**
     * Open the player tournaments GUI
     */
    public static void openPlayerTournamentsGui(ServerPlayerEntity player) {
        // Get initial config
        JsonObject initialConfig = UIConfigLoader.getMainScreenConfig();
        // Create a final variable for the modified config to use in lambda
        final JsonObject configToUse = initialConfig.has("player_matches_screen")
                ? initialConfig.getAsJsonObject("player_matches_screen")
                : initialConfig;

        String title = configToUse.has("title") ? configToUse.get("title").getAsString() : "Player Tournaments";

        ContainerFactory.openTournamentGui(player, title, (inventory, p) -> {
            populatePlayerTournamentsGui(inventory, configToUse, p);
        });
    }

    private static void populateMainGui(Inventory inventory, ServerPlayerEntity player) {
        JsonObject config = UIConfigLoader.getMainScreenConfig();
        TournamentManager manager = TournamentManager.getInstance();
        Tournament playerTournament = manager.getPlayerTournament(player);

        // First, apply the borders to the inventory
        UIConfigLoader.applyBorders(inventory, config);

        // Add stats book in top left
        ItemStack statsBook = new ItemStack(Items.ENCHANTED_BOOK);
        statsBook.setHoverName(new StringTextComponent("Tournament Stats")
                .withStyle(TextFormatting.GOLD));

        // Set the author and title
        CompoundNBT bookTag = statsBook.getOrCreateTag();
        bookTag.putString("author", player.getName().getString());
        bookTag.putString("title", "Tournament Stats");
        bookTag.putBoolean("resolved", true);

        // Use getOrCreatePlayer to ensure we have an EloPlayer even for players without stats
        EloPlayer playerElo = Tournaments.ELO_MANAGER.getOrCreatePlayer(player.getUUID());

        // Add lore with player stats
        List<ITextComponent> statsLore = new ArrayList<>();

        statsLore.add(new StringTextComponent("ELO Rating: " + playerElo.getElo())
                .withStyle(TextFormatting.GOLD));

        statsLore.add(new StringTextComponent("Wins: " + playerElo.getWins())
                .withStyle(TextFormatting.GREEN));

        statsLore.add(new StringTextComponent("Losses: " + playerElo.getLosses())
                .withStyle(TextFormatting.RED));

        // Calculate win rate
        int totalGames = playerElo.getWins() + playerElo.getLosses();
        if (totalGames > 0) {
            float winRate = (float)playerElo.getWins() * 100f / totalGames;
            statsLore.add(new StringTextComponent("Win Rate: " + String.format("%.1f%%", winRate))
                    .withStyle(TextFormatting.AQUA));
        } else {
            statsLore.add(new StringTextComponent("Win Rate: 0.0%")
                    .withStyle(TextFormatting.AQUA));
        }

        TournamentGuiHandler.setItemLore(statsBook, statsLore);

        // Add to stats book slot (top left)
        int statsBookSlot = UIConfigLoader.getSlot(config, "stats_book_slot");
        if (statsBookSlot >= 0 && statsBookSlot < inventory.getContainerSize()) {
            inventory.setItem(statsBookSlot, statsBook);
        } else {
            // Fallback to slot 0 if not configured
            inventory.setItem(0, statsBook);
        }

        // Add action buttons based on player status
        if (playerTournament == null) {
            // Player is not in a tournament - show join button
            UIConfigLoader.ItemConfig joinConfig = UIConfigLoader.getItemConfig(config, "join_button");
            ItemStack joinButton = new ItemStack(joinConfig.getItem());
            joinButton.setHoverName(new StringTextComponent(joinConfig.getName())
                    .withStyle(joinConfig.getColor()));

            CompoundNBT tag = joinButton.getOrCreateTag();
            tag.putString("GuiAction", joinConfig.getAction());
            inventory.setItem(UIConfigLoader.getSlot(config, "join_button_slot"), joinButton);
        } else {
            // Player is in a tournament - show leave button
            UIConfigLoader.ItemConfig leaveConfig = UIConfigLoader.getItemConfig(config, "leave_button");
            ItemStack leaveButton = new ItemStack(leaveConfig.getItem());
            leaveButton.setHoverName(new StringTextComponent(leaveConfig.getName())
                    .withStyle(leaveConfig.getColor()));

            CompoundNBT tag = leaveButton.getOrCreateTag();
            tag.putString("GuiAction", leaveConfig.getAction());
            inventory.setItem(UIConfigLoader.getSlot(config, "leave_button_slot"), leaveButton);

            // Add start button if player is the host and tournament is waiting
            if (player.getUUID().equals(playerTournament.getHostId()) &&
                    playerTournament.getStatus() == Tournament.TournamentStatus.WAITING) {

                UIConfigLoader.ItemConfig startConfig = UIConfigLoader.getItemConfig(config, "start_button");
                ItemStack startButton = new ItemStack(startConfig.getItem());
                startButton.setHoverName(new StringTextComponent(startConfig.getName())
                        .withStyle(startConfig.getColor()));

                CompoundNBT startTag = startButton.getOrCreateTag();
                startTag.putString("GuiAction", startConfig.getAction());
                inventory.setItem(UIConfigLoader.getSlot(config, "start_button_slot"), startButton);
            }
        }

        // Add create tournament button based on permission level for regular tournaments
        boolean canCreatePlayer = RecurringTournamentHandler.canCreatePlayerTournament(player);

        if (canCreatePlayer) {
            UIConfigLoader.ItemConfig createConfig = UIConfigLoader.getItemConfig(config, "create_button");
            ItemStack createButton = new ItemStack(createConfig.getItem());
            createButton.setHoverName(new StringTextComponent(createConfig.getName())
                    .withStyle(createConfig.getColor()));

            CompoundNBT tag = createButton.getOrCreateTag();
            tag.putString("GuiAction", "create_player"); // Changed to directly open player tournament creation
            inventory.setItem(UIConfigLoader.getSlot(config, "create_button_slot"), createButton);
        }

        // Add recurring button outside the border for OPs only
        boolean canCreateRecurring = RecurringTournamentHandler.canCreateRecurringTournament(player);
        if (canCreateRecurring) {
            ItemStack recurringButton = new ItemStack(Items.DIAMOND_BLOCK);
            recurringButton.setHoverName(new StringTextComponent("Create Recurring Tournament")
                    .withStyle(TextFormatting.LIGHT_PURPLE, TextFormatting.BOLD));

            List<ITextComponent> recurringLore = new ArrayList<>();
            recurringLore.add(new StringTextComponent("Click to create a recurring tournament")
                    .withStyle(TextFormatting.GRAY));
            TournamentGuiHandler.setItemLore(recurringButton, recurringLore);

            CompoundNBT tag = recurringButton.getOrCreateTag();
            tag.putString("GuiAction", "create_recurring");

            // Place outside of borders
            inventory.setItem(6, recurringButton);
        }

        // Add reload config button for admins
        if (player.hasPermissions(2)) {
            ItemStack reloadButton = new ItemStack(Items.COMPARATOR);
            reloadButton.setHoverName(new StringTextComponent("Reload Config")
                    .withStyle(TextFormatting.LIGHT_PURPLE));

            List<ITextComponent> reloadLore = new ArrayList<>();
            reloadLore.add(new StringTextComponent("Click to reload the UI configuration")
                    .withStyle(TextFormatting.GRAY));
            TournamentGuiHandler.setItemLore(reloadButton, reloadLore);

            CompoundNBT reloadTag = reloadButton.getOrCreateTag();
            reloadTag.putString("GuiAction", "reloadconfig");
            inventory.setItem(7, reloadButton);
        }

        // Populate leaderboard slots with player heads (slots 10-16)
        List<EloPlayer> topPlayers = Tournaments.ELO_MANAGER.getTopPlayers(7);

        for (int i = 0; i < topPlayers.size() && i < 7; i++) {
            // Using "topPlayerElo" to avoid naming conflict with "playerElo"
            EloPlayer topPlayerElo = topPlayers.get(i);
            int slot = 10 + i; // Use slots 10-16 directly

            ItemStack playerHead = new ItemStack(Items.PLAYER_HEAD);

            // Set the correct NBT data to display player skin
            CompoundNBT skullOwner = new CompoundNBT();
            skullOwner.putString("Name", topPlayerElo.getPlayerName());

            // UUID is required for proper skin lookup
            UUID playerUUID = topPlayerElo.getPlayerId();
            CompoundNBT uuidTag = new CompoundNBT();
            uuidTag.putIntArray("Id", uuidToIntArray(playerUUID));
            skullOwner.put("Id", uuidTag);

            CompoundNBT tag = playerHead.getOrCreateTag();
            tag.put("SkullOwner", skullOwner);

            playerHead.setHoverName(
                    new StringTextComponent("#" + (i + 1) + " " + topPlayerElo.getPlayerName() + " (" + topPlayerElo.getElo() + " ELO)")
                            .withStyle(TextFormatting.GOLD)
            );

            List<ITextComponent> lore = new ArrayList<>();
            lore.add(new StringTextComponent("Wins: " + topPlayerElo.getWins())
                    .withStyle(TextFormatting.GREEN));
            lore.add(new StringTextComponent("Losses: " + topPlayerElo.getLosses())
                    .withStyle(TextFormatting.RED));

            // Calculate win rate
            int playerTotalGames = topPlayerElo.getWins() + topPlayerElo.getLosses();
            if (playerTotalGames > 0) {
                float winRate = (float) topPlayerElo.getWins() * 100f / playerTotalGames;
                lore.add(new StringTextComponent(String.format("Win Rate: %.1f%%", winRate))
                        .withStyle(TextFormatting.AQUA));
            }

            TournamentGuiHandler.setItemLore(playerHead, lore);
            inventory.setItem(slot, playerHead);
        }

        // Add section labels and "Show All" buttons
        addSectionLabels(inventory, config, player);

        // Populate recurring tournaments - passing config parameter
        populateRecurringTournamentSlots(inventory, config, player);

        // Populate player tournaments - passing config parameter
        populatePlayerTournamentSlots(inventory, config, player);

        // Add in-progress tournament displays
        addInProgressDisplays(inventory, config);
    }

    private static int[] uuidToIntArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new int[]{
                (int) (most >> 32),
                (int) most,
                (int) (least >> 32),
                (int) least
        };
    }
    /**
     * Add in-progress tournament displays in specified slots
     */
    private static void addInProgressDisplays(Inventory inventory, JsonObject config) {
        // Get all active tournaments
        List<Tournament> inProgressTournaments = new ArrayList<>();

        for (Tournament tournament : TournamentManager.getInstance().getAllTournaments().values()) {
            if (tournament.getStatus() == Tournament.TournamentStatus.IN_PROGRESS) {
                inProgressTournaments.add(tournament);
            }
        }

        if (inProgressTournaments.isEmpty()) {
            return;
        }

        // Get recurring in-progress display slots
        List<Integer> recurringSlots = new ArrayList<>();

        // Safely get the slots from the config
        if (config != null && config.has("slots")) {
            JsonObject slotsObj = config.getAsJsonObject("slots");
            if (slotsObj.has("recurring_in_progress_slots")) {
                try {
                    JsonArray slotsArray = slotsObj.getAsJsonArray("recurring_in_progress_slots");
                    for (JsonElement element : slotsArray) {
                        recurringSlots.add(element.getAsInt());
                    }
                } catch (Exception e) {
                    Tournaments.LOGGER.error("Error parsing recurring_in_progress_slots", e);
                    // Use default slots if there's an error
                    recurringSlots.add(18);
                    recurringSlots.add(27);
                    recurringSlots.add(36);
                }
            } else {
                // Default slots
                recurringSlots.add(18);
                recurringSlots.add(27);
                recurringSlots.add(36);
            }
        } else {
            // Default slots
            recurringSlots.add(18);
            recurringSlots.add(27);
            recurringSlots.add(36);
        }

        // Get player in-progress display slots
        List<Integer> playerSlots = new ArrayList<>();

        // Safely get the slots from the config
        if (config != null && config.has("slots")) {
            JsonObject slotsObj = config.getAsJsonObject("slots");
            if (slotsObj.has("player_in_progress_slots")) {
                try {
                    JsonArray slotsArray = slotsObj.getAsJsonArray("player_in_progress_slots");
                    for (JsonElement element : slotsArray) {
                        playerSlots.add(element.getAsInt());
                    }
                } catch (Exception e) {
                    Tournaments.LOGGER.error("Error parsing player_in_progress_slots", e);
                    // Use default slots if there's an error
                    playerSlots.add(26);
                    playerSlots.add(35);
                    playerSlots.add(44);
                }
            } else {
                // Default slots
                playerSlots.add(26);
                playerSlots.add(35);
                playerSlots.add(44);
            }
        } else {
            // Default slots
            playerSlots.add(26);
            playerSlots.add(35);
            playerSlots.add(44);
        }

        // Split tournaments into recurring and player tournaments
        List<Tournament> recurringTournaments = new ArrayList<>();
        List<Tournament> playerTournaments = new ArrayList<>();

        for (Tournament tournament : inProgressTournaments) {
            CompoundNBT extraSettings = TournamentManager.getInstance().getTournamentExtraSettings(tournament.getName());
            boolean isRecurring = extraSettings != null && extraSettings.contains("isRecurring") && extraSettings.getBoolean("isRecurring");

            if (isRecurring) {
                recurringTournaments.add(tournament);
            } else {
                playerTournaments.add(tournament);
            }
        }

        // Add recurring in-progress tournaments to display slots
        for (int i = 0; i < Math.min(recurringTournaments.size(), recurringSlots.size()); i++) {
            Tournament tournament = recurringTournaments.get(i);
            int slot = recurringSlots.get(i);

            ItemStack displayItem = new ItemStack(Items.BOOK);
            displayItem.setHoverName(new StringTextComponent("Active: " + tournament.getName())
                    .withStyle(TextFormatting.GOLD));

            List<ITextComponent> lore = new ArrayList<>();
            lore.add(new StringTextComponent("Players: " + tournament.getParticipantCount() +
                    "/" + tournament.getMaxParticipants())
                    .withStyle(TextFormatting.GRAY));

            TournamentGuiHandler.setItemLore(displayItem, lore);
            inventory.setItem(slot, displayItem);
        }

        // Add player in-progress tournaments to display slots
        for (int i = 0; i < Math.min(playerTournaments.size(), playerSlots.size()); i++) {
            Tournament tournament = playerTournaments.get(i);
            int slot = playerSlots.get(i);

            ItemStack displayItem = new ItemStack(Items.BOOK);
            displayItem.setHoverName(new StringTextComponent("Active: " + tournament.getName())
                    .withStyle(TextFormatting.AQUA));

            List<ITextComponent> lore = new ArrayList<>();
            lore.add(new StringTextComponent("Players: " + tournament.getParticipantCount() +
                    "/" + tournament.getMaxParticipants())
                    .withStyle(TextFormatting.GRAY));

            TournamentGuiHandler.setItemLore(displayItem, lore);
            inventory.setItem(slot, displayItem);
        }
    }

    /**
     * Add section labels and show all buttons
     */
    private static void addSectionLabels(Inventory inventory, JsonObject config, ServerPlayerEntity player) {
        if (config == null) {
            Tournaments.LOGGER.warn("Config is null in addSectionLabels");
            return;
        }

        // Add recurring tournaments label - safely get config
        UIConfigLoader.ItemConfig recurringLabelConfig = UIConfigLoader.getItemConfig(config, "recurring_label");
        if (recurringLabelConfig != null && recurringLabelConfig.getItem() != Items.BARRIER) {
            ItemStack recurringLabel = new ItemStack(recurringLabelConfig.getItem());
            recurringLabel.setHoverName(new StringTextComponent(recurringLabelConfig.getName())
                    .withStyle(recurringLabelConfig.getColor()));

            int slot = UIConfigLoader.getSlot(config, "recurring_label_slot");
            if (slot >= 0 && slot < inventory.getContainerSize()) {
                inventory.setItem(slot, recurringLabel);
            }
        }

        // Add player tournaments label - safely get config
        UIConfigLoader.ItemConfig playerLabelConfig = UIConfigLoader.getItemConfig(config, "player_label");
        if (playerLabelConfig != null && playerLabelConfig.getItem() != Items.BARRIER) {
            ItemStack playerLabel = new ItemStack(playerLabelConfig.getItem());
            playerLabel.setHoverName(new StringTextComponent(playerLabelConfig.getName())
                    .withStyle(playerLabelConfig.getColor()));

            int slot = UIConfigLoader.getSlot(config, "player_label_slot");
            if (slot >= 0 && slot < inventory.getContainerSize()) {
                inventory.setItem(slot, playerLabel);
            }
        }

        // Get recurring tournaments count
        int recurringCount = 0;
        for (RecurringTournament tournament : RecurringTournament.getAllRecurringTournaments()) {
            recurringCount++;
        }

        // Get player tournaments count
        int playerTournamentCount = 0;
        for (Tournament tournament : TournamentManager.getInstance().getAllTournaments().values()) {
            CompoundNBT extraSettings = TournamentManager.getInstance().getTournamentExtraSettings(tournament.getName());
            boolean isRecurring = extraSettings != null && extraSettings.contains("isRecurring") && extraSettings.getBoolean("isRecurring");

            if (!isRecurring) {
                playerTournamentCount++;
            }
        }

        // Add recurring show all button if more than 5
        if (recurringCount > 5) {
            UIConfigLoader.ItemConfig recurringShowAllConfig = UIConfigLoader.getItemConfig(config, "recurring_show_all");
            if (recurringShowAllConfig != null && recurringShowAllConfig.getItem() != Items.BARRIER) {
                ItemStack recurringShowAll = new ItemStack(recurringShowAllConfig.getItem());
                recurringShowAll.setHoverName(new StringTextComponent(recurringShowAllConfig.getName())
                        .withStyle(recurringShowAllConfig.getColor()));

                CompoundNBT recurringShowAllTag = recurringShowAll.getOrCreateTag();
                recurringShowAllTag.putString("GuiAction", recurringShowAllConfig.getAction());

                int slot = UIConfigLoader.getSlot(config, "recurring_show_all_slot");
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    inventory.setItem(slot, recurringShowAll);
                }
            }
        }

        // Add player show all button if more than 5
        if (playerTournamentCount > 5) {
            UIConfigLoader.ItemConfig playerShowAllConfig = UIConfigLoader.getItemConfig(config, "player_show_all");
            if (playerShowAllConfig != null && playerShowAllConfig.getItem() != Items.BARRIER) {
                ItemStack playerShowAll = new ItemStack(playerShowAllConfig.getItem());
                playerShowAll.setHoverName(new StringTextComponent(playerShowAllConfig.getName())
                        .withStyle(playerShowAllConfig.getColor()));

                CompoundNBT playerShowAllTag = playerShowAll.getOrCreateTag();
                playerShowAllTag.putString("GuiAction", playerShowAllConfig.getAction());

                int slot = UIConfigLoader.getSlot(config, "player_show_all_slot");
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    inventory.setItem(slot, playerShowAll);
                }
            }
        }
    }

    /**
     * Populate recurring tournament slots - FIXED to prevent NullPointerException
     */
    private static void populateRecurringTournamentSlots(Inventory inventory, JsonObject config, ServerPlayerEntity player) {
        // Safety check for null config
        if (config == null) {
            Tournaments.LOGGER.warn("Config is null in populateRecurringTournamentSlots");
            return;
        }

        // Get tournament items safely - default to an empty object if not found
        JsonObject tournamentItems = config.has("tournament_items") ?
                config.getAsJsonObject("tournament_items") :
                new JsonObject();

        // Get recurring tournaments
        List<RecurringTournament> recurringTournaments = RecurringTournament.getAllRecurringTournaments();

        // Safely get the start slot - default to 29 as requested
        int startSlot = 29;
        if (config.has("slots")) {
            JsonObject slots = config.getAsJsonObject("slots");
            if (slots.has("recurring_matches_start")) {
                try {
                    startSlot = slots.get("recurring_matches_start").getAsInt();
                } catch (Exception e) {
                    Tournaments.LOGGER.warn("Error getting recurring_matches_start, using default", e);
                }
            }
        }

        int maxDisplayItems = 5; // Only show 5 items in the main view

        for (int i = 0; i < recurringTournaments.size() && i < maxDisplayItems; i++) {
            RecurringTournament tournament = recurringTournaments.get(i);
            int slot = startSlot + i;

            if (slot >= 54) break; // Prevent overflow

            // Create item for recurring tournament - safely get item config
            ItemStack tournamentItem;

            if (tournamentItems.has("recurring")) {
                JsonObject itemConfig = tournamentItems.getAsJsonObject("recurring");
                if (itemConfig.has("item")) {
                    tournamentItem = new ItemStack(UIConfigLoader.getItem(itemConfig.get("item").getAsString()));
                } else {
                    // Default to clock if item not specified
                    tournamentItem = new ItemStack(Items.CLOCK);
                }
            } else {
                // Default to clock if recurring item config not found
                tournamentItem = new ItemStack(Items.CLOCK);
            }

            // Set tournament name
            tournamentItem.setHoverName(new StringTextComponent(tournament.getName())
                    .withStyle(TextFormatting.AQUA, TextFormatting.BOLD));

            // Add description with lore
            List<ITextComponent> lore = new ArrayList<>();

            lore.add(new StringTextComponent("Template: " + tournament.getTemplateName())
                    .withStyle(TextFormatting.GRAY));

            lore.add(new StringTextComponent("Level Range: " + tournament.getMinLevel() + "-" + tournament.getMaxLevel())
                    .withStyle(TextFormatting.AQUA));

            lore.add(new StringTextComponent("Format: " + tournament.getFormat())
                    .withStyle(TextFormatting.LIGHT_PURPLE));

            lore.add(new StringTextComponent("Recurrence: Every " + formatHours(tournament.getRecurrenceHours()))
                    .withStyle(TextFormatting.GOLD));

            lore.add(new StringTextComponent("Next Occurrence: " + formatTimeUntil(tournament.getNextScheduled()))
                    .withStyle(TextFormatting.GREEN));

            if (tournament.getEntryFee() > 0) {
                lore.add(new StringTextComponent("Entry Fee: " + tournament.getEntryFee())
                        .withStyle(TextFormatting.YELLOW));
            }

            TournamentGuiHandler.setItemLore(tournamentItem, lore);

            // Add tournament ID to item NBT
            CompoundNBT nbt = tournamentItem.getOrCreateTag();
            nbt.putString("RecurringTournamentId", tournament.getName());
            nbt.putString("GuiAction", "view_recurring");

            inventory.setItem(slot, tournamentItem);
        }
    }

    /**
     * Populate the player tournament slots (non-recurring tournaments)
     */
    private static void populatePlayerTournamentSlots(Inventory inventory, JsonObject config, ServerPlayerEntity player) {
        // Safety check for null config
        if (config == null) {
            Tournaments.LOGGER.warn("Config is null in populatePlayerTournamentSlots");
            return;
        }

        // Get tournament items safely - default to an empty object if not found
        JsonObject tournamentItems = config.has("tournament_items") ?
                config.getAsJsonObject("tournament_items") :
                new JsonObject();

        // Get all current tournaments that are not recurring
        Map<String, Tournament> allTournaments = TournamentManager.getInstance().getAllTournaments();
        List<Tournament> playerTournaments = new ArrayList<>();

        // Filter out recurring tournaments
        for (Tournament tournament : allTournaments.values()) {
            CompoundNBT extraSettings = TournamentManager.getInstance().getTournamentExtraSettings(tournament.getName());
            boolean isRecurring = extraSettings != null && extraSettings.contains("isRecurring") && extraSettings.getBoolean("isRecurring");

            if (!isRecurring) {
                playerTournaments.add(tournament);
            }
        }

        // Get the player's tournament for highlighting
        Tournament playerTournament = TournamentManager.getInstance().getPlayerTournament(player);

        // Safely get the start slot - default to 38 as requested
        int startSlot = 38;
        if (config.has("slots")) {
            JsonObject slots = config.getAsJsonObject("slots");
            if (slots.has("player_matches_start")) {
                try {
                    startSlot = slots.get("player_matches_start").getAsInt();
                } catch (Exception e) {
                    Tournaments.LOGGER.warn("Error getting player_matches_start, using default", e);
                }
            }
        }

        int maxDisplayItems = 5; // Only show 5 items in the main view

        for (int i = 0; i < playerTournaments.size() && i < maxDisplayItems; i++) {
            Tournament tournament = playerTournaments.get(i);
            int slot = startSlot + i;

            if (slot >= 54) break; // Prevent overflow

            // Choose item based on tournament status - with safe checks
            ItemStack tournamentItem;
            JsonObject itemConfig = null;

            // If this is the player's tournament, highlight it
            if (playerTournament != null && playerTournament.getName().equals(tournament.getName())) {
                if (tournamentItems.has("player_tournament")) {
                    itemConfig = tournamentItems.getAsJsonObject("player_tournament");
                }
                tournamentItem = new ItemStack(itemConfig != null && itemConfig.has("item") ?
                        UIConfigLoader.getItem(itemConfig.get("item").getAsString()) : Items.GOLDEN_HELMET);
            } else {
                // Select based on tournament status
                String status;
                switch (tournament.getStatus()) {
                    case WAITING:
                        status = "waiting";
                        break;
                    case IN_PROGRESS:
                        status = "in_progress";
                        break;
                    case ENDED:
                        status = "ended";
                        break;
                    default:
                        status = "waiting";
                }

                if (tournamentItems.has(status)) {
                    itemConfig = tournamentItems.getAsJsonObject(status);
                }

                // Default items if config not found
                if (itemConfig != null && itemConfig.has("item")) {
                    tournamentItem = new ItemStack(UIConfigLoader.getItem(itemConfig.get("item").getAsString()));
                } else {
                    // Fallback items based on status
                    switch (status) {
                        case "waiting":
                            tournamentItem = new ItemStack(Items.PAPER);
                            break;
                        case "in_progress":
                            tournamentItem = new ItemStack(Items.BOOK);
                            break;
                        case "ended":
                            tournamentItem = new ItemStack(Items.WRITABLE_BOOK);
                            break;
                        default:
                            tournamentItem = new ItemStack(Items.PAPER);
                    }
                }
            }

            // Set tournament display name
            tournamentItem.setHoverName(new StringTextComponent(tournament.getName())
                    .withStyle(TextFormatting.AQUA, TextFormatting.BOLD));

            // Add description with lore
            List<ITextComponent> lore = new ArrayList<>();

            // Status with color
            TextFormatting statusColor;
            switch (tournament.getStatus()) {
                case WAITING:
                    statusColor = TextFormatting.GREEN;
                    break;
                case IN_PROGRESS:
                    statusColor = TextFormatting.GOLD;
                    break;
                case ENDED:
                    statusColor = TextFormatting.RED;
                    break;
                default:
                    statusColor = TextFormatting.WHITE;
            }

            // Combine the strings from the styled components
            ITextComponent statusText = new StringTextComponent("Status: ").withStyle(TextFormatting.GRAY);
            ITextComponent statusValue = new StringTextComponent(tournament.getStatus().toString()).withStyle(statusColor);
            lore.add(new StringTextComponent(statusText.getString() + statusValue.getString()));

            lore.add(new StringTextComponent("Players: " + tournament.getParticipantCount() +
                    "/" + tournament.getMaxParticipants())
                    .withStyle(TextFormatting.GRAY));

            // Add tournament settings to lore
            TournamentManager.TournamentSettings settings =
                    TournamentManager.getInstance().getTournamentSettings(tournament.getName());

            if (settings != null) {
                lore.add(new StringTextComponent("Level Range: " + settings.getMinLevel() +
                        "-" + settings.getMaxLevel())
                        .withStyle(TextFormatting.AQUA));

                lore.add(new StringTextComponent("Format: " + settings.getFormat())
                        .withStyle(TextFormatting.LIGHT_PURPLE));
            }

            // Add entry fee info if applicable
            CompoundNBT extraSettings = TournamentManager.getInstance()
                    .getTournamentExtraSettings(tournament.getName());

            if (extraSettings != null && extraSettings.contains("entryFee")) {
                double entryFee = extraSettings.getDouble("entryFee");
                if (entryFee > 0) {
                    lore.add(new StringTextComponent("Entry Fee: " + entryFee)
                            .withStyle(TextFormatting.GOLD));
                }
            }

            // Add scheduled start info if applicable
            if (tournament.getScheduledStartTime() != null && tournament.getStatus() == Tournament.TournamentStatus.WAITING) {
                Instant now = Instant.now();
                Instant startTime = tournament.getScheduledStartTime();

                if (startTime.isAfter(now)) {
                    lore.add(new StringTextComponent("Starts in: " + formatTimeUntil(startTime))
                            .withStyle(TextFormatting.YELLOW));
                }
            }

            // Add action instructions
            if (tournament.getStatus() == Tournament.TournamentStatus.WAITING &&
                    playerTournament == null) {
                lore.add(new StringTextComponent("Click to join")
                        .withStyle(TextFormatting.GREEN));
            } else if (tournament.getStatus() == Tournament.TournamentStatus.IN_PROGRESS) {
                lore.add(new StringTextComponent("Click to view matches")
                        .withStyle(TextFormatting.YELLOW));
            }

            // Add host info
            for (TournamentParticipant participant : tournament.getParticipants()) {
                if (participant.getPlayerId().equals(tournament.getHostId())) {
                    lore.add(new StringTextComponent("Host: " + participant.getPlayerName())
                            .withStyle(TextFormatting.WHITE));
                    break;
                }
            }

            TournamentGuiHandler.setItemLore(tournamentItem, lore);

            // Add the tournament name to item NBT for click handling
            CompoundNBT nbt = tournamentItem.getOrCreateTag();
            nbt.putString("TournamentName", tournament.getName());

            // If tournament is in progress, add a matches action
            if (tournament.getStatus() == Tournament.TournamentStatus.IN_PROGRESS) {
                nbt.putString("GuiAction", "matches");
            } else if (tournament.getStatus() == Tournament.TournamentStatus.WAITING) {
                nbt.putString("GuiAction", "join");
            }

            inventory.setItem(slot, tournamentItem);
        }
    }

    /**
     * Populate recurring tournaments GUI (show all view)
     */
    private static void populateRecurringTournamentsGui(Inventory inventory, JsonObject config, ServerPlayerEntity player) {
        // Apply borders first
        UIConfigLoader.applyBorders(inventory, config);

        // Add back button
        UIConfigLoader.ItemConfig backConfig = UIConfigLoader.getItemConfig(config, "back_button");
        ItemStack backButton = new ItemStack(backConfig.getItem());
        backButton.setHoverName(new StringTextComponent(backConfig.getName())
                .withStyle(backConfig.getColor()));

        CompoundNBT tag = backButton.getOrCreateTag();
        tag.putString("GuiAction", backConfig.getAction());
        inventory.setItem(UIConfigLoader.getSlot(config, "back_button_slot"), backButton);

        // Get all recurring tournaments
        List<RecurringTournament> recurringTournaments = RecurringTournament.getAllRecurringTournaments();

        // Start populating from configured slot
        int slot = UIConfigLoader.getSlot(config, "matches_start_slot");

        for (RecurringTournament tournament : recurringTournaments) {
            if (slot >= 54) break; // Prevent overflow

            // Create item for recurring tournament
            ItemStack tournamentItem = new ItemStack(Items.CLOCK);

            // Set tournament name
            tournamentItem.setHoverName(new StringTextComponent(tournament.getName())
                    .withStyle(TextFormatting.AQUA, TextFormatting.BOLD));

            // Add description with lore
            List<ITextComponent> lore = new ArrayList<>();

            lore.add(new StringTextComponent("Template: " + tournament.getTemplateName())
                    .withStyle(TextFormatting.GRAY));

            lore.add(new StringTextComponent("Level Range: " + tournament.getMinLevel() + "-" + tournament.getMaxLevel())
                    .withStyle(TextFormatting.AQUA));

            lore.add(new StringTextComponent("Format: " + tournament.getFormat())
                    .withStyle(TextFormatting.LIGHT_PURPLE));

            lore.add(new StringTextComponent("Recurrence: Every " + formatHours(tournament.getRecurrenceHours()))
                    .withStyle(TextFormatting.GOLD));

            lore.add(new StringTextComponent("Next Occurrence: " + formatTimeUntil(tournament.getNextScheduled()))
                    .withStyle(TextFormatting.GREEN));

            if (tournament.getEntryFee() > 0) {
                lore.add(new StringTextComponent("Entry Fee: " + tournament.getEntryFee())
                        .withStyle(TextFormatting.YELLOW));
            }

            // Admin actions if player has permission
            if (RecurringTournamentHandler.canCreateRecurringTournament(player)) {
                lore.add(new StringTextComponent("Right click to delete")
                        .withStyle(TextFormatting.RED));
            }

            TournamentGuiHandler.setItemLore(tournamentItem, lore);

            // Add tournament ID to item NBT
            CompoundNBT nbt = tournamentItem.getOrCreateTag();
            nbt.putString("RecurringTournamentId", tournament.getName());
            nbt.putString("GuiAction", "view_recurring");

            inventory.setItem(slot, tournamentItem);
            slot++;
        }

        // Add "Create Recurring Tournament" button for admins
        if (RecurringTournamentHandler.canCreateRecurringTournament(player) && slot < 54) {
            ItemStack createButton = new ItemStack(Items.EMERALD);
            createButton.setHoverName(new StringTextComponent("Create Recurring Tournament")
                    .withStyle(TextFormatting.GREEN, TextFormatting.BOLD));

            List<ITextComponent> lore = new ArrayList<>();
            lore.add(new StringTextComponent("Click to create a new recurring tournament")
                    .withStyle(TextFormatting.GRAY));
            TournamentGuiHandler.setItemLore(createButton, lore);

            CompoundNBT createTag = createButton.getOrCreateTag();
            createTag.putString("GuiAction", "create_recurring");

            inventory.setItem(slot, createButton);
        }
    }


    /**
     * Populate player tournaments GUI (show all view)
     */
    private static void populatePlayerTournamentsGui(Inventory inventory, JsonObject config, ServerPlayerEntity player) {
        // Apply borders first
        UIConfigLoader.applyBorders(inventory, config);

        // Add back button
        UIConfigLoader.ItemConfig backConfig = UIConfigLoader.getItemConfig(config, "back_button");
        ItemStack backButton = new ItemStack(backConfig.getItem());
        backButton.setHoverName(new StringTextComponent(backConfig.getName())
                .withStyle(backConfig.getColor()));

        CompoundNBT tag = backButton.getOrCreateTag();
        tag.putString("GuiAction", backConfig.getAction());
        inventory.setItem(UIConfigLoader.getSlot(config, "back_button_slot"), backButton);

        // Get all player tournaments (non-recurring)
        Map<String, Tournament> allTournaments = TournamentManager.getInstance().getAllTournaments();
        List<Tournament> playerTournaments = new ArrayList<>();

        // Filter out recurring tournaments
        for (Tournament tournament : allTournaments.values()) {
            CompoundNBT extraSettings = TournamentManager.getInstance().getTournamentExtraSettings(tournament.getName());
            boolean isRecurring = extraSettings != null && extraSettings.contains("isRecurring") && extraSettings.getBoolean("isRecurring");

            if (!isRecurring) {
                playerTournaments.add(tournament);
            }
        }

        // Get the player's tournament for highlighting
        Tournament playerTournament = TournamentManager.getInstance().getPlayerTournament(player);

        // Start populating from configured slot
        int slot = UIConfigLoader.getSlot(config, "matches_start_slot");

        for (Tournament tournament : playerTournaments) {
            if (slot >= 54) break; // Prevent overflow

            // Choose item based on tournament status
            ItemStack tournamentItem;

            // If this is the player's tournament, highlight it
            if (playerTournament != null && playerTournament.getName().equals(tournament.getName())) {
                tournamentItem = new ItemStack(Items.GOLDEN_HELMET);
            } else {
                // Select based on tournament status
                switch (tournament.getStatus()) {
                    case WAITING:
                        tournamentItem = new ItemStack(Items.PAPER);
                        break;
                    case IN_PROGRESS:
                        tournamentItem = new ItemStack(Items.BOOK);
                        break;
                    case ENDED:
                        tournamentItem = new ItemStack(Items.WRITABLE_BOOK);
                        break;
                    default:
                        tournamentItem = new ItemStack(Items.PAPER);
                        break;
                }
            }

            // Set tournament display name
            tournamentItem.setHoverName(new StringTextComponent(tournament.getName())
                    .withStyle(TextFormatting.AQUA, TextFormatting.BOLD));

            // Add description with lore
            List<ITextComponent> lore = new ArrayList<>();

            // Status with color
            TextFormatting statusColor;
            switch (tournament.getStatus()) {
                case WAITING:
                    statusColor = TextFormatting.GREEN;
                    break;
                case IN_PROGRESS:
                    statusColor = TextFormatting.GOLD;
                    break;
                case ENDED:
                    statusColor = TextFormatting.RED;
                    break;
                default:
                    statusColor = TextFormatting.WHITE;
            }

            // Add status text
            lore.add(new StringTextComponent("Status: " + tournament.getStatus())
                    .withStyle(statusColor));

            lore.add(new StringTextComponent("Players: " + tournament.getParticipantCount() +
                    "/" + tournament.getMaxParticipants())
                    .withStyle(TextFormatting.GRAY));

            // Add tournament settings to lore
            TournamentManager.TournamentSettings settings =
                    TournamentManager.getInstance().getTournamentSettings(tournament.getName());

            if (settings != null) {
                lore.add(new StringTextComponent("Level Range: " + settings.getMinLevel() +
                        "-" + settings.getMaxLevel())
                        .withStyle(TextFormatting.AQUA));

                lore.add(new StringTextComponent("Format: " + settings.getFormat())
                        .withStyle(TextFormatting.LIGHT_PURPLE));
            }

            // Add entry fee info if applicable
            CompoundNBT extraSettings = TournamentManager.getInstance()
                    .getTournamentExtraSettings(tournament.getName());

            if (extraSettings != null && extraSettings.contains("entryFee")) {
                double entryFee = extraSettings.getDouble("entryFee");
                if (entryFee > 0) {
                    lore.add(new StringTextComponent("Entry Fee: " + entryFee)
                            .withStyle(TextFormatting.GOLD));
                }
            }

            // Add scheduled start info if applicable
            if (tournament.getScheduledStartTime() != null && tournament.getStatus() == Tournament.TournamentStatus.WAITING) {
                Instant now = Instant.now();
                Instant startTime = tournament.getScheduledStartTime();

                if (startTime.isAfter(now)) {
                    lore.add(new StringTextComponent("Starts in: " + formatTimeUntil(startTime))
                            .withStyle(TextFormatting.YELLOW));
                }
            }

            // Add action instructions
            if (tournament.getStatus() == Tournament.TournamentStatus.WAITING &&
                    playerTournament == null) {
                lore.add(new StringTextComponent("Click to join")
                        .withStyle(TextFormatting.GREEN));
            } else if (tournament.getStatus() == Tournament.TournamentStatus.IN_PROGRESS) {
                lore.add(new StringTextComponent("Click to view matches")
                        .withStyle(TextFormatting.YELLOW));
            }

            // Admin option to delete
            if (player.hasPermissions(2)) {
                lore.add(new StringTextComponent("Right click to delete")
                        .withStyle(TextFormatting.RED));
            }

            TournamentGuiHandler.setItemLore(tournamentItem, lore);

            // Add the tournament name to item NBT for click handling
            CompoundNBT nbt = tournamentItem.getOrCreateTag();
            nbt.putString("TournamentName", tournament.getName());

            // If tournament is in progress, add a matches action
            if (tournament.getStatus() == Tournament.TournamentStatus.IN_PROGRESS) {
                nbt.putString("GuiAction", "matches");
            } else if (tournament.getStatus() == Tournament.TournamentStatus.WAITING) {
                nbt.putString("GuiAction", "join");
            }

            inventory.setItem(slot, tournamentItem);
            slot++;
        }

        // Add "Create Tournament" button for players with permission
        if (RecurringTournamentHandler.canCreatePlayerTournament(player) && slot < 54) {
            ItemStack createButton = new ItemStack(Items.EMERALD);
            createButton.setHoverName(new StringTextComponent("Create Tournament")
                    .withStyle(TextFormatting.GREEN, TextFormatting.BOLD));

            List<ITextComponent> lore = new ArrayList<>();
            lore.add(new StringTextComponent("Click to create a new tournament")
                    .withStyle(TextFormatting.GRAY));
            TournamentGuiHandler.setItemLore(createButton, lore);

            CompoundNBT createTag = createButton.getOrCreateTag();
            createTag.putString("GuiAction", "create_player");

            inventory.setItem(slot, createButton);
        }
    }

    /**
     * Populate matches GUI
     */
    private static void populateMatchesGui(Inventory inventory, Tournament tournament) {
        JsonObject config = UIConfigLoader.getMatchesScreenConfig();

        // Apply borders first
        UIConfigLoader.applyBorders(inventory, config);

        // Add back button
        UIConfigLoader.ItemConfig backConfig = UIConfigLoader.getItemConfig(config, "back_button");
        if (backConfig != null && backConfig.getItem() != Items.BARRIER) {
            ItemStack backButton = new ItemStack(backConfig.getItem());
            backButton.setHoverName(new StringTextComponent(backConfig.getName())
                    .withStyle(backConfig.getColor()));

            CompoundNBT tag = backButton.getOrCreateTag();
            tag.putString("GuiAction", backConfig.getAction());
            inventory.setItem(UIConfigLoader.getSlot(config, "back_button_slot"), backButton);
        } else {
            // Fallback to default
            ItemStack backButton = new ItemStack(Items.ARROW);
            backButton.setHoverName(new StringTextComponent("Back").withStyle(TextFormatting.GRAY));
            CompoundNBT tag = backButton.getOrCreateTag();
            tag.putString("GuiAction", "back");
            inventory.setItem(UIConfigLoader.getSlot(config, "back_button_slot"), backButton);
        }

        // Get matches
        List<TournamentMatch> matches = tournament.getCurrentMatches();
        JsonObject items = config != null && config.has("items") ? config.getAsJsonObject("items") : null;

        // Start populating from configured slot
        int slot = config != null ? UIConfigLoader.getSlot(config, "matches_start_slot") : 18;
        for (TournamentMatch match : matches) {
            if (slot >= 54) break;  // Prevent overflow

            ItemStack matchItem;
            String itemType;

            // Choose item based on match status
            switch (match.getStatus()) {
                case SCHEDULED:
                    itemType = "scheduled_match";
                    break;
                case IN_PROGRESS:
                    itemType = "in_progress_match";
                    break;
                case COMPLETED:
                    itemType = "completed_match";
                    break;
                case CANCELLED:
                    itemType = "cancelled_match";
                    break;
                default:
                    itemType = "scheduled_match";
            }

            // Get item from config with safe checks
            if (items != null && items.has(itemType)) {
                JsonObject itemConfig = items.getAsJsonObject(itemType);
                if (itemConfig.has("item")) {
                    matchItem = new ItemStack(UIConfigLoader.getItem(itemConfig.get("item").getAsString()));
                } else {
                    // Default items based on status
                    matchItem = getDefaultMatchItem(match.getStatus());
                }
            } else {
                // Default items based on status
                matchItem = getDefaultMatchItem(match.getStatus());
            }

            // Set match details
            matchItem.setHoverName(new StringTextComponent(match.getDescription())
                    .withStyle(TextFormatting.AQUA));

            // Add description with lore
            List<ITextComponent> lore = new ArrayList<>();
            lore.add(new StringTextComponent("Status: " + match.getStatus())
                    .withStyle(TextFormatting.GRAY));

            // Add winner info if match is completed
            if (match.getStatus() == TournamentMatch.MatchStatus.COMPLETED && match.getWinnerId() != null) {
                String winnerName = match.getPlayer1Id().equals(match.getWinnerId()) ?
                        match.getPlayer1Name() : match.getPlayer2Name();
                lore.add(new StringTextComponent("Winner: " + winnerName)
                        .withStyle(TextFormatting.GOLD));
            }

            TournamentGuiHandler.setItemLore(matchItem, lore);
            inventory.setItem(slot, matchItem);
            slot++;
        }
    }

    /**
     * Get default item for match based on status
     */
    private static ItemStack getDefaultMatchItem(TournamentMatch.MatchStatus status) {
        switch (status) {
            case SCHEDULED:
                return new ItemStack(Items.CLOCK);
            case IN_PROGRESS:
                return new ItemStack(Items.DIAMOND_SWORD);
            case COMPLETED:
                return new ItemStack(Items.EMERALD);
            case CANCELLED:
                return new ItemStack(Items.BARRIER);
            default:
                return new ItemStack(Items.CLOCK);
        }
    }

    @SubscribeEvent
    public static void onContainerClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity) ||
                event.getPlayer().containerMenu == null) {
            return;
        }

        if (tournamentContainerIds.contains(event.getPlayer().containerMenu.containerId)) {
            // This is a tournament container
            Tournaments.LOGGER.debug("Detected click in tournament container");
        }
    }

    // Custom click handler - needs to be called from the container
    public static boolean onInventoryClick(Container container, int slotId, int dragType,
                                           ClickType clickType, ServerPlayerEntity player) {
        if (slotId < 0 || slotId >= container.slots.size()) {
            return false;
        }

        Slot slot = container.slots.get(slotId);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem().copy();  // Copy to avoid issues with the original stack

            if (stack.hasTag() && stack.getTag().contains("GuiAction")) {
                CompoundNBT tag = stack.getTag();
                String action = tag.getString("GuiAction");
                String tournamentName = tag.contains("TournamentName") ?
                        tag.getString("TournamentName") : null;
                String recurringId = tag.contains("RecurringTournamentId") ?
                        tag.getString("RecurringTournamentId") : null;

                Tournaments.LOGGER.debug("Processing GUI action: {} for tournament: {}", action, tournamentName);

                // Process action
                processGuiAction(player, action, tournamentName, recurringId, clickType);
                return true;  // We handled the click
            }
        }

        return false;  // Let the normal handling continue
    }

    /**
     * Process GUI actions - with full parameters
     */
    public static void processGuiAction(ServerPlayerEntity player, String action, String tournamentName,
                                        String recurringId, ClickType clickType) {
        // Prevent recursion
        if (processingAction) {
            Tournaments.LOGGER.warn("Preventing recursive GUI action processing");
            return;
        }

        processingAction = true;

        try {
            TournamentManager manager = TournamentManager.getInstance();

            // Close container first to prevent issues
            player.closeContainer();

            Tournaments.LOGGER.info("GUI FLOW DEBUG: Processing action '{}'", action);

            // Handle creating regular tournaments
            if ("create_player".equals(action)) {
                if (RecurringTournamentHandler.canCreatePlayerTournament(player)) {
                    // Open regular tournament creation GUI
                    TournamentCreationGUI.openCreationGUI(player);
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to create tournaments")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                    // Reopen main GUI
                    openMainGui(player);
                }
                return;
            }

            // Handle creating recurring tournaments
            if ("create_recurring".equals(action)) {
                if (RecurringTournamentHandler.canCreateRecurringTournament(player)) {
                    TournamentRecurringCreationGUI.openCreationGUI(player);
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to create recurring tournaments")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                    openMainGui(player);
                }
                return;
            }

            // Add handling for createRecurringTournament action
            if ("createRecurringTournament".equals(action)) {
                if (RecurringTournamentHandler.canCreateRecurringTournament(player)) {
                    // Create the recurring tournament
                    TournamentRecurringCreationGUI.createRecurringTournament(player);
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to create recurring tournaments")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                    openMainGui(player);
                }
                return;
            }

            switch (action) {
                case "join":
                    if (tournamentName != null) {
                        if (manager.joinTournament(tournamentName, player)) {
                            player.sendMessage(
                                    new StringTextComponent("Successfully joined tournament: " + tournamentName)
                                            .withStyle(TextFormatting.GREEN),
                                    player.getUUID());
                        }
                    }
                    // Reopen main GUI after join action
                    openMainGui(player);
                    break;

                case "leave":
                    if (manager.leaveTournament(player)) {
                        player.sendMessage(
                                new StringTextComponent("Successfully left tournament")
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    } else {
                        player.sendMessage(
                                new StringTextComponent("You are not in a tournament")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    }
                    // Reopen main GUI after leave action
                    openMainGui(player);
                    break;

                case "start":
                    Tournament playerTournament = manager.getPlayerTournament(player);
                    if (playerTournament != null &&
                            player.getUUID().equals(playerTournament.getHostId()) &&
                            playerTournament.getStatus() == Tournament.TournamentStatus.WAITING) {

                        playerTournament.start();
                        player.sendMessage(
                                new StringTextComponent("Tournament started!")
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    } else {
                        player.sendMessage(
                                new StringTextComponent("You cannot start this tournament")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    }
                    // Reopen main GUI after start action
                    openMainGui(player);
                    break;

                case "matches":
                    if (tournamentName != null) {
                        openMatchesGui(player, tournamentName);
                        // Do NOT reopen main GUI here
                    } else {
                        // If no tournament name provided, go back to main GUI
                        openMainGui(player);
                    }
                    break;

                case "view_recurring":
                    if (recurringId != null) {
                        // Get the recurring tournament details
                        RecurringTournament tournament = RecurringTournament.getRecurringTournament(recurringId);

                        if (tournament != null) {
                            // Display tournament details in chat
                            player.sendMessage(
                                    new StringTextComponent("=== Recurring Tournament: " + tournament.getName() + " ===")
                                            .withStyle(TextFormatting.GOLD),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Template Name: " + tournament.getTemplateName())
                                            .withStyle(TextFormatting.YELLOW),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Level Range: " + tournament.getMinLevel() + "-" + tournament.getMaxLevel())
                                            .withStyle(TextFormatting.AQUA),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Format: " + tournament.getFormat())
                                            .withStyle(TextFormatting.LIGHT_PURPLE),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Recurrence: Every " + formatHours(tournament.getRecurrenceHours()))
                                            .withStyle(TextFormatting.GREEN),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Next Occurrence: " + formatTimeUntil(tournament.getNextScheduled()))
                                            .withStyle(TextFormatting.GREEN),
                                    player.getUUID());

                            // Check if right-clicked and has permission to delete
                            if (clickType == ClickType.values()[1] && RecurringTournamentHandler.canCreateRecurringTournament(player)) {
                                player.sendMessage(
                                        new StringTextComponent("To delete this recurring tournament, type: /tournament deleterecurring " + tournament.getName())
                                                .withStyle(TextFormatting.RED),
                                        player.getUUID());
                            }
                        } else {
                            player.sendMessage(
                                    new StringTextComponent("Recurring tournament not found: " + recurringId)
                                            .withStyle(TextFormatting.RED),
                                    player.getUUID());
                        }
                    }

                    // Reopen the GUI they were on before
                    if ("show_all_recurring".equals(action)) {
                        openRecurringTournamentsGui(player);
                    } else {
                        openMainGui(player);
                    }
                    break;

                case "back":
                    // Go back to main GUI
                    openMainGui(player);
                    break;

                case "show_all_recurring":
                    // Show all recurring tournaments
                    openRecurringTournamentsGui(player);
                    break;

                case "show_all_player":
                    // Show all player tournaments
                    openPlayerTournamentsGui(player);
                    break;

                case "reloadconfig":
                    if (player.hasPermissions(2)) {
                        try {
                            // Reload the config
                            UIConfigLoader.loadConfig(null);
                            UIConfigLoader.saveConfig();

                            player.sendMessage(
                                    new StringTextComponent("Tournament UI configuration reloaded successfully")
                                            .withStyle(TextFormatting.GREEN),
                                    player.getUUID());

                            Tournaments.LOGGER.info("UI config reloaded by {}", player.getName().getString());
                        } catch (Exception e) {
                            Tournaments.LOGGER.error("Failed to reload UI config", e);
                            player.sendMessage(
                                    new StringTextComponent("Error reloading UI configuration: " + e.getMessage())
                                            .withStyle(TextFormatting.RED),
                                    player.getUUID());
                        }
                        // Reopen main GUI
                        openMainGui(player);
                    }
                    break;

                default:
                    if ("none".equals(action)) {
                        // Just reopen the main GUI for "none" action
                        openMainGui(player);
                    } else {
                        // Unknown action, just reopen main GUI
                        player.sendMessage(
                                new StringTextComponent("Unknown action: " + action)
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                        openMainGui(player);
                    }
                    break;
            }
        } finally {
            // Always reset the processing flag to prevent deadlocks
            processingAction = false;
        }
    }

    /**
     * Process GUI actions - simpler version that forwards to full version
     */
    public static void processGuiAction(ServerPlayerEntity player, String action, String tournamentName) {
        // For backward compatibility, map "create" to the appropriate action
        if ("create".equals(action)) {
            // Check permissions and redirect to appropriate creation GUI
            boolean canCreateRecurring = RecurringTournamentHandler.canCreateRecurringTournament(player);
            boolean canCreatePlayer = RecurringTournamentHandler.canCreatePlayerTournament(player);

            player.closeContainer();

            if (canCreatePlayer) {
                // Open regular tournament creation
                TournamentCreationGUI.openCreationGUI(player);
                return;
            }
        }

        // For other actions, call the 5-param version (which now has recursion protection)
        processGuiAction(player, action, tournamentName, null, null);
    }

    /**
     * Format time in hours nicely (e.g., "2 hours" or "30 minutes")
     */
    private static String formatHours(double hours) {
        if (hours >= 1) {
            if (hours == Math.floor(hours)) {
                return (int) hours + " hours";
            } else {
                return String.format("%.1f hours", hours);
            }
        } else {
            return (int) (hours * 60) + " minutes";
        }
    }

    /**
     * Format time until a future instant
     */
    private static String formatTimeUntil(Instant target) {
        if (target == null) {
            return "Never";
        }

        Instant now = Instant.now();
        if (now.isAfter(target)) {
            return "Now";
        }

        Duration duration = Duration.between(now, target);
        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            if (minutes == 0) {
                return hours + " hours";
            } else {
                return hours + " hours, " + minutes + " minutes";
            }
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            if (hours == 0) {
                return days + " days";
            } else {
                return days + " days, " + hours + " hours";
            }
        }
    }
}