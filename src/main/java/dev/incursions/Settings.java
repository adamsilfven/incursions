package dev.incursions;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Settings {
    static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue ENABLED, RESPECT_SPAWNING, DEBUG, BLOCK_INFECTION;
    static final ModConfigSpec.IntValue SCAN, COOLDOWN, SECTIONS;
    static final ModConfigSpec.EnumValue<Scaling> SCALING;
    enum Scaling { FIXED, PLAYER_SCALED, DIFFICULTY_SCALED }
    static {
        var b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Enable triggers; disabling ends an active encounter without rewards.").define("enabled", true);
        SCAN = b.defineInRange("scanIntervalTicks", 100, 20, 12000);
        COOLDOWN = b.comment("Shared cooldown starts at success/failure; not persisted on restart.").defineInRange("sharedCooldownTicks", 21600, 0, 1728000);
        RESPECT_SPAWNING = b.define("respectDoMobSpawning", true);
        SECTIONS = b.comment("Maximum 16x16x16 chunk sections examined per tick. Never loads chunks.").defineInRange("sectionsPerTick", 4, 1, 16);
        SCALING = b.defineEnum("scaling", Scaling.PLAYER_SCALED);
        BLOCK_INFECTION = b.comment("Prevent Utheric Infection buildup from newly spawned incursion enemies only. Does not cure existing infection or change ordinary Undergarden mobs/biomes.").define("preventIncursionUthericInfection", true);
        DEBUG = b.define("debugLogging", false);
        SPEC = b.build();
    }
}
