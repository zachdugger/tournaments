package com.blissy.tournaments.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

public class TournamentsConfig {
    public static class Common {
        public final ForgeConfigSpec.IntValue maxParticipants;
        public final ForgeConfigSpec.BooleanValue enableRewards;
        public final ForgeConfigSpec.IntValue battleTimeoutSeconds;
        public final ForgeConfigSpec.BooleanValue enableTeleports;

        // Entry point teleport coordinates
        public final ForgeConfigSpec.DoubleValue entryX;
        public final ForgeConfigSpec.DoubleValue entryY;
        public final ForgeConfigSpec.DoubleValue entryZ;
        public final ForgeConfigSpec.ConfigValue<String> entryDimension;

        // Exit point teleport coordinates
        public final ForgeConfigSpec.DoubleValue exitX;
        public final ForgeConfigSpec.DoubleValue exitY;
        public final ForgeConfigSpec.DoubleValue exitZ;
        public final ForgeConfigSpec.ConfigValue<String> exitDimension;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Tournament Settings")
                    .push("tournaments");

            maxParticipants = builder
                    .comment("Maximum number of participants allowed in a tournament")
                    .defineInRange("maxParticipants", 32, 2, 128);

            enableRewards = builder
                    .comment("Whether tournament winners receive rewards")
                    .define("enableRewards", true);

            battleTimeoutSeconds = builder
                    .comment("Time in seconds before a battle is considered timed out")
                    .defineInRange("battleTimeoutSeconds", 300, 60, 3600);

            enableTeleports = builder
                    .comment("Whether tournament teleportation is enabled")
                    .define("enableTeleports", true);

            builder.comment("Teleport Settings")
                    .push("teleports");

            entryX = builder
                    .comment("X coordinate for the tournament entry point")
                    .defineInRange("entryX", 0.0, -30000000.0, 30000000.0);

            entryY = builder
                    .comment("Y coordinate for the tournament entry point")
                    .defineInRange("entryY", 64.0, 0.0, 256.0);

            entryZ = builder
                    .comment("Z coordinate for the tournament entry point")
                    .defineInRange("entryZ", 0.0, -30000000.0, 30000000.0);

            entryDimension = builder
                    .comment("Dimension for the tournament entry point (e.g., minecraft:overworld)")
                    .define("entryDimension", "minecraft:overworld");

            exitX = builder
                    .comment("X coordinate for the tournament exit point")
                    .defineInRange("exitX", 0.0, -30000000.0, 30000000.0);

            exitY = builder
                    .comment("Y coordinate for the tournament exit point")
                    .defineInRange("exitY", 64.0, 0.0, 256.0);

            exitZ = builder
                    .comment("Z coordinate for the tournament exit point")
                    .defineInRange("exitZ", 0.0, -30000000.0, 30000000.0);

            exitDimension = builder
                    .comment("Dimension for the tournament exit point (e.g., minecraft:overworld)")
                    .define("exitDimension", "minecraft:overworld");

            builder.pop(); // Pop teleports

            builder.pop(); // Pop tournaments
        }
    }

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder()
                .configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }
}