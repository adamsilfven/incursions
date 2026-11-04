package dev.incursions;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import java.util.*;

/** Separate definitions are joined into immutable validated snapshots on each reload. */
public final class Definitions extends SimpleJsonResourceReloadListener {
    public record Creature(ResourceLocation entity, double weight, boolean flying) {}
    public record Origin(ResourceLocation id, String provider, ResourceLocation block, String mod,
                         Set<ResourceLocation> dimensions, int radius, String frame) {}
    public record Reward(ResourceLocation table, double weight, int rolls) {}
    public record Profile(ResourceLocation id, List<Origin> origins, List<Creature> pool,
                          double chance, boolean first, int minWaves, int maxWaves, int seconds, int gap,
                          double zero, double one, double eliteChance, int eliteDelay, double health, double damage,
                          Creature rare, double rareChance, double rareEliteChance, String title,
                          List<Reward> rewards, boolean perPlayer, String particle, String scaling, ResourceLocation openingSound, ResourceLocation victorySound, int spawnInterval, ResourceLocation eliteSound, String eliteColor, ResourceLocation zeroPairSound) {}
    public record Snapshot(Map<ResourceLocation, Origin> origins, Map<ResourceLocation, Profile> profiles,
                           List<String> diagnostics, long generation) {}
    public static volatile Snapshot current = new Snapshot(Map.of(), Map.of(), List.of("not loaded"), 0);
    public Definitions() { super(new Gson(), "incursions"); }
    static ResourceLocation id(String text) {
        if (!text.contains(":")) throw new IllegalArgumentException("ID must have namespace: " + text);
        return ResourceLocation.parse(text);
    }
    static String str(JsonObject j, String k) { return j.get(k).getAsString(); }
    static double num(JsonObject j, String k, double min, double max) {
        double d = j.get(k).getAsDouble();
        if (!Double.isFinite(d) || d < min || d > max) throw new IllegalArgumentException(k + " must be finite in ["+min+","+max+"]");
        return d;
    }
    static int interval(JsonObject j) {
        int interval=j.has("spawn_interval_ticks")?integer(j,"spawn_interval_ticks",20,1200):20;
        int duration=integer(j,"window_seconds",1,60)*20;
        if(interval>duration || duration%interval!=0) throw new IllegalArgumentException("spawn_interval_ticks must evenly divide window_seconds * 20");
        return interval;
    }
    static int integer(JsonObject j, String k, int min, int max) {
        double n = num(j, k, min, max);
        if (n != Math.rint(n)) throw new IllegalArgumentException(k + " must be integer");
        return (int)n;
    }
    static boolean flag(JsonObject j, String k) { return j.has(k) && j.get(k).getAsBoolean(); }
    static boolean dependency(JsonObject j) { return !j.has("required_mod") || ModList.get().isLoaded(str(j,"required_mod")); }
    static Creature creature(JsonObject e) {
        ResourceLocation entity = id(str(e, "entity"));
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)) throw new IllegalArgumentException("entity: unknown registry ID " + entity);
        return new Creature(entity, num(e, "weight", 0.000001, 1000000), flag(e, "flying"));
    }
    @Override protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Origin> origins = new LinkedHashMap<>();
        Map<ResourceLocation, List<Creature>> pools = new HashMap<>();
        Map<ResourceLocation, List<Reward>> rewards = new HashMap<>();
        Map<ResourceLocation, JsonObject> schedules = new HashMap<>();
        Map<ResourceLocation, Profile> profiles = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        // Sorted resource IDs make error reporting and seeded tests stable.
        var sorted = new TreeMap<>(input);
        for (var entry : sorted.entrySet()) {
            try {
                JsonObject j = entry.getValue().getAsJsonObject();
                if (!dependency(j)) { errors.add(entry.getKey() + ": disabled, missing required_mod=" + str(j,"required_mod")); continue; }
                switch (str(j,"kind")) {
                    case "origin" -> {
                        String provider = str(j,"provider");
                        if (!Set.of("vertical", "horizontal", "anchor").contains(provider)) throw new IllegalArgumentException("provider: expected vertical, horizontal, or anchor");
                        ResourceLocation block = id(str(j,"block"));
                        if (!BuiltInRegistries.BLOCK.containsKey(block)) throw new IllegalArgumentException("block: unknown " + block);
                        Set<ResourceLocation> dimensions = new HashSet<>();
                        for (var x : j.getAsJsonArray("dimensions")) dimensions.add(id(x.getAsString()));
                        if(j.has("frame")) {
                            String frame=str(j,"frame");ResourceLocation frameId=id(frame.startsWith("#")?frame.substring(1):frame);
                            if(!frame.startsWith("#")&&!BuiltInRegistries.BLOCK.containsKey(frameId)) throw new IllegalArgumentException("frame: unknown block "+frameId);
                        }
                        origins.put(entry.getKey(), new Origin(entry.getKey(), provider, block,
                            j.has("required_mod") ? str(j,"required_mod") : "", Set.copyOf(dimensions), integer(j,"emergence_radius",3,16),j.has("frame")?str(j,"frame"):""));
                    }
                    case "creature_pool" -> {
                        List<Creature> list = new ArrayList<>();
                        for (var x : j.getAsJsonArray("entries")) list.add(creature(x.getAsJsonObject()));
                        if (list.isEmpty() || list.size() > 128) throw new IllegalArgumentException("entries: require 1..128 creatures");
                        pools.put(entry.getKey(), List.copyOf(list));
                    }
                    case "reward_pool" -> {
                        List<Reward> list = new ArrayList<>();
                        for (var x : j.getAsJsonArray("entries")) {
                            var e = x.getAsJsonObject();
                            list.add(new Reward(id(str(e,"table")), num(e,"weight",.000001,1000000),integer(e,"rolls",1,64)));
                        }
                        if (list.size()>128) throw new IllegalArgumentException("entries: maximum 128");
                        rewards.put(entry.getKey(), List.copyOf(list));
                        if (list.isEmpty()) errors.add(entry.getKey()+": victory loot UNCONFIGURED (ordinary mob loot/XP unaffected)");
                    }
                    case "wave_schedule" -> {
                        int min=integer(j,"min_waves",1,16);
                        integer(j,"max_waves",min,16);integer(j,"window_seconds",1,60);integer(j,"interwave_ticks",0,12000);
                        double total=num(j,"zero_probability",0,1)+num(j,"one_probability",0,1)+num(j,"two_probability",0,1);
                        if(Math.abs(total-1)>1e-9) throw new IllegalArgumentException("spawn probabilities must sum to 1");
                        interval(j);
                        schedules.put(entry.getKey(),j.deepCopy());
                    }
                    case "profile" -> {} // Resolve after origins, pools, and rewards.
                    default -> throw new IllegalArgumentException("kind: unknown definition kind");
                }
            } catch (Exception e) { errors.add(entry.getKey() + ": " + e.getMessage() + "; correct this definition and /reload"); }
        }
        boolean hasFirst = false;
        for (var entry : sorted.entrySet()) try {
            JsonObject j = entry.getValue().getAsJsonObject();
            if (!"profile".equals(str(j,"kind")) || !dependency(j)) continue;
            JsonObject schedule=schedules.get(id(str(j,"wave_schedule")));
            if(schedule==null) throw new IllegalArgumentException("wave_schedule: unresolved reference");
            j=j.deepCopy();
            for(String field:List.of("min_waves","max_waves","window_seconds","interwave_ticks","zero_probability","one_probability","two_probability")) j.add(field,schedule.get(field));
            j.addProperty("spawn_interval_ticks",interval(schedule));
            if(!(BuiltInRegistries.PARTICLE_TYPE.get(id(str(j,"particle"))) instanceof net.minecraft.core.particles.SimpleParticleType)) throw new IllegalArgumentException("particle: expected registered simple particle ID (parameterized particles unsupported)");
            ResourceLocation openingSound=id(str(j,"opening_sound")), victorySound=id(str(j,"victory_sound"));
            if(!BuiltInRegistries.SOUND_EVENT.containsKey(openingSound) || !BuiltInRegistries.SOUND_EVENT.containsKey(victorySound)) throw new IllegalArgumentException("opening_sound/victory_sound: unknown registry ID");
            ResourceLocation eliteSound=j.has("elite_sound")?id(str(j,"elite_sound")):id("minecraft:entity.evoker.prepare_attack");
            if(!BuiltInRegistries.SOUND_EVENT.containsKey(eliteSound)) throw new IllegalArgumentException("elite_sound: unknown registry ID "+eliteSound);
            ResourceLocation zeroPairSound=j.has("zero_pair_sound")?id(str(j,"zero_pair_sound")):id("minecraft:block.end_portal.spawn");
            if(!BuiltInRegistries.SOUND_EVENT.containsKey(zeroPairSound)) throw new IllegalArgumentException("zero_pair_sound: unknown registry ID "+zeroPairSound);
            String eliteColor=j.has("elite_name_color")?str(j,"elite_name_color"):"gold";
            var formatting=net.minecraft.ChatFormatting.getByName(eliteColor);
            if(formatting==null || !formatting.isColor()) throw new IllegalArgumentException("elite_name_color: expected a named Minecraft text color");
            List<Origin> bindings = new ArrayList<>();
            for (var x : j.getAsJsonArray("origins")) {
                Origin o = origins.get(id(x.getAsString()));
                if (o == null) throw new IllegalArgumentException("origins: unresolved " + x);
                bindings.add(o);
            }
            List<Creature> pool = pools.get(id(str(j,"creature_pool")));
            List<Reward> reward = rewards.get(id(str(j,"reward_pool")));
            if (pool == null || reward == null || bindings.isEmpty()) throw new IllegalArgumentException("unresolved creature_pool/reward_pool or empty origins");
            boolean first = flag(j,"nether_first");
            if (first && hasFirst) throw new IllegalArgumentException("nether_first: only one profile may claim priority");
            int min = integer(j,"min_waves",1,16), max = integer(j,"max_waves",min,16);
            double zero = num(j,"zero_probability",0,1), one = num(j,"one_probability",0,1), two=num(j,"two_probability",0,1);
            if (Math.abs(zero+one+two-1)>1e-9) throw new IllegalArgumentException("spawn probabilities must sum to 1");
            Creature rare = j.has("rare") ? creature(j.getAsJsonObject("rare")) : null;
            String scaling = j.has("scaling") ? str(j,"scaling") : "GLOBAL";
            if (!Set.of("GLOBAL","FIXED","PLAYER_SCALED","DIFFICULTY_SCALED").contains(scaling)) throw new IllegalArgumentException("scaling: unsupported mode");
            profiles.put(entry.getKey(), new Profile(entry.getKey(), List.copyOf(bindings),pool,num(j,"trigger_chance",0,1),first,
                min,max,integer(j,"window_seconds",1,60),integer(j,"interwave_ticks",0,12000),zero,one,
                num(j,"elite_chance",0,1),integer(j,"elite_delay_ticks",0,12000),num(j,"elite_health_multiplier",1,10),num(j,"elite_damage_multiplier",1,10),
                rare,rare==null?0:num(j,"rare_chance",0,1),rare==null?0:num(j,"rare_elite_chance",0,1),str(j,"opening_title"),reward,flag(j,"per_player_rewards"),str(j,"particle"),scaling,openingSound,victorySound,interval(j),eliteSound,eliteColor,zeroPairSound));
            hasFirst |= first;
        } catch (Exception e) { errors.add(entry.getKey()+": "+e.getMessage()+"; profile disabled, correct and /reload"); }
        current = new Snapshot(Map.copyOf(origins),Map.copyOf(profiles),List.copyOf(errors),current.generation+1);
        errors.forEach(Incursions.LOG::warn);
        Incursions.LOG.info("Incursions definitions generation {}: {} profiles, {} origins", current.generation, profiles.size(), origins.size());
    }
}
