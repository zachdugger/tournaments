package com.blissy.tournaments.gui;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.config.UIConfigLoader;
import com.blissy.tournaments.data.EloPlayer;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.data.TournamentParticipant;
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

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class TournamentMainGUI {

    // Store a reference to containers for click detection
    private static final List<Integer> tournamentContainerIds = new ArrayList<>();

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
     * Populate the main tournament GUI
     */
    private static void populateMainGui(Inventory inventory, ServerPlayerEntity player) {
        JsonObject config = UIConfigLoader.getMainScreenConfig();
        TournamentManager manager = TournamentManager.getInstance();
        Tournament playerTournament = manager.getPlayerTournament(player);

        // First, apply the borders to the inventory
        UIConfigLoader.applyBorders(inventory, config);

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

        // Add create tournament button for players with permission
        if (player.hasPermissions(2)) {
            UIConfigLoader.ItemConfig createConfig = UIConfigLoader.getItemConfig(config, "create_button");
            ItemStack createButton = new ItemStack(createConfig.getItem());
            createButton.setHoverName(new StringTextComponent(createConfig.getName())
                    .withStyle(createConfig.getColor()));

            CompoundNBT tag = createButton.getOrCreateTag();
            tag.putString("GuiAction", createConfig.getAction());
            inventory.setItem(UIConfigLoader.getSlot(config, "create_button_slot"), createButton);

            // Add reload config button for admins
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

        // Populate leaderboard slots
        List<EloPlayer> topPlayers = Tournaments.ELO_MANAGER.getTopPlayers(6);

        for (int i = 0; i < topPlayers.size(); i++) {
            EloPlayer eloPlayer = topPlayers.get(i);

            ItemStack playerHead = new ItemStack(Items.PLAYER_HEAD);

            CompoundNBT tag = playerHead.getOrCreateTag();
            tag.putString("SkullOwner", eloPlayer.getPlayerName());

            // Fixed to not use append
            playerHead.setHoverName(
                    new StringTextComponent("#" + (i + 1) + " " + eloPlayer.getPlayerName() + " (" + eloPlayer.getElo() + " ELO)")
                            .withStyle(TextFormatting.GOLD)
            );

            List<ITextComponent> lore = new ArrayList<>();
            lore.add(new StringTextComponent("Wins: " + eloPlayer.getWins())
                    .withStyle(TextFormatting.GREEN));
            lore.add(new StringTextComponent("Losses: " + eloPlayer.getLosses())
                    .withStyle(TextFormatting.RED));

            TournamentGuiHandler.setItemLore(playerHead, lore);

            inventory.setItem(
                    UIConfigLoader.getSlot(config, "leaderboard_" + (i + 1)),
                    playerHead
            );
        }

        // Add player stats book
        ItemStack statsBook = new ItemStack(Items.WRITTEN_BOOK);
        statsBook.setHoverName(new StringTextComponent("Your Tournament Stats")
                .withStyle(TextFormatting.GOLD));

        // Set the author and title
        CompoundNBT bookTag = statsBook.getOrCreateTag();
        bookTag.putString("author", player.getName().getString());
        bookTag.putString("title", "Tournament Stats");
        bookTag.putBoolean("resolved", true);

        ListNBT pages = new ListNBT();

        // Use getOrCreatePlayer to ensure we have an EloPlayer even for players without stats
        EloPlayer eloPlayer = Tournaments.ELO_MANAGER.getOrCreatePlayer(player.getUUID());

        // Build the text content using string concatenation
        String statsText = new StringTextComponent("ELO Rating: " + eloPlayer.getElo())
                .withStyle(TextFormatting.GOLD).getString() + "\n\n";

        statsText += new StringTextComponent("Wins: " + eloPlayer.getWins())
                .withStyle(TextFormatting.GREEN).getString() + "\n\n";

        statsText += new StringTextComponent("Losses: " + eloPlayer.getLosses())
                .withStyle(TextFormatting.RED).getString() + "\n\n";

        // Calculate win rate
        int totalGames = eloPlayer.getWins() + eloPlayer.getLosses();
        if (totalGames > 0) {
            float winRate = (float)eloPlayer.getWins() * 100f / totalGames;
            statsText += new StringTextComponent("Win Rate: " + String.format("%.1f%%", winRate))
                    .withStyle(TextFormatting.AQUA).getString();
        } else {
            statsText += new StringTextComponent("Win Rate: 0.0%")
                    .withStyle(TextFormatting.AQUA).getString();
        }

        ITextComponent page = new StringTextComponent(statsText);
        pages.add(StringNBT.valueOf(ITextComponent.Serializer.toJson(page)));
        bookTag.put("pages", pages);

        // Add GuiAction
        bookTag.putString("GuiAction", "openStatsBook");

        inventory.setItem(
                UIConfigLoader.getSlot(config, "stats_book_slot"),
                statsBook
        );

        // Populate tournaments
        populateTournamentSlots(inventory, player);
    }

    /**
     * Populate tournament slots
     */
    private static void populateTournamentSlots(Inventory inventory, ServerPlayerEntity player) {
        JsonObject config = UIConfigLoader.getMainScreenConfig();
        JsonObject tournamentItems = config.getAsJsonObject("tournament_items");

        // Get all current tournaments
        Map<String, Tournament> tournaments = TournamentManager.getInstance().getAllTournaments();
        Tournament playerTournament = TournamentManager.getInstance().getPlayerTournament(player);

        // Start populating from configured slot
        int slot = UIConfigLoader.getSlot(config, "tournaments_start_slot");
        for (Tournament tournament : tournaments.values()) {
            if (slot >= 54) break;  // Prevent overflow

            // Choose item based on tournament status
            ItemStack tournamentItem;
            JsonObject itemConfig;

            // If this is the player's tournament, highlight it
            if (playerTournament != null && playerTournament.getName().equals(tournament.getName())) {
                itemConfig = tournamentItems.getAsJsonObject("player_tournament");
                tournamentItem = new ItemStack(UIConfigLoader.getItem(itemConfig.get("item").getAsString()));
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

                itemConfig = tournamentItems.getAsJsonObject(status);
                tournamentItem = new ItemStack(UIConfigLoader.getItem(itemConfig.get("item").getAsString()));
            }

            // Set tournament display name (use custom name instead of default)
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

            // Fixed to not use incompatible type assignments
            ITextComponent statusText = new StringTextComponent("Status: ").withStyle(TextFormatting.GRAY);
            ITextComponent statusValue = new StringTextComponent(tournament.getStatus().toString()).withStyle(statusColor);

            // Combine the strings from the styled components
            lore.add(new StringTextComponent(statusText.getString() + statusValue.getString()));

            lore.add(new StringTextComponent("Players: " + tournament.getParticipantCount() +
                    "/" + tournament.getMaxParticipants())
                    .withStyle(TextFormatting.GRAY));

            // Add tournament settings to lore
            TournamentManager.TournamentSettings settings =
                    TournamentManager.getInstance().getTournamentSettings(tournament.getName());

            lore.add(new StringTextComponent("Level Range: " + settings.getMinLevel() +
                    "-" + settings.getMaxLevel())
                    .withStyle(TextFormatting.AQUA));

            lore.add(new StringTextComponent("Format: " + settings.getFormat())
                    .withStyle(TextFormatting.LIGHT_PURPLE));

            // Add entry fee info if applicable
            CompoundNBT extraSettings = TournamentManager.getInstance()
                    .getTournamentExtraSettings(tournament.getName());

            if (extraSettings.contains("entryFee")) {
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
                    long secondsUntilStart = java.time.Duration.between(now, startTime).getSeconds();
                    String timeDisplay;

                    if (secondsUntilStart < 60) {
                        timeDisplay = secondsUntilStart + " seconds";
                    } else if (secondsUntilStart < 3600) {
                        timeDisplay = (secondsUntilStart / 60) + " minutes";
                    } else {
                        double hoursLeft = secondsUntilStart / 3600.0;
                        timeDisplay = String.format("%.1f hours", hoursLeft);
                    }

                    lore.add(new StringTextComponent("Starts in: " + timeDisplay)
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
            ServerPlayerEntity host = null;
            for (TournamentParticipant participant : tournament.getParticipants()) {
                if (participant.getPlayerId().equals(tournament.getHostId())) {
                    host = participant.getPlayer();
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
            slot++;
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
        if (backConfig.getItem() != Items.BARRIER) {
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
        JsonObject items = config.getAsJsonObject("items");

        // Start populating from configured slot
        int slot = UIConfigLoader.getSlot(config, "matches_start_slot");
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

            // Get item from config
            JsonObject itemConfig = items.getAsJsonObject(itemType);
            matchItem = new ItemStack(UIConfigLoader.getItem(itemConfig.get("item").getAsString()));

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
     * Process clicks in tournament containers
     */
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

                Tournaments.LOGGER.debug("Processing GUI action: {} for tournament: {}", action, tournamentName);

                // Process action
                processGuiAction(player, action, tournamentName);
                return true;  // We handled the click
            }
        }

        return false;  // Let the normal handling continue
    }

    /**
     * Process GUI actions
     */
    public static void processGuiAction(ServerPlayerEntity player, String action, String tournamentName) {
        TournamentManager manager = TournamentManager.getInstance();

        // Close container first to prevent issues
        player.closeContainer();

        Tournaments.LOGGER.info("GUI FLOW DEBUG: Processing action '{}' in TournamentMainGUI", action);

        switch (action) {
            case "join":
                if (tournamentName != null) {
                    if (manager.joinTournament(tournamentName, player)) {
                        player.sendMessage(
                                new StringTextComponent("Successfully joined tournament: " + tournamentName)
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    }
                    // Note: We don't need an else block here as joinTournament now sends its own error messages
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

            case "create":
                Tournaments.LOGGER.info("GUI FLOW DEBUG: Create tournament action detected");
                // Open tournament creation GUI instead of directly creating
                if (player.hasPermissions(2)) {
                    Tournaments.LOGGER.info("GUI FLOW DEBUG: Player has permission, opening creation GUI");
                    TournamentCreationGUI.openCreationGUI(player);
                    // Do NOT reopen main GUI here!
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to create tournaments")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                    // Reopen main GUI since we're not opening creation GUI
                    openMainGui(player);
                }
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

            case "back":
                // Go back to main GUI
                openMainGui(player);
                break;

            case "help":
                player.sendMessage(
                        new StringTextComponent("Tournament commands: /tournament join, leave, list, create, ready, delete")
                                .withStyle(TextFormatting.YELLOW),
                        player.getUUID());
                // Reopen main GUI after help action
                openMainGui(player);
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

                        Tournaments.LOGGER.info("UI config reloaded by {} via GUI", player.getName().getString());
                    } catch (Exception e) {
                        Tournaments.LOGGER.error("Failed to reload UI config via GUI", e);
                        player.sendMessage(
                                new StringTextComponent("Error reloading UI configuration: " + e.getMessage())
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    }
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to reload the configuration")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                }
                // Reopen main GUI after reload
                openMainGui(player);
                break;

            case "openStatsBook":
                // Create a readable book to open
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                CompoundNBT bookTag = book.getOrCreateTag();
                bookTag.putString("author", player.getName().getString());
                bookTag.putString("title", "Tournament Stats");
                bookTag.putBoolean("resolved", true);

                ListNBT pages = new ListNBT();

                // Get or create EloPlayer to ensure stats exist even if player has no stats
                EloPlayer eloPlayer = Tournaments.ELO_MANAGER.getOrCreatePlayer(player.getUUID());

                // Format the stats text with colors
                String statsText = "";

                // Add ELO Rating with gold color
                statsText += new StringTextComponent("ELO Rating: " + eloPlayer.getElo())
                        .withStyle(TextFormatting.GOLD).getString() + "\n\n";

                // Add wins with green color
                statsText += new StringTextComponent("Wins: " + eloPlayer.getWins())
                        .withStyle(TextFormatting.GREEN).getString() + "\n\n";

                // Add losses with red color
                statsText += new StringTextComponent("Losses: " + eloPlayer.getLosses())
                        .withStyle(TextFormatting.RED).getString() + "\n\n";

                // Calculate win rate
                int totalGames = eloPlayer.getWins() + eloPlayer.getLosses();
                if (totalGames > 0) {
                    float winRate = (float)eloPlayer.getWins() * 100f / totalGames;
                    statsText += new StringTextComponent("Win Rate: " + String.format("%.1f%%", winRate))
                            .withStyle(TextFormatting.AQUA).getString();
                } else {
                    statsText += new StringTextComponent("Win Rate: 0.0%")
                            .withStyle(TextFormatting.AQUA).getString();
                }

                // Add the page with plain text
                ITextComponent page = new StringTextComponent(statsText);
                pages.add(StringNBT.valueOf(ITextComponent.Serializer.toJson(page)));
                bookTag.put("pages", pages);

                Tournaments.LOGGER.info("Opening stats book for player: {}", player.getName().getString());

                // Store player's current item - MOVED OUTSIDE try-catch for proper scope
                ItemStack originalItem = player.getItemInHand(net.minecraft.util.Hand.MAIN_HAND);

                try {
                    // SIMPLER APPROACH: Give the player a copy of the book and delete after a delay

                    // Give player the book
                    player.setItemInHand(net.minecraft.util.Hand.MAIN_HAND, book.copy());

                    // Open it directly using Minecraft's built-in method
                    player.openItemGui(book, net.minecraft.util.Hand.MAIN_HAND);

                    // Log that we attempted to open the book
                    Tournaments.LOGGER.info("Stats book opened for player {}", player.getName().getString());

                    // Schedule a task to restore the original item with a longer delay (3 ticks)
                    player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(3, () -> {
                        // After a short delay, restore original item
                        player.setItemInHand(net.minecraft.util.Hand.MAIN_HAND, originalItem);

                        // For debugging
                        Tournaments.LOGGER.info("Restored original item");

                        // Open main GUI again
                        openMainGui(player);
                    }));
                } catch (Exception e) {
                    Tournaments.LOGGER.error("Error opening stats book: {}", e.getMessage(), e);
                    player.sendMessage(
                            new StringTextComponent("Error opening stats book: " + e.getMessage())
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());

                    // Restore original item immediately on error
                    player.setItemInHand(net.minecraft.util.Hand.MAIN_HAND, originalItem);
                }
                break;

            default:
                // Unknown action, just reopen main GUI
                player.sendMessage(
                        new StringTextComponent("Unknown action: " + action)
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                openMainGui(player);
                break;
        }
    }
}