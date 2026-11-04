package dev.incursions;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Loaded-section discovery with a strict per-tick budget, and bounded connected components. */
final class Origins {
    record Site(ServerLevel level, Definitions.Origin definition, BlockPos center, Map<BlockPos, Block> required) {
        String key() { return level.dimension().location()+"/"+definition.id()+"/"+center.asLong(); }
        boolean intact() {
            for (var e : required.entrySet()) if (!level.hasChunkAt(e.getKey()) || !level.getBlockState(e.getKey()).is(e.getValue())) return false;
            return validFrame(this);
        }
        boolean near(ServerPlayer p, double distance) {
            return p.serverLevel()==level && p.isAlive() && !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(center)) <= distance*distance;
        }
    }
    record SectionJob(ServerLevel level, int x, int z, int section) {}
    private final ArrayDeque<SectionJob> jobs = new ArrayDeque<>();
    private final Map<String, Site> sites = new LinkedHashMap<>();
    private long generation = -1;
    private int playerCursor;
    private final Map<String,Boolean> accessible=new HashMap<>();
    void tick(MinecraftServer server, long now) {
        accessible.clear();
        if (generation != Definitions.current.generation()) { sites.clear(); jobs.clear(); generation=Definitions.current.generation(); }
        if (now % 100 == 0) {
            sites.values().removeIf(s -> !s.intact() || server.getPlayerList().getPlayers().stream().noneMatch(p->s.near(p,112)));
            if (jobs.isEmpty()) enqueue(server);
        }
        for (int budget=0; budget<Settings.SECTIONS.get() && !jobs.isEmpty(); budget++) scan(jobs.removeFirst());
    }
    private void enqueue(MinecraftServer server) {
        Set<String> visited = new HashSet<>();
        Set<Block> possible=new HashSet<>();Definitions.current.origins().values().forEach(o->possible.add(BuiltInRegistries.BLOCK.get(o.block())));
        int examined=0;
        var players=new ArrayList<>(server.getPlayerList().getPlayers());
        if(!players.isEmpty()) java.util.Collections.rotate(players,-(playerCursor++%players.size()));
        for (ServerPlayer player : players) {
            if (!player.isAlive() || player.isSpectator()) continue;
            ServerLevel level = player.serverLevel();
            // Seven chunks cover the 96-block eligibility radius at chunk boundaries.
            for (int dx=-7; dx<=7; dx++) for (int dz=-7; dz<=7; dz++) {
                int x=player.chunkPosition().x+dx, z=player.chunkPosition().z+dz;
                String key=level.dimension().location()+"/"+x+"/"+z;
                if (!visited.add(key)) continue;
                if(++examined>4096) return;
                LevelChunk chunk=level.getChunkSource().getChunkNow(x,z);
                if (chunk==null) continue;
                for(int s=0;s<chunk.getSectionsCount();s++) {
                    var section=chunk.getSection(s);
                    if(section.hasOnlyAir() || !section.getStates().maybeHas(state->possible.contains(state.getBlock()))) continue;
                    if (jobs.size()>=32768) return; // bounded even on very large servers; later refreshes resume current player vicinity
                    jobs.add(new SectionJob(level,x,z,s));
                }
            }
        }
    }
    private void scan(SectionJob job) {
        LevelChunk chunk=job.level.getChunkSource().getChunkNow(job.x,job.z);
        if(chunk==null) return;
        LevelChunkSection section=chunk.getSection(job.section);
        if(section.hasOnlyAir()) return;
        List<Definitions.Origin> definitions = Definitions.current.origins().values().stream()
            .filter(o -> o.dimensions().isEmpty() || o.dimensions().contains(job.level.dimension().location())).toList();
        Set<Block> blocks=new HashSet<>();
        definitions.forEach(o -> blocks.add(BuiltInRegistries.BLOCK.get(o.block())));
        if(!section.getStates().maybeHas(s->blocks.contains(s.getBlock()))) return;
        int baseY=chunk.getSectionYFromSectionIndex(job.section)*16;
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
            BlockState state=section.getBlockState(x,y,z);
            if(!blocks.contains(state.getBlock())) continue;
            BlockPos pos=new BlockPos(job.x*16+x,baseY+y,job.z*16+z);
            for(var o:definitions) if(state.is(BuiltInRegistries.BLOCK.get(o.block()))) {
                // Don't repeatedly flood each block of an already indexed portal.
                if(sites.values().stream().anyMatch(s->s.level==job.level && s.definition.id().equals(o.id()) && s.required.containsKey(pos))) continue;
                Site site=recognize(job.level,o,pos);
                if(site!=null && sites.size()<8192) sites.put(site.key(),site);
            }
        }
    }
    static Site recognize(ServerLevel level, Definitions.Origin o, BlockPos pos) {
        Map<BlockPos,Block> required=new HashMap<>();
        Block expected=BuiltInRegistries.BLOCK.get(o.block());
        if(!level.hasChunkAt(pos) || !level.getBlockState(pos).is(expected)) return null;
        if(o.provider().equals("anchor")) {
            Block stairs=null;
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                BlockPos p=pos.offset(dx,0,dz);
                if(!level.hasChunkAt(p)) return null;
                Block block=level.getBlockState(p).getBlock();
                if(dx!=0 || dz!=0) {
                    if(!(block instanceof StairBlock)) return null;
                    if(stairs!=null && block!=stairs) return null;
                    stairs=block;
                }
                required.put(p.immutable(),block);
            }
            return new Site(level,o,pos.immutable(),Map.copyOf(required));
        }
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(pos.immutable());
        while(!queue.isEmpty()) {
            BlockPos p=queue.removeFirst();
            if(required.containsKey(p)) continue;
            if(!level.hasChunkAt(p)) return null;
            if(!level.getBlockState(p).is(expected)) continue;
            required.put(p,expected);
            if(required.size()>1024) return null;
            for(Direction d:Direction.values()) {
                if(o.provider().equals("horizontal") && d.getAxis()==Direction.Axis.Y) continue;
                queue.add(p.relative(d));
            }
        }
        if(required.size() < (o.provider().equals("horizontal")?4:6)) return null;
        BlockPos center=required.keySet().stream().min(Comparator.comparingLong(BlockPos::asLong)).orElse(pos);
        return new Site(level,o,center,Map.copyOf(required));
    }
    static boolean validFrame(Site site) {
        if(site.definition.provider().equals("anchor")) return true;
        if(site.definition.provider().equals("horizontal")) {
            for(var p:site.required.keySet()) {
                if(!site.level.hasChunkAt(p.below()) || !site.level.getBlockState(p.below()).isFaceSturdy(site.level,p.below(),Direction.UP)) return false;
            }
            return true;
        }
        if(site.definition.frame().isEmpty()) return true;
        var points=site.required.keySet();
        int minX=points.stream().mapToInt(BlockPos::getX).min().orElse(0), maxX=points.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minZ=points.stream().mapToInt(BlockPos::getZ).min().orElse(0), maxZ=points.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        int minY=points.stream().mapToInt(BlockPos::getY).min().orElse(0), maxY=points.stream().mapToInt(BlockPos::getY).max().orElse(0);
        boolean axisX=minZ==maxZ;
        if(!axisX && minX!=maxX) return false;
        int min=axisX?minX:minZ, max=axisX?maxX:maxZ;
        if(max-min+1<2 || max-min+1>21 || maxY-minY+1<3 || maxY-minY+1>21 || points.size()!=(max-min+1)*(maxY-minY+1)) return false;
        String frame=site.definition.frame();
        for(int across=min-1;across<=max+1;across++) for(int y=minY-1;y<=maxY+1;y++) {
            boolean side=across==min-1 || across==max+1, top=y==minY-1 || y==maxY+1;
            if(!side && !top || side && top) continue; // vanilla-style corners are optional
            BlockPos p=axisX?new BlockPos(across,y,minZ):new BlockPos(minX,y,across);
            if(!site.level.hasChunkAt(p)) return false;
            var state=site.level.getBlockState(p);
            if(frame.startsWith("#")) {
                if(!state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,ResourceLocation.parse(frame.substring(1))))) return false;
            } else if(!state.is(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(frame)))) return false;
        }
        return true;
    }
    List<Site> eligible(Definitions.Profile profile, List<ServerPlayer> players) {
        Set<ResourceLocation> bindings=new HashSet<>();profile.origins().forEach(o->bindings.add(o.id()));
        return sites.values().stream().filter(s->bindings.contains(s.definition.id()) && s.intact() && players.stream().anyMatch(p->s.near(p,96)))
            .filter(s->accessible.computeIfAbsent(s.key(),key->!SpawnSpace.reachable(s,.6f,1.8f,false,players).isEmpty())).toList();
    }
    int count() { return sites.size(); }
}
