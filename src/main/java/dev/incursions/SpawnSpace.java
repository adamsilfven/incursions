package dev.incursions;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Flood-fill only through collision-clear cells connected to the active interior's exterior.
 * Candidates never appear across a wall merely because they are within a radius.
 */
final class SpawnSpace {
    private SpawnSpace() {}
    static boolean clear(Origins.Site s, BlockPos p, float width, float height, boolean flying) {
        double half=width/2d;
        AABB box=new AABB(p.getX()+.5-half,p.getY(),p.getZ()+.5-half,p.getX()+.5+half,p.getY()+height,p.getZ()+.5+half);
        if(!s.level().getWorldBorder().isWithinBounds(box)) return false;
        for(BlockPos b:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
            if(!s.level().hasChunkAt(b)) return false;
            var state=s.level().getBlockState(b);
            if(!state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)) return false;
        }
        if(!s.level().noCollision(box)) return false;
        if(!flying) {
            AABB feet=new AABB(box.minX,p.getY()-.05,box.minZ,box.maxX,p.getY(),box.maxZ);
            for(BlockPos below:BlockPos.betweenClosed(BlockPos.containing(feet.minX,feet.minY,feet.minZ),BlockPos.containing(feet.maxX,feet.minY,feet.maxZ))) {
                if(!s.level().hasChunkAt(below)) return false;
                var floor=s.level().getBlockState(below);
                if(floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)) return false;
            }
            boolean support=false;
            for(var shape:s.level().getBlockCollisions(null,feet)) if(!shape.isEmpty()) {support=true;break;}
            if(!support) return false;
        }
        return true;
    }
    static boolean sweep(Origins.Site s, BlockPos a, BlockPos b, float width, float height) {
        double half=width/2d;
        AABB box=new AABB(Math.min(a.getX(),b.getX())+.5-half,Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ())+.5-half,
            Math.max(a.getX(),b.getX())+.5+half,Math.max(a.getY(),b.getY())+height,Math.max(a.getZ(),b.getZ())+.5+half);
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
            if(!s.level().hasChunkAt(p)) return false;
        return s.level().noCollision(box);
    }
    static List<Vec3> reachable(Origins.Site site, float width, float height, boolean flying, List<ServerPlayer> players) {
        Set<BlockPos> interior=site.required().keySet();
        Set<Long> columns=new HashSet<>();for(var p:interior)columns.add(new BlockPos(p.getX(),0,p.getZ()).asLong());
        var seeds=new LinkedHashSet<BlockPos>();
        for(BlockPos block:interior) for(Direction d:Direction.Plane.HORIZONTAL) {
            BlockPos p=block.relative(d);
            if(interior.contains(p)) continue;
            if(site.definition().provider().equals("anchor") || site.definition().provider().equals("horizontal")) p=p.above();
            for(int dy=-1;dy<=1;dy++) {
                BlockPos seed=p.offset(0,dy,0);
                if(clear(site,seed,width,height,flying)) seeds.add(seed);
            }
        }
        if(site.definition().provider().equals("anchor")) for(BlockPos block:interior) {
            BlockPos top=block.above();
            if(clear(site,top,width,height,flying)) seeds.add(top);
        }
        var participants=players.stream().filter(player->site.near(player,96)).toList();
        Comparator<BlockPos> towardPlayer=Comparator.comparingDouble(p->participants.stream()
            .mapToDouble(player->player.distanceToSqr(Vec3.atBottomCenterOf(p))).min().orElse((double)p.distManhattan(site.center())));
        var result=new ArrayList<Vec3>();var visited=new HashSet<BlockPos>();
        int radius=site.definition().radius();
        // Each disconnected exterior component proves its own accessibility. An open front cannot authorize a sealed rear room.
        for(BlockPos seed:seeds) {
            if(visited.contains(seed) || visited.size()>=4096) continue;
            var queue=new PriorityQueue<BlockPos>(towardPlayer);queue.add(seed);visited.add(seed);
            var candidates=new ArrayList<Vec3>();boolean accessible=false;
            while(!queue.isEmpty() && visited.size()<4096) {
                BlockPos p=queue.remove();
                if(Math.abs(p.getX()-site.center().getX())>96 || Math.abs(p.getZ()-site.center().getZ())>96 || Math.abs(p.getY()-site.center().getY())>32) continue;
                if(site.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,p.getX(),p.getZ())<=p.getY()
                    || participants.stream().anyMatch(player->player.distanceToSqr(Vec3.atBottomCenterOf(p))<9 && sweep(site,p,player.blockPosition(),width,height))) accessible=true;
                boolean outside=!columns.contains(new BlockPos(p.getX(),0,p.getZ()).asLong());
                if(outside && Math.abs(p.getX()-site.center().getX())<=radius && Math.abs(p.getZ()-site.center().getZ())<=radius
                    && Math.abs(p.getY()-site.center().getY())<=8) candidates.add(Vec3.atBottomCenterOf(p));
                if(accessible && candidates.size()>=64) break;
                for(Direction d:Direction.Plane.HORIZONTAL) for(int dy=-1;dy<=1;dy++) {
                    BlockPos n=p.relative(d).offset(0,dy,0);
                    if(visited.contains(n) || n.distManhattan(site.center())>224) continue;
                    BlockPos high=dy>0?p.above():dy<0?n.above():n;
                    if(!clear(site,n,width,height,flying) || !clear(site,high,width,height,true) || !sweep(site,p,high,width,height) || !sweep(site,high,n,width,height)) continue;
                    visited.add(n);queue.add(n);
                }
                if(flying) for(Direction d:List.of(Direction.UP,Direction.DOWN)) {
                    BlockPos n=p.relative(d);
                    if(!visited.contains(n) && n.distManhattan(site.center())<=224 && clear(site,n,width,height,true)) {visited.add(n);queue.add(n);}
                }
            }
            if(accessible) result.addAll(candidates);
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }
}
