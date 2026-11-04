package dev.incursions.mixin;

import net.minecraft.world.entity.monster.piglin.Piglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keeps native adult weapon preparation instead of converting an unarmed baby after preparation. */
@Mixin(Piglin.class)
public abstract class AdultPiglinMixin {
    @ModifyConstant(method="finalizeSpawn",constant=@Constant(floatValue=0.2F,ordinal=0),require=1)
    private float incursions$adultChance(float original) {
        return ((Piglin)(Object)this).getPersistentData().getBoolean("incursions_spawned")?0F:original;
    }
}
