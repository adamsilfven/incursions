package dev.incursions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.Projectile;

/** Source-specific suppression; never removes an effect already present on the victim. */
public final class CockatriceGuard {
    public static final String TAG="incursions_cockatrice_needle";
    private static final ResourceLocation COCKATRICE=ResourceLocation.parse("aether:cockatrice");
    private CockatriceGuard() {}
    public static boolean suppress(Projectile needle) {
        var owner=needle.getOwner();
        return needle.getPersistentData().getBoolean(TAG) || owner!=null && Protection.tagged(owner)
            && COCKATRICE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(owner.getType()));
    }
}
