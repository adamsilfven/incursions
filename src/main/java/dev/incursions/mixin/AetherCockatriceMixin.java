package dev.incursions.mixin;

import dev.incursions.CockatriceGuard;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Retains projectile damage and superclass behavior; suppresses only its effect application. */
@Pseudo
@Mixin(targets="com.aetherteam.aether.entity.projectile.PoisonNeedle",remap=false)
public abstract class AetherCockatriceMixin {
    @Redirect(method="doPostHurtEffects",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),require=1,remap=false)
    private boolean incursions$skipInebriation(LivingEntity victim,MobEffectInstance effect) {
        return !CockatriceGuard.suppress((Projectile)(Object)this) && victim.addEffect(effect);
    }
}
