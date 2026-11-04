package dev.incursions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Only the native, successful Sentry detonation can resolve its deliberate self-removal. */
public final class SentryGuard {
    private static final String TAG="incursions_sentry_detonated";
    private static final ResourceLocation TYPE=ResourceLocation.parse("aether:sentry");
    private SentryGuard() {}
    public static void afterExplosion(Entity sentry) {
        if(Protection.tagged(sentry) && sentry.isRemoved() && sentry.getRemovalReason()==Entity.RemovalReason.DISCARDED
            && TYPE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(sentry.getType()))) sentry.getPersistentData().putBoolean(TAG,true);
    }
    static boolean resolved(Entity entity) {
        return entity.getPersistentData().getBoolean(TAG) && entity.isRemoved()
            && entity.getRemovalReason()==Entity.RemovalReason.DISCARDED && TYPE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }
}
