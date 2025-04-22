package com.blissy.tournaments.debug;

import com.blissy.tournaments.Tournaments;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Diagnostic class to debug Pixelmon battle events
 * Add this class to your project to get detailed logging on battle events
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class BattleEventDiagnostics {

    @SubscribeEvent
    public static void onAnyBattleEnd(BattleEndEvent event) {
        try {
            // Log that we received the event
            Tournaments.LOGGER.info("=============== DIAGNOSTIC: BATTLE END EVENT DETECTED ===============");
            Tournaments.LOGGER.info("Battle controller: {}", event.getBattleController().battleIndex);
            Tournaments.LOGGER.info("Battle end cause: {}", event.getCause());
            Tournaments.LOGGER.info("Is abnormal: {}", event.isAbnormal());

            // Log the results map size
            Tournaments.LOGGER.info("Results map size: {}", event.getResults().size());

            // Log each battle participant and their result
            for (BattleParticipant participant : event.getResults().keySet()) {
                if (participant instanceof PlayerParticipant) {
                    PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                    Tournaments.LOGGER.info("Player: {}, Result: {}",
                            playerParticipant.player.getName().getString(),
                            event.getResults().get(participant));
                } else {
                    Tournaments.LOGGER.info("Non-player participant: {}, Result: {}",
                            participant.getClass().getSimpleName(),
                            event.getResults().get(participant));
                }
            }

            // Log players in battle
            Tournaments.LOGGER.info("Players in battle: {}", event.getPlayers().size());
            for (int i = 0; i < event.getPlayers().size(); i++) {
                Tournaments.LOGGER.info("Player {}: {}", i,
                        event.getPlayers().get(i).getName().getString());
            }

            Tournaments.LOGGER.info("===============================================================");
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error in diagnostic event handler", e);
        }
    }
}