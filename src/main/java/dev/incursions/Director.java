package dev.incursions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import java.util.*;

final class Director {
    final MinecraftServer server;
    final Origins origins=new Origins();
    final Random rng=new Random();
    final java.util.function.Supplier<List<net.minecraft.server.level.ServerPlayer>> participants;
    long time, cooldown;
    Encounter active;
    String lastEnd="none";
    Director(MinecraftServer server) { this(server,()->server.getPlayerList().getPlayers()); }
    Director(MinecraftServer server,java.util.function.Supplier<List<net.minecraft.server.level.ServerPlayer>> participants) {this.server=server;this.participants=participants;}
    List<net.minecraft.server.level.ServerPlayer> players() {return participants.get();}
    boolean allowed() {
        return Settings.ENABLED.get() && server.getWorldData().getDifficulty()!=Difficulty.PEACEFUL
            && (!Settings.RESPECT_SPAWNING.get() || server.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING));
    }
    void tick() {
        time++;
        origins.tick(server,time);
        if(!allowed()) {
            if(active!=null) active.fail("server_rule");
            return; // No cooldown advance/reset from failed trigger rolls in Peaceful.
        }
        if(active!=null) { active.tick(); return; }
        if(time<cooldown || time%Settings.SCAN.get()!=0) return;
        Map<Definitions.Profile,List<Origins.Site>> eligible=new LinkedHashMap<>();
        var players=players();
        Definitions.current.profiles().values().stream().sorted(Comparator.comparing(p->p.id().toString())).forEach(p->{
            var sites=origins.eligible(p,players); if(!sites.isEmpty()) eligible.put(p,sites);
        });
        var choices=eligible.keySet().stream().map(p->new Rules.Lottery<>(p,p.chance(),p.first())).toList();
        var selected=Rules.choose(choices,rng);
        if(Settings.DEBUG.get()) Incursions.LOG.info("Incursion scan tick {}: eligible profiles {}, selected {}",time,eligible.size(),selected==null?"none":selected.id());
        if(selected!=null) {
            var sites=eligible.get(selected);
            active=new Encounter(this,selected,sites.get(rng.nextInt(sites.size())));
            active.open();
        }
    }
    String trigger(String dimension, boolean forceElite) {
        if(active!=null) return "An incursion is already active";
        if(!allowed()) return "Incursions are disabled by server settings or game rules";
        var profile=Definitions.current.profiles().values().stream()
            .filter(p->p.id().getPath().equals("profiles/"+dimension)).findFirst().orElse(null);
        if(profile==null) return "Dimension profile unavailable: "+dimension;
        var eligible=origins.eligible(profile,players());
        if(eligible.isEmpty()) return "No eligible active portal or anchor found for "+dimension+" (use /incursions origins)";
        var site=eligible.get(rng.nextInt(eligible.size()));
        active=new Encounter(this,profile,site);
        active.open(forceElite);
        Incursions.LOG.info("Operator manually triggered {} (forced elite: {})",profile.id(),forceElite);
        return "Manually started "+profile.id()+" at "+site.center().toShortString()+(forceElite?" with guaranteed elite":"");
    }
    String cancel() {
        if(active==null) return "No incursion is active";
        active.fail("operator_stop");
        return "Active incursion stopped (no victory rewards)";
    }
    void ended(Encounter encounter, String reason) {
        if(active!=encounter) return;
        lastEnd=encounter.profile.id()+": "+reason+"; wave "+(encounter.fight==null?0:encounter.fight.wave)+"/"+encounter.totalWaves+"; enhanced spawned "+encounter.enhancedSpawned;
        active=null;cooldown=time+encounter.cooldownTicks;
        Incursions.LOG.info("Incursion {} ended: {}; cooldown until logical tick {}",encounter.id,reason,cooldown);
    }
    List<String> originReport() {
        var lines=new ArrayList<String>();
        lines.add("Indexed loaded origins: "+origins.count());
        Definitions.current.profiles().values().stream().sorted(Comparator.comparing(p->p.id().toString())).forEach(p->{
            var eligible=origins.eligible(p,players());
            lines.add(p.id()+": "+eligible.size()+" eligible origins; chance "+(p.chance()*100)+"% per scan"+(p.first()?"; first priority":""));
            for(var site:eligible.stream().limit(8).toList()) lines.add("  "+site.definition().id()+" at "+site.level().dimension().location()+" "+site.center().toShortString());
        });
        return lines;
    }
    void stop() { if(active!=null) active.fail("shutdown");active=null;cooldown=0; }
    String status() { return active==null ? "Idle; cooldown remaining "+Math.max(0,cooldown-time)+" ticks; origins "+origins.count()+"; last result "+lastEnd
        : active.profile.id()+" at "+active.site.center()+"; wave "+active.fight.wave+"/"+active.totalWaves+"; enhanced spawned "+active.enhancedSpawned+"; required mobs "+active.tracked.size()+"; pending "+active.fight.pendingCount(); }
}
