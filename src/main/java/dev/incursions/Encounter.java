package dev.incursions;

import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import java.util.*;

final class Encounter implements Rules.World {
    final Director director;
    final Definitions.Profile profile;
    final Origins.Site site;
    final UUID id=UUID.randomUUID();
    final Map<UUID,Mob> tracked=new HashMap<>();
    final Map<UUID,Integer> unreachable=new HashMap<>();
    final Map<UUID,Boolean> zombieImmunity=new HashMap<>();
    final Map<Definitions.Reward,LootTable> tables=new LinkedHashMap<>();
    final Map<Definitions.Reward,LootContext> lootContexts=new HashMap<>();
    final int cooldownTicks;
    final Settings.Scaling scaling;
    final boolean blockInfection;
    int totalWaves, enhancedSpawned;
    record CueGroup(long due,boolean zeroPair) {}
    final Set<CueGroup> soundedEliteGroups=new HashSet<>();
    Rules.Fight fight;
    private final Map<Rules.Arrival,Definitions.Creature> reserved=new IdentityHashMap<>();
    record Body(float width,float height,boolean flying) {}
    private final Map<Body,List<Vec3>> spaceCache=new HashMap<>();
    private long cacheTick=-1;
    private List<Vec3> space(float width,float height,boolean flying) {
        if(cacheTick!=director.time){spaceCache.clear();cacheTick=director.time;}
        return spaceCache.computeIfAbsent(new Body(width,height,flying),k->SpawnSpace.reachable(site,width,height,flying,director.players()));
    }
    private long absentSince=-1;
    private boolean finished;
    Encounter(Director d, Definitions.Profile profile, Origins.Site site) {
        director=d;this.profile=profile;this.site=site;
        cooldownTicks=Settings.COOLDOWN.get();
        blockInfection=Settings.BLOCK_INFECTION.get();
        scaling=profile.scaling().equals("GLOBAL")?Settings.SCALING.get():Settings.Scaling.valueOf(profile.scaling());
        for(var reward:profile.rewards()) {
            var key=ResourceKey.create(Registries.LOOT_TABLE,reward.table());
            var lookup=d.server.reloadableRegistries().get();
            if(lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key).isEmpty()) {
                Incursions.LOG.error("{} reward table {} missing; victory loot disabled for this encounter",profile.id(),reward.table());tables.clear();break;
            }
            tables.put(reward,d.server.reloadableRegistries().getLootTable(key));
            var params=new LootParams.Builder(site.level()).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(site.center())).create(LootContextParamSets.CHEST);
            lootContexts.put(reward,new LootContext.Builder(params).withOptionalRandomSeed(d.rng.nextLong()).create(Optional.empty()));
        }
    }
    List<ServerPlayer> nearby() { return director.players().stream().filter(p->site.near(p,96)).toList(); }
    void open() {
        int waves=profile.minWaves()+director.rng.nextInt(profile.maxWaves()-profile.minWaves()+1);
        totalWaves=waves;
        boolean rare=profile.rare()!=null && director.rng.nextDouble()<profile.rareChance();
        boolean rareElite=rare && director.rng.nextDouble()<profile.rareEliteChance();
        boolean normal=director.rng.nextDouble()<profile.eliteChance();
        fight=new Rules.Fight(new Rules.Schedule(waves,profile.seconds(),profile.gap(),profile.eliteDelay(),profile.zero(),profile.one(),normal,rare,rareElite,profile.spawnInterval()),director.rng,director.time,this);
        announce(profile.title());effect(false);
        for(var player:nearby()) {
            boolean hasMenu=player.connection.getConnection()!=null
                && player.connection.getConnection().channel()!=null
                && player.connection.hasChannel(SettingsNetwork.Control.TYPE);
            var tip=hasMenu
                ? Component.translatableWithFallback("incursions.chat.settings","Toggle Incursions with the I button in the pause menu. On servers, an operator must change this setting.")
                : Component.translatableWithFallback("incursions.chat.config","The world owner or server operator can turn Incursions off in the server configuration.");
            player.sendSystemMessage(Component.literal(profile.title()).withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(Component.literal(" ").append(tip).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
        Incursions.LOG.info("Incursion {} opened: {} at {} {} ({} waves)",id,profile.id(),site.level().dimension().location(),site.center(),waves);
        Incursions.LOG.info("Incursion {} reservations: normal elite={}, rare={}, rare enhanced={}",id,normal,rare,rareElite);
    }
    void tick() {
        if(finished) return;
        if(!site.intact()) { fail("origin_destroyed_or_unloaded");return; }
        var players=nearby();
        if(players.isEmpty()) {
            if(absentSince<0) absentSince=director.time;
            if(director.time-absentSince>=600) {fail("abandoned");return;}
        } else absentSince=-1;
        if(director.time%20==0) {
            if(space(.6f,1.8f,false).isEmpty()) {fail("origin_obstructed");return;}
            maintain(players);
            if(finished) return;
        }
        fight.tick(director.time,this);
    }
    private void maintain(List<ServerPlayer> players) {
        for(var iterator=tracked.entrySet().iterator();iterator.hasNext();) {
            var e=iterator.next();Mob mob=e.getValue();
            if((!mob.isAlive() && mob.getHealth()<=0) || SentryGuard.resolved(mob)) { iterator.remove();unreachable.remove(e.getKey());continue; }
            if(mob.isRemoved() || !site.level().hasChunkAt(mob.blockPosition()) || site.level().getEntity(e.getKey())!=mob) {fail("required_enemy_removed_or_unloaded");return;}
            if(mob.level()!=site.level()) {fail("required_enemy_changed_dimension");return;}
            ServerPlayer target=players.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            if(target==null) continue;
            if(mob.getTarget()==null || !mob.getTarget().isAlive()) {
                if(mob.distanceToSqr(Vec3.atCenterOf(site.center()))>48*48 && loadedForPath(mob)) mob.getNavigation().moveTo(site.center().getX()+.5,site.center().getY()+1,site.center().getZ()+.5,1);
                else {
                    mob.setTarget(target);
                    if(mob instanceof AbstractPiglin piglin) anger(piglin,target);
                    if(mob instanceof ZombifiedPiglin piglin) {piglin.setPersistentAngerTarget(target.getUUID());piglin.setRemainingPersistentAngerTime(600);}
                }
            }
            // Path or sight must reach a participant. No untracking an undefeated enemy into a paid victory.
            boolean sees=mob.hasLineOfSight(target);
            var path=loadedForPath(mob)?mob.getNavigation().createPath(target,0):null;
            boolean recentCombat=mob.tickCount-mob.getLastHurtByMobTimestamp()<100 && mob.getLastHurtByMobTimestamp()>0;
            if(!sees && (path==null || !path.canReach()) && !recentCombat) {
                int seconds=unreachable.merge(e.getKey(),1,Integer::sum);
                if(seconds>=30) {fail("enemy_unreachable_for_30_seconds");return;}
            } else unreachable.remove(e.getKey());
        }
    }
    private static void anger(AbstractPiglin piglin, ServerPlayer target) {
        piglin.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT,target.getUUID(),600L);
    }
    private boolean loadedForPath(Mob mob) {
        double follow=mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        if(!Double.isFinite(follow) || follow>128) return false;
        int radius=(int)follow+16;
        for(int x=(mob.blockPosition().getX()-radius)>>4;x<=((mob.blockPosition().getX()+radius)>>4);x++)
            for(int z=(mob.blockPosition().getZ()-radius)>>4;z<=((mob.blockPosition().getZ()+radius)>>4);z++)
                if(site.level().getChunkSource().getChunkNow(x,z)==null) return false;
        return true;
    }
    @Override public double scaling() {
        return switch(scaling) {
            case FIXED -> 1;
            case PLAYER_SCALED -> Rules.multiplier(nearby().size());
            case DIFFICULTY_SCALED -> switch(site.level().getDifficulty()) {case EASY, PEACEFUL -> 1;case NORMAL -> 1.35;case HARD -> 1.7;};
        };
    }
    @Override public void waveStarted(int wave,int total) {
        Incursions.LOG.info("Incursion {} wave {}/{} started",id,wave,total);
    }
    @Override public int requiredAlive() { return tracked.size(); }
    private Definitions.Creature choose(Rules.Kind kind) {
        return kind==Rules.Kind.RARE || kind==Rules.Kind.RARE_ENHANCED?profile.rare():Rules.weighted(profile.pool(),Definitions.Creature::weight,director.rng);
    }
    @Override public boolean spawn(Rules.Arrival arrival) {
        if(arrival.kind()!=Rules.Kind.ORDINARY && (director.time-arrival.due())%20!=0) return false;
        var entry=reserved.computeIfAbsent(arrival,a->choose(a.kind()));
        boolean result=spawnCreature(arrival.kind(),entry);
        if(result && enhanced(arrival.kind())) eliteCue(arrival.due(),arrival.zeroPair());
        if(result || arrival.kind()==Rules.Kind.ORDINARY) reserved.remove(arrival);
        return result;
    }
    static boolean enhanced(Rules.Kind kind) {return kind==Rules.Kind.ENHANCED || kind==Rules.Kind.RARE_ENHANCED;}
    private void eliteCue(long group,boolean zeroPair) {
        if(soundedEliteGroups.add(new CueGroup(group,zeroPair))) site.level().playSound(null,site.center(),BuiltInRegistries.SOUND_EVENT.get(zeroPair?profile.zeroPairSound():profile.eliteSound()),SoundSource.HOSTILE,.6f,.8f);
    }
    @Override public boolean spawn(Rules.Kind kind) {
        boolean result=spawnCreature(kind,choose(kind));
        if(result && enhanced(kind)) eliteCue(director.time,false);
        return result;
    }
    private boolean spawnCreature(Rules.Kind kind, Definitions.Creature selected) {
        Definitions.Creature entry=selected;
        if(entry==null) return false;
        Entity raw=BuiltInRegistries.ENTITY_TYPE.get(entry.entity()).create(site.level());
        if(!(raw instanceof Mob mob)) { if(raw!=null) raw.discard();return false; }
        mob.getPersistentData().putBoolean(Protection.TAG,true);
        AdultSpawns.normalize(mob);
        // finalizeSpawn may change dimensions/equipment; check the resulting body, then spawn only after a second collision test.
        var positions=space(mob.getBbWidth(),mob.getBbHeight(),entry.flying());
        if(positions.isEmpty()) {mob.discard();return false;}
        var pos=positions.get(director.rng.nextInt(positions.size()));
        mob.moveTo(pos.x,pos.y,pos.z,director.rng.nextFloat()*360,0);
        mob.finalizeSpawn(site.level(),site.level().getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.EVENT,null);
        AdultSpawns.normalize(mob);
        if(mob.isBaby()) {mob.discard();Incursions.LOG.warn("{}: cannot normalize baby entity {}; spawn rejected",id,entry.entity());return false;}
        // Incursions never spawns riders, even if an entity's normal spawn preparation creates one.
        mob.getPassengers().forEach(Entity::discard);mob.ejectPassengers();mob.stopRiding();
        boolean placed=false;int offset=director.rng.nextInt(positions.size());
        for(int attempt=0;attempt<Math.min(16,positions.size());attempt++) {
            Vec3 candidate=positions.get((offset+attempt)%positions.size());mob.moveTo(candidate.x,candidate.y,candidate.z,mob.getYRot(),0);
            if(SpawnSpace.clear(site,mob.blockPosition(),mob.getBbWidth(),mob.getBbHeight(),entry.flying()) && site.level().noCollision(mob,mob.getBoundingBox())) {placed=true;break;}
        }
        if(!placed){mob.discard();return false;}
        mob.getPersistentData().putBoolean(Protection.TAG,true);
        mob.getPersistentData().putBoolean(InfectionGuard.TAG,blockInfection);
        mob.getPersistentData().putUUID("incursions_session",id);
        if(mob instanceof AbstractPiglin piglin) {zombieImmunity.put(mob.getUUID(),piglin.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getBoolean("IsImmuneToZombification"));piglin.setImmuneToZombification(true);}
        if(mob instanceof Hoglin hoglin) {zombieImmunity.put(mob.getUUID(),hoglin.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getBoolean("IsImmuneToZombification"));hoglin.setImmuneToZombification(true);}
        mob.getPersistentData().putBoolean("incursions_previous_immunity",zombieImmunity.getOrDefault(mob.getUUID(),false));
        if(kind==Rules.Kind.ENHANCED || kind==Rules.Kind.RARE_ENHANCED) {
            var hp=mob.getAttribute(Attributes.MAX_HEALTH);if(hp!=null) hp.setBaseValue(hp.getBaseValue()*profile.health());
            mob.getPersistentData().putDouble("incursions_damage_multiplier",profile.damage());
            mob.setHealth(mob.getMaxHealth());
            mob.setCustomName(Component.translatable("entity.incursions.elite",mob.getType().getDescription()).withStyle(net.minecraft.ChatFormatting.getByName(profile.eliteColor())));
            mob.setCustomNameVisible(true);
        }
        mob.setCanPickUpLoot(false);
        // No equipment is injected; natural equipment retains ordinary drop chances.
        var target=nearby().stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
        if(target!=null) {
            mob.setTarget(target);
            if(mob instanceof AbstractPiglin piglin) anger(piglin,target);
            if(mob instanceof ZombifiedPiglin piglin) {piglin.setPersistentAngerTarget(target.getUUID());piglin.setRemainingPersistentAngerTime(600);}
        }
        if(!site.level().addFreshEntity(mob)) {mob.discard();return false;}
        tracked.put(mob.getUUID(),mob);
        if(kind==Rules.Kind.ENHANCED || kind==Rules.Kind.RARE_ENHANCED) {
            enhancedSpawned++;
            Incursions.LOG.info("Incursion {} enhanced arrival: {} UUID {}, health {}, damage multiplier {}",id,entry.entity(),mob.getUUID(),mob.getMaxHealth(),profile.damage());
        }
        return true;
    }
    void fail(String reason) { if(fight!=null) fight.fail(this,reason); else failed(reason); }
    @Override public void failed(String reason) { end(false,reason); }
    @Override public void completed() { end(true,"victory"); }
    private void end(boolean success,String reason) {
        if(finished) return;finished=true; // acquire payout once before executing any loot callbacks
        if(success) {
            announce("Incursion Repelled!");effect(true);
            payout();
        }
        for(Mob mob:tracked.values()) {
            mob.getPersistentData().remove("incursions_session");
            // Safety provenance remains on survivors/projectiles. Their AI/despawn reverts to vanilla.
            if(mob instanceof AbstractPiglin p) p.setImmuneToZombification(zombieImmunity.getOrDefault(mob.getUUID(),false));
            if(mob instanceof Hoglin h) h.setImmuneToZombification(zombieImmunity.getOrDefault(mob.getUUID(),false));
        }
        tracked.clear();unreachable.clear();reserved.clear();
        director.ended(this,reason);
    }
    private void payout() {
        if(tables.isEmpty()) return;
        try {
            var rewards=new ArrayList<>(tables.keySet());
            int payouts=profile.perPlayer()?nearby().size():1;
            var safe=space(.25f,.25f,false);
            if(safe.isEmpty()) {Incursions.LOG.warn("{}: no safe reward ejection position; no payout",id);return;}
            Vec3 pos=safe.getFirst().add(0,.25,0);
            for(int p=0;p<payouts;p++) {
                var chosen=Rules.weighted(rewards,Definitions.Reward::weight,director.rng);
                for(int r=0;r<chosen.rolls();r++) tables.get(chosen).getRandomItems(lootContexts.get(chosen),item -> {
                    var drop=new ItemEntity(site.level(),pos.x,pos.y,pos.z,item);drop.setDefaultPickUpDelay();site.level().addFreshEntity(drop);
                });
            }
        } catch(Exception e) {Incursions.LOG.error("Incursion {} reward evaluation failed; payout will not be retried",id,e);}
    }
    private void announce(String text) {
        for(var p:nearby()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5,45,10));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text)));
        }
    }
    private void effect(boolean victory) {
        SimpleParticleType particle=(SimpleParticleType)BuiltInRegistries.PARTICLE_TYPE.get(net.minecraft.resources.ResourceLocation.parse(profile.particle()));
        Vec3 pos=Vec3.atCenterOf(site.center()).add(0,1,0);
        for(var p:nearby()) site.level().sendParticles(p,particle,false,pos.x,pos.y,pos.z,24,1,.75,1,.015);
        site.level().playSound(null,site.center(),BuiltInRegistries.SOUND_EVENT.get(victory?profile.victorySound():profile.openingSound()),SoundSource.HOSTILE,.6f,victory?1:1.3f);
    }
}
