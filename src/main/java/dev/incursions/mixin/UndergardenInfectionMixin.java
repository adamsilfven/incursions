package dev.incursions.mixin;

import dev.incursions.InfectionGuard;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels only Undergarden's infection listener, never the underlying attack event. */
@Pseudo
@Mixin(targets="quek.undergarden.event.UthericInfectionEvents",remap=false)
public abstract class UndergardenInfectionMixin {
    @Inject(method="onEntityHit",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private static void incursions$preventInfection(LivingIncomingDamageEvent event, CallbackInfo ci) {
        if(InfectionGuard.suppress(event)) ci.cancel();
    }
}
