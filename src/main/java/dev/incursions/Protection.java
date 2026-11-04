package dev.incursions;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.*;

/** Persistent provenance protects survivors and their projectiles without touching unrelated mobs. */
final class Protection {
    static final String TAG="incursions_spawned";
    private Protection() {}
    static Entity owner(Entity entity) {
        for(int i=0;i<8 && entity instanceof Projectile p && p.getOwner()!=null;i++) entity=p.getOwner();
        return entity;
    }
    static boolean tagged(Entity entity) {
        return entity!=null && (entity.getPersistentData().getBoolean(TAG) || owner(entity).getPersistentData().getBoolean(TAG));
    }
    static void register() {
        NeoForge.EVENT_BUS.addListener(Protection::grief);
        NeoForge.EVENT_BUS.addListener(Protection::explosion);
        NeoForge.EVENT_BUS.addListener(Protection::place);
        NeoForge.EVENT_BUS.addListener(Protection::join);
        NeoForge.EVENT_BUS.addListener(Protection::damage);
    }
    static void grief(EntityMobGriefingEvent e) { if(tagged(e.getEntity())) e.setCanGrief(false); }
    static void explosion(ExplosionEvent.Detonate e) {
        if(tagged(e.getExplosion().getDirectSourceEntity()) || tagged(e.getExplosion().getIndirectSourceEntity())) e.getAffectedBlocks().clear();
    }
    static void place(BlockEvent.EntityPlaceEvent e) { if(tagged(e.getEntity())) e.setCanceled(true); }
    static void damage(LivingIncomingDamageEvent e) {
        Entity source=owner(e.getSource().getEntity());
        if(source==null) source=owner(e.getSource().getDirectEntity());
        if(source!=null && tagged(source)) {
            double multiplier=source.getPersistentData().getDouble("incursions_damage_multiplier");
            if(multiplier>0 && Double.isFinite(multiplier)) e.setAmount((float)(e.getAmount()*multiplier));
        }
    }
    static void join(EntityJoinLevelEvent e) {
        Entity entity=e.getEntity();
        if(e.getLevel().isClientSide()) return;
        if(entity instanceof Projectile p && tagged(p.getOwner())) {
            entity.getPersistentData().putBoolean(TAG,true);
            if(CockatriceGuard.suppress(p)) entity.getPersistentData().putBoolean(CockatriceGuard.TAG,true);
            entity.getPersistentData().putDouble("incursions_damage_multiplier",p.getOwner().getPersistentData().getDouble("incursions_damage_multiplier"));
        }
        if(e.loadedFromDisk() && tagged(entity)) {
            entity.getPersistentData().remove("incursions_session");
            if(entity instanceof AbstractPiglin p) p.setImmuneToZombification(entity.getPersistentData().getBoolean("incursions_previous_immunity"));
            if(entity instanceof Hoglin h) h.setImmuneToZombification(entity.getPersistentData().getBoolean("incursions_previous_immunity"));
        }
    }
}
