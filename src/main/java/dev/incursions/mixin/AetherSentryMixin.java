package dev.incursions.mixin;

import dev.incursions.SentryGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.aetherteam.aether.entity.monster.dungeon.Sentry",remap=false)
public abstract class AetherSentryMixin {
    @Inject(method="explodeAt",at=@At("TAIL"),require=1,remap=false)
    private void incursions$detonationResolved(LivingEntity victim,CallbackInfo ci) {
        SentryGuard.afterExplosion((Entity)(Object)this);
    }
}
