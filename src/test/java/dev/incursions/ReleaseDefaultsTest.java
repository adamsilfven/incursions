package dev.incursions;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReleaseDefaultsTest {
 JsonObject read(String kind,String id)throws Exception {try(var stream=getClass().getResourceAsStream("/data/incursions/incursions/"+kind+"/"+id+".json")){assertNotNull(stream);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}}
 @Test void defaultsMatchApprovedPacingAndEliteRevision()throws Exception {
  for(String id:List.of("nether","undergarden","aether","twilight")){
   var schedule=read("schedules",id);var p=read("profiles",id);int duration=schedule.get("window_seconds").getAsInt()*20;
   assertEquals(40,Definitions.interval(schedule));assertEquals(id.equals("nether")||id.equals("undergarden")?4:7,duration/40);
   assertEquals(600,p.get("elite_delay_ticks").getAsInt());assertEquals(1.75,p.get("elite_health_multiplier").getAsDouble());assertEquals(1.3,p.get("elite_damage_multiplier").getAsDouble());assertEquals(.15,p.get("elite_chance").getAsDouble());assertEquals("gold",p.get("elite_name_color").getAsString());assertNotEquals(p.get("elite_sound").getAsString(),p.get("zero_pair_sound").getAsString());
  }
 }
 @Test void rebalancedPoolsKeepAllApprovedSpeciesAndTotalOneHundred()throws Exception {
  Map<String,Map<String,Double>> targets=Map.of("nether",Map.of("piglin",50d,"wither_skeleton",10d,"zombified_piglin",15d,"piglin_brute",10d,"blaze",10d,"hoglin",5d),"undergarden",Map.of("rotling",25d,"rotwalker",20d,"nargoyle",20d,"sploogie",20d,"forgotten",15d),"aether",Map.of("cockatrice",20d,"mimic",30d,"sentry",45d,"blue_swet",2.5d,"golden_swet",2.5d),"twilight",Map.of("helmet_crab",35d,"slime_beetle",25d,"minotaur",20d,"blockchain_goblin",10d,"skeleton_druid",10d));
  for(var target:targets.entrySet()){
   Map<String,Double> actual=new TreeMap<>();for(var e:read("pools",target.getKey()).getAsJsonArray("entries")){var j=e.getAsJsonObject();actual.put(j.get("entity").getAsString().split(":")[1],j.get("weight").getAsDouble());}
   assertEquals(target.getValue(),actual);assertEquals(100d,actual.values().stream().mapToDouble(Double::doubleValue).sum());
  }
 }
 @Test void portalChancesAndSharedWeightedLootMatchApproval()throws Exception {
  for(String id:List.of("nether","undergarden","aether","twilight")) {
   var profile=read("profiles",id);
   assertEquals(id.equals("nether")?.02:.01,profile.get("trigger_chance").getAsDouble());
   assertEquals(id.equals("nether"),profile.get("nether_first").getAsBoolean());
   assertFalse(profile.get("per_player_rewards").getAsBoolean());
   var entries=read("rewards",id).getAsJsonArray("entries");
   var weights=Map.of("nether",List.of(20,30,25,25),"aether",List.of(15,30,35,20),"undergarden",List.of(25,50,25),"twilight",List.of(35,25,25,15)).get(id);
   assertEquals(weights.size(),entries.size());
   assertEquals(100,weights.stream().mapToInt(Integer::intValue).sum());
   int index=0;
   Set<String> unique=new HashSet<>();
   for(var entry:entries) {var e=entry.getAsJsonObject();assertEquals(weights.get(index++).intValue(),e.get("weight").getAsInt());assertEquals(1,e.get("rolls").getAsInt());assertTrue(unique.add(e.get("table").getAsString()));}
  }
 }
 @Test void curatedTablesHaveUnconditionalNonEmptyPrimaryPools()throws Exception {
  for(String id:List.of("aether_bronze","aether_silver","undergarden_mine","undergarden_catacombs","undergarden_mound","twilight_room","twilight_library","twilight_potion","twilight_cache","nether_fortress","nether_bastion_other","nether_hoglin_stable")) {
   try(var stream=getClass().getResourceAsStream("/data/incursions/loot_table/rewards/"+id+".json")) {
    assertNotNull(stream);var table=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
    var primary=table.getAsJsonArray("pools").get(0).getAsJsonObject();assertEquals(1,primary.get("rolls").getAsInt());assertFalse(primary.has("conditions"));
    assertFalse(primary.getAsJsonArray("entries").isEmpty());
    for(var element:primary.getAsJsonArray("entries")) {var entry=element.getAsJsonObject();assertEquals("minecraft:item",entry.get("type").getAsString());assertFalse(entry.has("conditions"));assertTrue(!entry.has("weight") || entry.get("weight").getAsInt()>0);}
    if(!id.startsWith("nether_")) assertEquals("neoforge:mod_loaded",table.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("type").getAsString());
   }
  }
 }
}
