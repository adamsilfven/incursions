package dev.incursions;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.event.EventHooks;
import java.util.*;

@GameTestHolder("incursions")
@PrefixGameTestTemplate(false)
public class WorldTests {
    @SuppressWarnings("removal")
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true,batch="zzz_settings")
    public static void settingsAuthorityPersistenceAndCodec(GameTestHelper h) throws Exception {
        var ordinary=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SettingsGuest")) {
            @Override public boolean hasPermissions(int level) {return false;}
        };
        var operator=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SettingsAdmin")) {
            @Override public boolean hasPermissions(int level) {return true;}
        };
        boolean original=Settings.ENABLED.get();
        try {
            var denied=SettingsNetwork.apply(ordinary,true,!original);
            h.assertTrue(!denied.editable() && denied.enabled()==original && Settings.ENABLED.get()==original,"Unauthorized request cannot mutate server setting");
            var saved=SettingsNetwork.apply(operator,true,false);
            h.assertTrue(saved.editable() && !saved.failed() && !saved.enabled(),"Operator can disable incursions");
            var file=java.nio.file.Path.of("config/incursions-server.toml");
            h.assertTrue(java.nio.file.Files.readString(file).contains("enabled = false"),"Disable is persisted to actual SERVER config file");
            var d=new Director(h.getLevel().getServer());
            h.assertTrue(!d.allowed(),"Disabled setting suppresses director triggers");
            var read=SettingsNetwork.apply(ordinary,false,true);
            h.assertTrue(!read.enabled()&&!read.editable(),"Read-only request returns actual server state");
            var packet=new SettingsNetwork.Control(12345,true,false,true,false);
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                SettingsNetwork.Control.CODEC.encode(buffer,packet);
                h.assertTrue(packet.equals(SettingsNetwork.Control.CODEC.decode(buffer)),"Actual registry-buffer codec round trip preserves request and fields");
            } finally {buffer.release();}
            var restored=SettingsNetwork.apply(operator,true,true);
            h.assertTrue(restored.enabled()&&!restored.failed(),"Re-enabling succeeds");
            h.assertTrue(java.nio.file.Files.readString(file).contains("enabled = true"),"Enable is persisted to disk");
        } finally {
            Settings.ENABLED.set(original);Settings.SPEC.save();ordinary.discard();operator.discard();
        }
        h.succeed();
    }
    static Definitions.Origin anchorDefinition() {return Definitions.current.origins().get(ResourceLocation.parse("incursions:origins/lodestone_anchor"));}
    static void floor(GameTestHelper h) {for(int x=0;x<20;x++)for(int z=0;z<20;z++)h.setBlock(x,0,z,Blocks.STONE);}
    static BlockPos anchor(GameTestHelper h) {
        floor(h);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)h.setBlock(9+dx,1,9+dz,dx==0&&dz==0?Blocks.LODESTONE:Blocks.STONE_BRICK_STAIRS);
        return h.absolutePos(new BlockPos(9,1,9));
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void anchorAndCollision(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        h.assertTrue(site!=null&&site.intact(),"Complete matching anchor must be recognized");
        var safe=SpawnSpace.reachable(site,.6f,1.8f,false,List.of());
        h.assertTrue(!safe.isEmpty(),"Outdoor anchor has safe emergence");
        for(var p:safe) {
            h.assertTrue(SpawnSpace.clear(site,BlockPos.containing(p),.6f,1.8f,false),"Every candidate must be collision safe");
            h.assertTrue(Math.abs(p.x-center.getX()-.5)>1||Math.abs(p.z-center.getZ()-.5)>1,"No candidate is on anchor footprint");
        }
        h.setBlock(8,1,8,Blocks.OAK_STAIRS);
        h.assertTrue(!site.intact(),"Changing required stair fails immediately");
        h.assertTrue(Origins.recognize(h.getLevel(),anchorDefinition(),center)==null,"Mixed stair types rejected");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void enclosedAnchor(GameTestHelper h) {
        var center=anchor(h);
        for(int x=6;x<=12;x++)for(int z=6;z<=12;z++)for(int y=1;y<=5;y++)
            if(x==6||x==12||z==6||z==12||y==5)h.setBlock(x,y,z,Blocks.STONE);
        var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        h.assertTrue(site!=null,"Enclosed structure is still a complete anchor");
        h.assertTrue(SpawnSpace.reachable(site,.6f,1.8f,false,List.of()).isEmpty(),"Sealed anchor cannot emerge through walls or roof");
        h.setBlock(9,1,6,Blocks.AIR);h.setBlock(9,2,6,Blocks.AIR);h.setBlock(9,3,6,Blocks.AIR);
        h.assertTrue(!SpawnSpace.reachable(site,.6f,1.8f,false,List.of()).isEmpty(),"Opening connected doorway enables emergence");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void netherLifecycle(GameTestHelper h) {
        floor(h);
        for(int x=7;x<=10;x++)for(int y=1;y<=5;y++)if(x==7||x==10||y==1||y==5)h.setBlock(x,y,9,Blocks.OBSIDIAN);
        for(int x=8;x<=9;x++)for(int y=2;y<=4;y++)h.setBlock(x,y,9,Blocks.NETHER_PORTAL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS,Direction.Axis.X));
        var o=Definitions.current.origins().get(ResourceLocation.parse("incursions:origins/nether"));
        var a=Origins.recognize(h.getLevel(),o,h.absolutePos(new BlockPos(8,2,9)));
        var b=Origins.recognize(h.getLevel(),o,h.absolutePos(new BlockPos(9,4,9)));
        h.assertTrue(a!=null&&a.intact()&&b!=null,"Active framed Nether portal recognized");
        h.assertTrue(a.key().equals(b.key())&&a.required().size()==6,"Interior canonicalizes to one origin");
        h.setBlock(7,3,9,Blocks.AIR);
        h.assertTrue(!a.intact(),"Breaking frame invalidates origin immediately");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void vanillaMobSpawnAndElite(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        var d=new Director(h.getLevel().getServer());d.time=100;
        var p=Definitions.current.profiles().get(ResourceLocation.parse("incursions:profiles/nether"));
        var encounter=new Encounter(d,p,site);d.active=encounter;
        h.assertTrue(encounter.spawn(Rules.Kind.ENHANCED),"Enhanced Nether enemy spawns in actual server world");
        Mob mob=encounter.tracked.values().iterator().next();
        h.assertTrue(h.getLevel().noCollision(mob,mob.getBoundingBox()),"Spawned actual mob has clear collision space");
        h.assertTrue(mob.getPersistentData().getBoolean(Protection.TAG),"Spawn provenance attached");
        h.assertTrue(Math.abs(mob.getPersistentData().getDouble("incursions_damage_multiplier")-1.3)<1e-6,"Elite attack multiplier retained");
        var baseline=(Mob)mob.getType().create(h.getLevel());
        h.assertTrue(Math.abs(mob.getMaxHealth()-baseline.getMaxHealth()*1.75)<.001,"Actual elite has 75% extra maximum health");
        var victim=EntityType.COW.create(h.getLevel());
        float before=victim.getHealth();victim.hurt(victim.damageSources().mobAttack(mob),2);
        h.assertTrue(Math.abs(before-victim.getHealth()-2.6)<.001,"Actual incoming attack gets 30% elite damage boost");
        baseline.discard();victim.discard();
        h.assertTrue(mob.hasCustomName() && mob.isCustomNameVisible(),"Elite name is explicitly visible");
        h.assertTrue(mob.getCustomName().getStyle().getColor().getValue()==net.minecraft.ChatFormatting.GOLD.getColor(),"Elite name is gold");
        h.assertTrue(encounter.soundedEliteGroups.size()==1,"Successful enhanced arrival emits one cue group");
        h.assertTrue(!EventHooks.canEntityGrief(h.getLevel(),mob),"Incursion mob cannot grief");
        var ordinary=EntityType.ZOMBIE.create(h.getLevel());
        h.assertTrue(EventHooks.canEntityGrief(h.getLevel(),ordinary)==h.getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING),"Ordinary zombie griefing unaffected");
        encounter.failed("test_cleanup");
        h.assertTrue(d.active==null&&d.cooldown==21700,"Failure releases only slot and starts 21600-tick cooldown");
        h.assertTrue(mob.isAlive(),"Failure keeps surviving mob in world");
        h.assertTrue(!mob.getPersistentData().contains("incursions_session"),"Failure removes session state");
        h.assertTrue(new Director(h.getLevel().getServer()).cooldown==0,"New server scheduler resets cooldown");
        mob.discard();ordinary.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void optionalRegistryAndPortalRecognition(GameTestHelper h) {
        floor(h);
        for(String name:List.of("aether","undergarden","twilight")) {
            var definition=Definitions.current.origins().get(ResourceLocation.parse("incursions:origins/"+name));
            if(definition==null) continue;
            var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(definition.block());
            if(name.equals("twilight")) {
                for(int x=7;x<=10;x++)for(int z=7;z<=10;z++)h.setBlock(x,1,z,Blocks.GRASS_BLOCK);
                for(int x=8;x<=9;x++)for(int z=8;z<=9;z++)h.setBlock(x,1,z,block);
            } else {
                Block frame=name.equals("aether")?Blocks.GLOWSTONE:Blocks.STONE_BRICKS;
                for(int x=7;x<=10;x++)for(int y=1;y<=5;y++)if(x==7||x==10||y==1||y==5)h.setBlock(x,y,9,frame);
                for(int x=8;x<=9;x++)for(int y=2;y<=4;y++)h.setBlock(x,y,9,block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS,Direction.Axis.X));
            }
            var site=Origins.recognize(h.getLevel(),definition,h.absolutePos(new BlockPos(8,name.equals("twilight")?1:2,9)));
            h.assertTrue(site!=null&&site.intact(),"Actual optional portal must validate: "+name);
            h.assertTrue(!SpawnSpace.reachable(site,.6f,1.8f,false,List.of()).isEmpty(),"Safe exterior for optional portal: "+name);
            for(var position:site.required().keySet()) h.getLevel().setBlock(position,Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(!site.intact(),"Broken optional portal must invalidate: "+name);
        }
        h.succeed();
    }
    static Definitions.Profile custom(Definitions.Profile p,List<Definitions.Creature> pool,List<Definitions.Reward> rewards) {
        return new Definitions.Profile(p.id(),p.origins(),pool,p.chance(),p.first(),p.minWaves(),p.maxWaves(),p.seconds(),p.gap(),p.zero(),p.one(),p.eliteChance(),p.eliteDelay(),p.health(),p.damage(),p.rare(),p.rareChance(),p.rareEliteChance(),p.title(),rewards,p.perPlayer(),p.particle(),p.scaling(),p.openingSound(),p.victorySound(),p.spawnInterval(),p.eliteSound(),p.eliteColor(),p.zeroPairSound());
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void everyAvailableCreatureCanEmerge(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        for(var p:Definitions.current.profiles().values()) {
            var entries=new ArrayList<>(p.pool());if(p.rare()!=null)entries.add(p.rare());
            for(var entry:entries) {
                var director=new Director(h.getLevel().getServer());director.time=100;
                var encounter=new Encounter(director,custom(p,List.of(entry),List.of()),site);director.active=encounter;
                h.assertTrue(encounter.spawn(Rules.Kind.ENHANCED),"Actual creature must emerge safely: "+entry.entity());
                var mob=encounter.tracked.values().iterator().next();
                h.assertTrue(mob.getType()==net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(entry.entity()),"Creature identity preserved");
                h.assertTrue(h.getLevel().noCollision(mob,mob.getBoundingBox()),"Actual creature collision safe: "+entry.entity());
                h.assertTrue(!mob.isPassenger()&&!mob.isVehicle(),"No riders");
                h.assertTrue(!mob.isBaby(),"Every configured species is adult: "+entry.entity());
                h.assertTrue(mob.hasCustomName() && mob.isCustomNameVisible(),"All enhanced species have visible names: "+entry.entity());
                encounter.failed("test_cleanup");mob.discard();
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void victoryPhysicalPayoutOnce(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        var d=new Director(h.getLevel().getServer());d.time=100;
        var p=Definitions.current.profiles().get(ResourceLocation.parse("incursions:profiles/nether"));
        var e=new Encounter(d,custom(p,p.pool(),List.of(new Definitions.Reward(ResourceLocation.parse("incursions:test_only"),1,1))),site);d.active=e;
        e.completed();e.completed();
        var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(center).inflate(12));
        h.assertTrue(drops.stream().mapToInt(drop->drop.getItem().getCount()).sum()==1,"One physical test token after duplicate completion callback");
        h.assertTrue(d.active==null,"Victory releases active slot");drops.forEach(Entity::discard);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void abandonmentAndOriginFailure(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        var d=new Director(h.getLevel().getServer());d.time=100;
        var p=Definitions.current.profiles().get(ResourceLocation.parse("incursions:profiles/nether"));
        var e=new Encounter(d,p,site);d.active=e;e.open();e.tick();
        d.time=699;e.tick();h.assertTrue(d.active==e,"29.95 seconds absence does not fail");
        d.time=700;e.tick();h.assertTrue(d.active==null,"30 seconds absence fails");
        var broken=new Encounter(d,p,site);d.active=broken;broken.open();
        h.setBlock(8,1,8,Blocks.AIR);broken.tick();h.assertTrue(d.active==null,"Required anchor block break fails active encounter immediately");
        h.killAllEntities();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true,batch="zz_validation")
    public static void malformedIsolationAndSnapshot(GameTestHelper h) {
        var before=Definitions.current;
        try {
            var map=new java.util.TreeMap<ResourceLocation,com.google.gson.JsonElement>();
            var resources=h.getLevel().getServer().getResourceManager();
            for(var entry:resources.listResources("incursions",location->location.getPath().endsWith(".json")).entrySet()) {
                String path=entry.getKey().getPath();path=path.substring("incursions/".length(),path.length()-5);
                try(var reader=entry.getValue().openAsReader()) {map.put(ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(),path),com.google.gson.JsonParser.parseReader(reader));}
            }
            var id=ResourceLocation.parse("incursions:profiles/nether");
            var original=before.profiles().get(id);
            map.get(id).getAsJsonObject().addProperty("trigger_chance",0);
            map.put(ResourceLocation.parse("broken:pools/invalid"),com.google.gson.JsonParser.parseString("{\"kind\":\"creature_pool\",\"entries\":[{\"entity\":\"broken:missing_creature\",\"weight\":1}]}"));
            new Definitions().apply(map,resources,h.getLevel().getProfiler());
            h.assertTrue(Definitions.current.profiles().containsKey(id),"Unrelated invalid creature definition leaves Nether enabled");
            h.assertTrue(Definitions.current.diagnostics().stream().anyMatch(x->x.contains("broken:pools/invalid")),"Invalid resource ID included in diagnostic");
            h.assertTrue(Definitions.current.profiles().get(id).chance()==0&&original.chance()==.02,"Reload publishes new snapshot without mutating old profile");
            h.assertTrue(Definitions.current.generation()==before.generation()+1,"Atomic generation advances");
        } catch(Exception e) {throw new RuntimeException(e);}
        finally {Definitions.current=before;}
        h.succeed();
    }
    @SuppressWarnings("removal")
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true,batch="zz_multiplayer")
    public static void multiplayerReturnAndServerRules(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        var server=h.getLevel().getServer();
        var first=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"IncursionTestOne"));
        var second=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"IncursionTestTwo"));
        var difficulty=server.getWorldData().getDifficulty();
        boolean spawning=server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
        try {
            var near=Vec3.atCenterOf(center).add(5,1,0);var far=near.add(200,0,0);
            first.setPos(near);second.setPos(far);
            var d=new Director(server,()->List.of(first,second));d.time=100;
            var p=Definitions.current.profiles().get(ResourceLocation.parse("incursions:profiles/nether"));
            var e=new Encounter(d,p,site);d.active=e;e.open();
            h.assertTrue(e.scaling()==1,"One actual mock server participant has 1x scaling");
            second.setPos(near.add(1,0,0));h.assertTrue(Math.abs(e.scaling()-1.35)<1e-6,"Late nearby participant produces 1.35x next-window multiplier");
            first.setPos(far);second.setPos(far);d.time=101;e.tick();d.time=700;e.tick();h.assertTrue(d.active==e,"Absence below 30 seconds preserves encounter");
            first.setPos(near);e.tick();first.setPos(far);d.time=701;e.tick();d.time=1300;e.tick();h.assertTrue(d.active==e,"Returning participant reset the abandonment timer");
            d.time=1301;e.tick();h.assertTrue(d.active==null,"Full new 30-second absence fails");
            first.setPos(near);
            var gamerule=new Encounter(d,p,site);d.active=gamerule;gamerule.open();
            server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);d.tick();
            h.assertTrue(d.active==null&&!d.allowed(),"doMobSpawning false terminates and suppresses triggers");
            server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(true,server);
            var peaceful=new Encounter(d,p,site);d.active=peaceful;peaceful.open();server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);d.tick();
            h.assertTrue(d.active==null&&!d.allowed(),"Peaceful ends active encounter without continued rolls");
        } finally {
            server.setDifficulty(difficulty,true);
            server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(spawning,server);
            first.discard();second.discard();h.killAllEntities();
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void thinWallSweep(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        h.setBlock(3,1,3,Blocks.OAK_FENCE);h.setBlock(3,2,3,Blocks.OAK_FENCE);
        h.assertTrue(!SpawnSpace.sweep(site,h.absolutePos(new BlockPos(2,1,3)),h.absolutePos(new BlockPos(4,1,3)),.6f,1.8f),"Swept corridor rejects thin fence between clear endpoints");
        h.succeed();
    }

    @SuppressWarnings("unchecked")
    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void infectionGuardScopesOnlyIncursionAttackers(GameTestHelper h) throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("undergarden")) {
            Incursions.LOG.info("Infection integration test NOT EXECUTED: Undergarden absent");h.succeed();return;
        }
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("undergarden:rotwalker"));
        var attacker=(Mob)type.create(h.getLevel());
        var victim=EntityType.COW.create(h.getLevel());
        var attachment=(java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Double>>)Class.forName("quek.undergarden.registry.UGAttachments").getField("UTHERIC_INFECTION").get(null);
        var pos=h.absolutePos(new BlockPos(5,2,5));victim.setPos(Vec3.atCenterOf(pos));attacker.setPos(Vec3.atCenterOf(pos.offset(1,0,0)));
        h.getLevel().addFreshEntity(victim);h.getLevel().addFreshEntity(attacker);
        try {
            victim.setData(attachment,3.0D);attacker.getPersistentData().putBoolean(Protection.TAG,true);attacker.getPersistentData().putBoolean(InfectionGuard.TAG,true);
            float health=victim.getHealth();victim.hurt(victim.damageSources().mobAttack(attacker),1);
            h.assertTrue(victim.getData(attachment)==3.0D,"Incursion hit neither adds infection nor cures existing infection");
            h.assertTrue(victim.getHealth()<health,"The attack itself still deals damage");
            attacker.getPersistentData().remove(Protection.TAG);victim.invulnerableTime=0;
            victim.hurt(victim.damageSources().mobAttack(attacker),1);
            h.assertTrue(victim.getData(attachment)>3.0D,"Ordinary Rotwalker still builds infection");
            victim.setData(attachment,0.0D);victim.invulnerableTime=0;attacker.getPersistentData().putBoolean(Protection.TAG,true);attacker.getPersistentData().putBoolean(InfectionGuard.TAG,false);
            victim.hurt(victim.damageSources().mobAttack(attacker),1);
            h.assertTrue(victim.getData(attachment)>0.0D,"Disabled protection preserves native infection");
        } finally {victim.discard();attacker.discard();}
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void elitePairSoundDeduplicatesAcrossRetries(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        var d=new Director(h.getLevel().getServer());d.time=600;
        var p=Definitions.current.profiles().get(ResourceLocation.parse("incursions:profiles/nether"));
        var e=new Encounter(d,p,site);d.active=e;
        var sounds=new ArrayList<ResourceLocation>();
        java.util.function.Consumer<net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition> listener=event->{
            if(event.getLevel()==h.getLevel() && event.getPosition().distanceToSqr(Vec3.atCenterOf(center))<.01 && event.getSound()!=null)
                sounds.add(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(event.getSound().value()));
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            var pair=new Rules.Arrival(600,1200,Rules.Kind.ENHANCED,true);
            h.assertTrue(e.spawn(pair),"First enhanced enemy arrives");d.time=620;
            h.assertTrue(e.spawn(new Rules.Arrival(600,1200,Rules.Kind.ENHANCED,true)),"Delayed partner arrives");
            h.assertTrue(e.soundedEliteGroups.size()==1,"Partner retries share one sound group");
            h.assertTrue(sounds.equals(List.of(p.zeroPairSound())),"Actual zero-pair sound fires only once");
            d.time=1800;h.assertTrue(e.spawn(new Rules.Arrival(1800,2400,Rules.Kind.ENHANCED,true)),"Later exceptional wave arrives");
            h.assertTrue(e.soundedEliteGroups.size()==2,"Later wave gets its own cue");
            h.assertTrue(!e.spawn(new Rules.Arrival(1800,2400,Rules.Kind.RARE_ENHANCED)),"Missing rare creature cannot spawn");
            h.assertTrue(sounds.size()==2,"Failed enhanced arrival emits no sound");
            h.assertTrue(e.spawn(new Rules.Arrival(1800,2400,Rules.Kind.ENHANCED)),"Regular elite can coexist with exceptional pair");
            h.assertTrue(sounds.equals(List.of(p.zeroPairSound(),p.zeroPairSound(),p.eliteSound())),"Regular and exceptional groups emit distinct actual sound events");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            e.tracked.values().forEach(Entity::discard);e.failed("test_cleanup");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void configuredVictoryTablesResolveAndPayOnce(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        var bounds=new net.minecraft.world.phys.AABB(center).inflate(12);
        var server=h.getLevel().getServer();
        for(var profile:Definitions.current.profiles().values()) {
            h.assertTrue(!profile.rewards().isEmpty(),"Configured profile has rewards: "+profile.id());
            for(var reward:profile.rewards()) {
                var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,reward.table());
                var table=server.reloadableRegistries().getLootTable(key);
                h.assertTrue(table!=net.minecraft.world.level.storage.loot.LootTable.EMPTY,"Actual runtime table resolves: "+reward.table());
                h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds).forEach(Entity::discard);
                var director=new Director(server);director.time=100;
                var encounter=new Encounter(director,custom(profile,profile.pool(),List.of(reward)),site);director.active=encounter;
                encounter.completed();
                var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds);
                h.assertTrue(!drops.isEmpty(),"Actual table produces physical loot: "+reward.table());
                int count=drops.stream().mapToInt(drop->drop.getItem().getCount()).sum();
                encounter.completed();
                h.assertTrue(count==h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds).stream().mapToInt(drop->drop.getItem().getCount()).sum(),"Duplicate victory cannot pay again: "+reward.table());
                Incursions.LOG.info("Verified configured victory loot table {}: {} physical item entities",reward.table(),drops.size());
                drops.forEach(Entity::discard);
            }
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void cockatriceInebriationIsSourceScoped(GameTestHelper h) throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("aether")) {
            Incursions.LOG.info("Cockatrice integration test NOT EXECUTED: Aether absent");h.succeed();return;
        }
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("aether:cockatrice"));
        var attacker=(Mob)type.create(h.getLevel());var victim=EntityType.COW.create(h.getLevel());
        var needleClass=Class.forName("com.aetherteam.aether.entity.projectile.PoisonNeedle");
        var constructor=needleClass.getConstructor(net.minecraft.world.level.Level.class,LivingEntity.class);
        var post=needleClass.getDeclaredMethod("doPostHurtEffects",LivingEntity.class);post.setAccessible(true);
        var effect=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("aether:inebriation")).orElseThrow();
        var needle=(net.minecraft.world.entity.projectile.Projectile)constructor.newInstance(h.getLevel(),attacker);
        try {
            attacker.getPersistentData().putBoolean(Protection.TAG,true);
            post.invoke(needle,victim);h.assertTrue(!victim.hasEffect(effect),"Incursion Cockatrice needle cannot apply inebriation");
            attacker.getPersistentData().remove(Protection.TAG);
            post.invoke(needle,victim);h.assertTrue(victim.hasEffect(effect),"Ordinary Cockatrice retains native inebriation");
            victim.removeEffect(effect);victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,100,0));
            attacker.getPersistentData().putBoolean(Protection.TAG,true);
            post.invoke(needle,victim);h.assertTrue(victim.getEffect(effect).getDuration()==100,"Existing inebriation is neither cleared nor refreshed");
            Protection.join(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(needle,h.getLevel()));
            h.assertTrue(needle.getPersistentData().getBoolean(CockatriceGuard.TAG),"Cockatrice provenance retained on projectile");
            needle.setOwner(null);victim.removeEffect(effect);post.invoke(needle,victim);
            h.assertTrue(!victim.hasEffect(effect),"Orphaned incursion needle retains suppression");
            var ordinary=(net.minecraft.world.entity.projectile.Projectile)constructor.newInstance(h.getLevel(),attacker);
            attacker.getPersistentData().remove(Protection.TAG);post.invoke(ordinary,victim);
            h.assertTrue(victim.hasEffect(effect),"Unmarked needles remain ordinary");ordinary.discard();
        } finally {attacker.discard();victim.discard();needle.discard();}
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void adultsOnlyWithNativePiglinEquipment(GameTestHelper h) {
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        var profile=Definitions.current.profiles().get(ResourceLocation.parse("incursions:profiles/nether"));
        for(String id:List.of("minecraft:piglin","minecraft:zombified_piglin","minecraft:hoglin")) {
            var entry=new Definitions.Creature(ResourceLocation.parse(id),1,false);
            var director=new Director(h.getLevel().getServer());director.time=100;
            var encounter=new Encounter(director,custom(profile,List.of(entry),List.of()),site);director.active=encounter;
            for(int count=0;count<30;count++) {
                h.assertTrue(encounter.spawn(Rules.Kind.ORDINARY),"Age-capable entity can spawn: "+id);
                var mob=encounter.tracked.values().stream().reduce((a,b)->b).orElseThrow();
                for(var spawned:encounter.tracked.values()) {
                    h.assertTrue(!spawned.isBaby(),"Every finalized incursion mob must be adult: "+id);
                    if(id.equals("minecraft:piglin")) h.assertTrue(!spawned.getMainHandItem().isEmpty(),"Native adult Piglin weapon preparation retained");
                    spawned.discard();
                }
                encounter.tracked.clear();
            }
            encounter.failed("test_cleanup");
        }
        int ordinaryBabies=0;
        for(int i=0;i<100;i++) {
            var ordinary=EntityType.PIGLIN.create(h.getLevel());ordinary.setPos(Vec3.atCenterOf(center));
            ordinary.finalizeSpawn(h.getLevel(),h.getLevel().getCurrentDifficultyAt(center),MobSpawnType.EVENT,null);
            if(ordinary.isBaby()) ordinaryBabies++;
            ordinary.discard();
        }
        h.assertTrue(ordinaryBabies>0,"Native ordinary Piglin preparation still produces babies");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void sentryDetonationResolvesWithoutTerrainDamage(GameTestHelper h) throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("aether")) {
            Incursions.LOG.info("Sentry integration test NOT EXECUTED: Aether absent");h.succeed();return;
        }
        var center=anchor(h);var site=Origins.recognize(h.getLevel(),anchorDefinition(),center);
        var profile=Definitions.current.profiles().get(ResourceLocation.parse("incursions:profiles/aether"));
        var director=new Director(h.getLevel().getServer());director.time=100;
        var encounter=new Encounter(director,custom(profile,List.of(new Definitions.Creature(ResourceLocation.parse("aether:sentry"),1,false)),List.of()),site);director.active=encounter;
        h.assertTrue(encounter.spawn(Rules.Kind.ORDINARY),"Actual Sentry can emerge");
        var sentry=encounter.tracked.values().iterator().next();
        var pos=h.absolutePos(new BlockPos(4,1,4));sentry.setPos(Vec3.atBottomCenterOf(pos));
        for(int x=2;x<=7;x++)for(int z=2;z<=7;z++)h.setBlock(x,0,z,Blocks.WHITE_WOOL);
        var victim=EntityType.COW.create(h.getLevel());victim.setPos(Vec3.atBottomCenterOf(pos.offset(1,0,0)));h.getLevel().addFreshEntity(victim);
        sentry.tickCount=21; // Native Aether Sentry cannot detonate during its first twenty ticks.
        var clazz=sentry.getClass();clazz.getMethod("setAwake",boolean.class).invoke(sentry,true);
        var explode=clazz.getDeclaredMethod("explodeAt",LivingEntity.class);explode.setAccessible(true);explode.invoke(sentry,victim);
        h.assertTrue(victim.getHealth()<victim.getMaxHealth(),"Native Sentry attack retains combat damage");
        h.assertTrue(SentryGuard.resolved(sentry),"Only successful native self-detonation is marked resolved");
        for(int x=2;x<=7;x++)for(int z=2;z<=7;z++)h.assertBlockPresent(Blocks.WHITE_WOOL,new BlockPos(x,0,z));
        var maintain=Encounter.class.getDeclaredMethod("maintain",List.class);maintain.setAccessible(true);maintain.invoke(encounter,List.of());
        h.assertTrue(encounter.requiredAlive()==0,"Native detonated Sentry no longer stalls/fails the wave");
        var other=(Mob)net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("aether:sentry")).create(h.getLevel());
        other.getPersistentData().putBoolean(Protection.TAG,true);other.discard();
        h.assertTrue(!SentryGuard.resolved(other),"Arbitrary Sentry discard is not a defeat");
        encounter.tracked.put(other.getUUID(),other);maintain.invoke(encounter,List.of());
        h.assertTrue(director.active==null,"Unresolved removal still fails safely");victim.discard();h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void allInstalledDefaultProfilesRemainEnabled(GameTestHelper h) {
        int expected=1;
        for(var pair:Map.of("aether","aether","undergarden","undergarden","twilight","twilightforest").entrySet()) {
            boolean installed=net.neoforged.fml.ModList.get().isLoaded(pair.getValue());
            boolean enabled=Definitions.current.profiles().containsKey(ResourceLocation.parse("incursions:profiles/"+pair.getKey()));
            h.assertTrue(installed==enabled,"Optional profile availability must match installed supported mod: "+pair.getKey());
            if(installed) expected++;
        }
        h.assertTrue(Definitions.current.profiles().size()==expected,"Exactly the approved profiles enabled");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void newRangedProjectilesRetainProvenance(GameTestHelper h) {
        floor(h);int checked=0;
        for(String id:List.of("twilightforest:slime_beetle","undergarden:sploogie")) {
            var location=ResourceLocation.parse(id);
            if(!net.neoforged.fml.ModList.get().isLoaded(location.getNamespace())) continue;
            var attacker=(Mob)net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(location).create(h.getLevel());
            var victim=EntityType.COW.create(h.getLevel());
            attacker.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(4,1,4))));victim.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(8,1,4))));
            attacker.getPersistentData().putBoolean(Protection.TAG,true);attacker.getPersistentData().putDouble("incursions_damage_multiplier",1.3);
            h.getLevel().addFreshEntity(attacker);h.getLevel().addFreshEntity(victim);
            ((RangedAttackMob)attacker).performRangedAttack(victim,1);
            var shots=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,new net.minecraft.world.phys.AABB(attacker.blockPosition()).inflate(12),shot->shot.getOwner()==attacker);
            h.assertTrue(!shots.isEmpty(),"Native ranged attack creates projectile: "+id);
            for(var shot:shots) {
                h.assertTrue(shot.getPersistentData().getBoolean(Protection.TAG),"Projectile safety provenance propagated: "+id);
                h.assertTrue(Math.abs(shot.getPersistentData().getDouble("incursions_damage_multiplier")-1.3)<1e-6,"Enhanced projectile provenance retained");shot.discard();
            }
            attacker.discard();victim.discard();checked++;
        }
        Incursions.LOG.info("New ranged provenance checks executed: {}",checked);h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100,skyAccess=true)
    public static void curatedRewardsProducePrimaryItemsAndGateAbsentMods(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();int checked=0;
        for(String id:List.of("aether_bronze","aether_silver","undergarden_mine","undergarden_catacombs","undergarden_mound","twilight_room","twilight_library","twilight_potion","twilight_cache","nether_fortress","nether_bastion_other","nether_hoglin_stable")) {
            var location=ResourceLocation.parse("incursions:rewards/"+id);
            var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,location);
            var resource=server.getResourceManager().getResource(ResourceLocation.parse("incursions:loot_table/rewards/"+id+".json")).orElseThrow();
            com.google.gson.JsonObject definition;
            try(var reader=resource.openAsReader()){definition=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();}
            boolean available=!definition.has("neoforge:conditions") || net.neoforged.fml.ModList.get().isLoaded(definition.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("modid").getAsString());
            boolean present=server.reloadableRegistries().get().lookupOrThrow(net.minecraft.core.registries.Registries.LOOT_TABLE).get(key).isPresent();
            h.assertTrue(available==present,"Curated table conditional availability: "+location);
            if(!available) continue;
            Set<ResourceLocation> primary=new HashSet<>();
            for(var element:definition.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")) {
                var entry=element.getAsJsonObject();primary.add(ResourceLocation.parse(entry.get("name").getAsString()));
                if(entry.get("name").getAsString().equals("minecraft:book")&&entry.has("functions")) primary.add(ResourceLocation.parse("minecraft:enchanted_book"));
            }
            var table=server.reloadableRegistries().getLootTable(key);
            var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel()).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,Vec3.atCenterOf(h.absolutePos(new BlockPos(5,1,5)))).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            for(int seed=1;seed<=32;seed++) {
                var context=new net.minecraft.world.level.storage.loot.LootContext.Builder(params).withOptionalRandomSeed(seed).create(Optional.empty());
                var loot=new ArrayList<net.minecraft.world.item.ItemStack>();table.getRandomItems(context,loot::add);
                h.assertTrue(loot.stream().anyMatch(stack->!stack.isEmpty()&&primary.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))),"Real curated payout contains primary item: "+location+" seed "+seed);
            }
            checked++;
        }
        Incursions.LOG.info("Curated reward primary checks: {} tables, 32 deterministic seeds each",checked);h.succeed();
    }
}
